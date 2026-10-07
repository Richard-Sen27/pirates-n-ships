package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Server side of sliding along a grappling rope (GR2, docs/design.md §8.3): grabbing the rope and the rider's tick.
 *
 * <p><b>Grabbing.</b> A player uses the rope while looking at it (the client sends {@link BoardRopePayload} when its
 * own pick hits a rope; {@link #tryBoard} checks again with the server's view of the player, a little more lenient
 * for the latency). The rope must be latched; the player must be within {@code board_reach} of the rope point the look
 * ray passes within {@code board_pick_radius} of. A thrower grabbing their own untied rope pins its near end where
 * their hand is ({@link GrapplingHookEntity#pinNearEnd}), so the rope stays strung when they slide away. The player
 * then rides a {@link RopeRiderEntity} at the picked point.
 *
 * <p><b>Riding.</b> {@link #tickRider} moves the rider by {@link RopeSlide#step} along the rope's live ends and hangs
 * the player {@code hang_offset} below it. Arriving, the player lands on the standing spot of that end
 * ({@link #landing}); fall distance is reset every tick, so only a drop from the rope hurts.
 */
public final class RopeSlideService {

    public static final String DISABLED_KEY = "message." + Constants.MOD_ID + ".grapple.slide_disabled";

    /** Extra reach and pick radius the server allows over the client's pick [blocks] (look direction latency). */
    static final double SERVER_TOLERANCE = 0.75;
    /** How high above the standing spot a landing player is put [blocks], so they drop onto the deck. */
    static final double LANDING_NUDGE = 0.25;
    /** Ticks a fresh rider may wait for its passenger before it removes itself. */
    private static final int MAX_EMPTY_TICKS = 2;

    private RopeSlideService() {
    }

    /** Outcome of a grab. */
    public enum Board { OK, DISABLED, BUSY, NOT_LATCHED, OUT_OF_REACH }

    /** From {@link BoardRopePayload}: the player used the rope of the hook with network id {@code hookId}. */
    public static void onBoardRequest(Player player, int hookId) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Entity e = level.getEntity(hookId);
        if (!(e instanceof GrapplingHookEntity hook)) {
            return;
        }
        Board b = tryBoard(level, player, hook, SERVER_TOLERANCE);
        if (b == Board.DISABLED) {
            player.displayClientMessage(Component.translatable(DISABLED_KEY), true);
        }
    }

    /**
     * The player grabs {@code hook}'s rope where their look ray passes it ({@link RopeSlide#pick}, reach and radius
     * widened by {@code tolerance}) and starts sliding.
     */
    public static Board tryBoard(ServerLevel level, Player player, GrapplingHookEntity hook, double tolerance) {
        if (!GrappleConfig.ENABLED.get() || !GrappleConfig.SLIDE_ENABLED.get()) {
            return Board.DISABLED;
        }
        if (player.isPassenger() || player.isVehicle() || player.isSpectator() || !player.isAlive() || player.isSleeping()
                || player.level() != level) {
            return Board.BUSY;
        }
        if (hook.isRemoved() || hook.level() != level || hook.state() != GrapplingHookEntity.State.LATCHED) {
            return Board.NOT_LATCHED;
        }
        Vec3 a = hook.ropeNearEnd(level);
        Vec3 b = hook.ropeFarEnd(level);
        if (a == null || b == null || a.distanceTo(b) < RopeSlide.MIN_LENGTH) {
            return Board.NOT_LATCHED;
        }
        RopeSlide.Pick pick = RopeSlide.pick(player.getEyePosition(), player.getLookAngle(),
                GrappleConfig.BOARD_REACH.get() + tolerance, a, b, GrappleConfig.BOARD_PICK_RADIUS.get() + tolerance);
        if (pick == null) {
            return Board.OUT_OF_REACH;
        }
        if (hook.getOwner() == player && !hook.nearEndFixed()) {
            pin(level, hook, player, a);
        }
        RopeRiderEntity rider = RopeRiderEntity.create(level, hook, pick.t(), hangPos(RopeSlide.at(a, b, pick.t())));
        level.addFreshEntity(rider);
        if (!player.startRiding(rider)) {
            rider.discard();
            return Board.BUSY;
        }
        player.resetFallDistance();
        level.playSound(null, rider.getX(), rider.getY() + GrappleConfig.HANG_OFFSET.get(), rider.getZ(), SoundEvents.LEASH_KNOT_PLACE,
                SoundSource.PLAYERS, 0.7f, 1.3f);
        return Board.OK;
    }

    /** Pins the thrower's rope at their hand {@code hand} (world), on the ship they stand on or in the world. */
    private static void pin(ServerLevel level, GrapplingHookEntity hook, Player thrower, Vec3 hand) {
        ShipBody ship = GrappleService.shipOf(level, thrower);
        if (ship != null && !ship.id().equals(hook.shipId())) {
            hook.pinNearEnd(ship.toPlot(hand), ship.id());
        } else {
            hook.pinNearEnd(hand, null);
        }
    }

    /** Where the hanging player's feet are for a rope point. */
    static Vec3 hangPos(Vec3 ropePoint) {
        return ropePoint.subtract(0, GrappleConfig.HANG_OFFSET.get(), 0);
    }

    // ------------------------------------------------------------------ riding

    /** One server tick of {@code rider}. */
    static void tickRider(ServerLevel level, RopeRiderEntity rider) {
        Entity passenger = rider.getFirstPassenger();
        if (passenger == null) {
            if (rider.tickCount > MAX_EMPTY_TICKS) {
                rider.discard();
            }
            return;
        }
        if (!(passenger instanceof Player player)) {
            rider.letGo(null); // only players hang on ropes
            return;
        }
        GrapplingHookEntity hook = rider.hook();
        if (!GrappleConfig.ENABLED.get() || !GrappleConfig.SLIDE_ENABLED.get() || hook == null
                || hook.state() != GrapplingHookEntity.State.LATCHED) {
            rider.letGo(null);
            return;
        }
        Vec3 a = hook.ropeNearEnd(level);
        Vec3 b = hook.ropeFarEnd(level);
        if (a == null || b == null) {
            rider.letGo(null);
            return;
        }
        RopeSlide.Step step = RopeSlide.step(rider.t(), rider.speed(), rider.dir(), a.distanceTo(b), a.y, b.y,
                GrappleConfig.slideParams());
        if (step.arrived()) {
            Vec3 land = landing(level, hook, step.dir() > 0, step.dir() > 0 ? b : a);
            rider.letGo(land.add(0, LANDING_NUDGE, 0));
            level.playSound(null, land.x, land.y, land.z, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8f, 1.0f);
            return;
        }
        rider.slideTo(step.t(), step.speed(), step.dir(), hangPos(RopeSlide.at(a, b, step.t())));
        player.resetFallDistance();
    }

    // ------------------------------------------------------------------ landing

    /**
     * Where a rider arriving at an end of {@code hook}'s rope stands (world, feet): on the hook's end the top of the
     * ship block the hook bit into (or the nearest standable block just above or below it), on the near end the same
     * around the ring or the pin, or the thrower's own feet when the thrower holds the rope. {@code ropeEnd} (world)
     * when nothing standable is found.
     */
    static Vec3 landing(ServerLevel level, GrapplingHookEntity hook, boolean hookEnd, Vec3 ropeEnd) {
        if (hookEnd) {
            ShipBody ship = hook.shipId() == null ? null : SableShips.byId(level, hook.shipId());
            BlockPos block = hook.latchedBlock();
            return block == null ? ropeEnd : standingSpot(level, ship, block, ropeEnd);
        }
        if (hook.tiedRing() != null) {
            UUID id = hook.tiedShip();
            return standingSpot(level, id == null ? null : SableShips.byId(level, id), hook.tiedRing(), ropeEnd);
        }
        Vec3 pin = hook.pinPos();
        if (pin != null) {
            UUID id = hook.pinShip();
            BlockPos feet = BlockPos.containing(pin.subtract(0, 1.0, 0)); // the hand was above the thrower's feet
            return standingSpot(level, id == null ? null : SableShips.byId(level, id), feet.below(), ropeEnd);
        }
        Entity owner = hook.getOwner();
        return owner != null ? owner.position() : ropeEnd;
    }

    /** Order in which heights around the start block are tried for a standing spot. */
    private static final int[] SEARCH = {0, 1, -1, 2, -2, 3, 4};

    /**
     * The world position (feet) on top of the first block near {@code start} (plot position on {@code ship}, world when
     * that is null) that has room for a player above it, or {@code fallback}.
     */
    static Vec3 standingSpot(ServerLevel level, @Nullable ShipBody ship, BlockPos start, Vec3 fallback) {
        for (int dy : SEARCH) {
            BlockPos p = start.above(dy);
            if (standable(level, p)) {
                Vec3 local = Vec3.atBottomCenterOf(p.above());
                return ship != null ? ship.toWorld(local) : local;
            }
        }
        return fallback;
    }

    private static boolean standable(ServerLevel level, BlockPos p) {
        return !level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && level.getBlockState(p.above(2)).getCollisionShape(level, p.above(2)).isEmpty();
    }
}
