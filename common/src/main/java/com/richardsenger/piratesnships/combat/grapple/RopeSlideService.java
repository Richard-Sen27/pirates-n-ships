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

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Server side of sliding along a grappling rope (GR2, docs/design.md §8.3): grabbing the rope and the rider's tick.
 *
 * <p><b>Grabbing.</b> A player uses the rope while looking at it, with an empty main hand or a hook in it (GR4) (the client sends {@link BoardRopePayload} when its
 * own pick hits a rope; {@link #tryBoard} checks again with the server's view of the player, a little more lenient
 * for the latency). The rope must be latched; the player must be within {@code board_reach} of the rope point the look
 * ray passes within {@code board_pick_radius} of. GR5 ("no floating near end"): a rope is a line to slide along only
 * when both ends are fixed, the hook and a cleat or mooring ring it is tied to; then the player rides a
 * {@link RopeRiderEntity} at the picked point. A rope whose near end is in the thrower's hand is never pinned in mid-air:
 * its thrower using it is pulled hand over hand toward the hook instead ({@link #tickPull}, the rope still running from
 * the hook to their hand), and nobody else can mount it. GR6: that pull is only a climb, allowed when the hook is at
 * least {@code climb_min_rise} above the thrower's feet ({@link #isClimb}); otherwise the use does nothing but show a
 * hint to tie the rope off.
 *
 * <p><b>Riding.</b> {@link #tickRider} moves the rider by {@link RopeSlide#step} along the rope's live ends and hangs
 * the player {@code hang_offset} below it. Arriving, the player lands on the standing spot of that end
 * ({@link #landing}); fall distance is reset every tick, so only a drop from the rope hurts.
 */
public final class RopeSlideService {

    public static final String DISABLED_KEY = "message." + Constants.MOD_ID + ".grapple.slide_disabled";
    /** GR6: the hint for a use of a hand-held rope whose hook is not high enough above the thrower to climb to it. */
    public static final String TIE_OFF_HINT_KEY = "message." + Constants.MOD_ID + ".grapple.tie_off_hint";
    /** GR6: the tie-off hint is shown at most once per this many ticks to the same player. */
    static final long HINT_INTERVAL_TICKS = 20;

    /** Extra reach and pick radius the server allows over the client's pick [blocks] (look direction latency). */
    static final double SERVER_TOLERANCE = 0.75;
    /** How high above the standing spot a landing player is put [blocks], so they drop onto the deck. */
    static final double LANDING_NUDGE = 0.25;
    /** Ticks a fresh rider may wait for its passenger before it removes itself. */
    private static final int MAX_EMPTY_TICKS = 2;

    private RopeSlideService() {
    }

    /** Outcome of a grab. */
    public enum Board {
        OK, DISABLED, BUSY,
        /** GR5: the near end is in its thrower's hand, not tied off: only the thrower can use it, to pull themselves in. */
        NOT_FIXED,
        /**
         * GR6: the thrower's own hand-held rope, but the hook is less than {@code climb_min_rise} above their feet: a
         * hand-held rope is only climbed, never slid along. Nothing happens but the tie-off hint.
         */
        NOT_A_CLIMB,
        /** GR4: the main hand holds a musket or another item with its own use ({@link RopeSlide.Grab#HAND_BUSY}). */
        HAND_BUSY,
        /** GR4: the hook left less than {@code grab_cooldown_ticks} ago ({@link RopeSlide.Grab#TOO_SOON}). */
        TOO_SOON,
        NOT_LATCHED, OUT_OF_REACH
    }

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
     * widened by {@code tolerance}) and starts sliding. GR4: only with an empty main hand or a grappling hook in it,
     * and not within {@code grab_cooldown_ticks} of the hook leaving ({@link RopeSlide#grab}); a musket in hand
     * keeps its own use, so the thrower is never hung on (and never pins) a rope they just fired.
     */
    public static Board tryBoard(ServerLevel level, Player player, GrapplingHookEntity hook, double tolerance) {
        if (!GrappleConfig.ENABLED.get() || !GrappleConfig.SLIDE_ENABLED.get()) {
            return Board.DISABLED;
        }
        if (player.isPassenger() || player.isVehicle() || player.isSpectator() || !player.isAlive() || player.isSleeping()
                || player.level() != level) {
            return Board.BUSY;
        }
        RopeSlide.Grab grab = RopeSlide.grab(GrapplingHookItem.heldOf(player.getMainHandItem()), hook.tickCount,
                GrappleConfig.GRAB_COOLDOWN_TICKS.get());
        if (grab == RopeSlide.Grab.HAND_BUSY) {
            return Board.HAND_BUSY;
        }
        if (grab == RopeSlide.Grab.TOO_SOON) {
            return Board.TOO_SOON;
        }
        if (hook.isRemoved() || hook.level() != level || hook.state() != GrapplingHookEntity.State.LATCHED) {
            return Board.NOT_LATCHED;
        }
        boolean pull = !hook.nearEndFixed();
        if (pull && hook.getOwner() != player) {
            return Board.NOT_FIXED; // GR5: a hand-held rope is no line to slide along
        }
        if (pull && player.isShiftKeyDown()) {
            return Board.BUSY; // sneaking hauls the rope (GR5) and sneak + use lets go of it: no pull
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
        if (pull && !isClimb(b.y, player.getY(), GrappleConfig.CLIMB_MIN_RISE.get())) {
            hintTieOff(level, player);
            return Board.NOT_A_CLIMB; // GR6: level, below or across a gap: tie the rope off to use it as a line
        }
        RopeRiderEntity rider = pull ? RopeRiderEntity.createPull(level, hook, player.position())
                : RopeRiderEntity.create(level, hook, pick.t(), hangPos(RopeSlide.at(a, b, pick.t())));
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

    /**
     * GR6, the climb rule: the thrower may pull themselves along their own hand-held rope only when the hook
     * ({@code hookY}, world) is at least {@code minRise} blocks above their feet ({@code feetY}). A rise of 0 lets every
     * pull through (GR5).
     */
    static boolean isClimb(double hookY, double feetY, double minRise) {
        return minRise <= 0.0 || hookY - feetY >= minRise;
    }

    /** GR6: whether a hint last shown at game time {@code last} ({@code Long.MIN_VALUE}: never) may be shown again at {@code now}. */
    static boolean hintDue(long now, long last) {
        return last == Long.MIN_VALUE || now - last >= HINT_INTERVAL_TICKS || now < last;
    }

    /** Game time at which each player last saw the tie-off hint (weak: players come and go). */
    private static final Map<Player, Long> LAST_HINT = new WeakHashMap<>();

    /** Shows {@code player} the tie-off hint on the status bar, at most once per {@link #HINT_INTERVAL_TICKS}. */
    private static void hintTieOff(ServerLevel level, Player player) {
        long now = level.getGameTime();
        synchronized (LAST_HINT) {
            if (!hintDue(now, LAST_HINT.getOrDefault(player, Long.MIN_VALUE))) {
                return;
            }
            LAST_HINT.put(player, now);
        }
        player.displayClientMessage(Component.translatable(TIE_OFF_HINT_KEY), true);
    }

    /** Game time at which {@code player} last got the tie-off hint, or {@code Long.MIN_VALUE} (for tests). */
    static long lastHint(Player player) {
        synchronized (LAST_HINT) {
            return LAST_HINT.getOrDefault(player, Long.MIN_VALUE);
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
        if (rider.pulling()) {
            tickPull(level, rider, player, hook);
            return;
        }
        if (!hook.nearEndFixed()) {
            rider.letGo(null); // GR5: the rope's anchor was lost, it is no line to slide along any more
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

    /** Gap kept between the pulled player's box and the blocks around it when checking for a way ahead [blocks]. */
    private static final double PULL_CLEARANCE = 1.0e-3;
    /** Shortest leg [blocks] of the pull worth trying a step along (a shorter one would stand still for ever). */
    private static final double MIN_PULL_STEP = 1.0e-3;

    /**
     * One tick of the thrower pulling themselves along their own hand-held rope (GR5): their feet move straight toward
     * the standing spot at the hook ({@link #landing}) at {@code slide_min_speed}, the rope's crawling speed, until they
     * are within {@code dismount_distance} of it (they land there) or the way ahead is blocked (they let go where they
     * are). GR6: a straight step that is blocked is tried as its vertical leg (up the face of the wall or cliff the hook
     * bit into, the way a climb runs into the lip), then as its horizontal leg; only when both are blocked too do they
     * let go. The rope keeps running from the hook to their hand. A rope tied off meanwhile drops the puller.
     */
    static void tickPull(ServerLevel level, RopeRiderEntity rider, Player player, GrapplingHookEntity hook) {
        Vec3 b = hook.ropeFarEnd(level);
        if (b == null || hook.nearEndFixed()) {
            rider.letGo(null);
            return;
        }
        Vec3 land = landing(level, hook, true, b);
        Vec3 feet = rider.position();
        Vec3 to = land.subtract(feet);
        double dist = to.length();
        RopeSlide.Params p = GrappleConfig.slideParams();
        if (dist <= p.dismountDistance()) {
            rider.letGo(land.add(0, LANDING_NUDGE, 0));
            level.playSound(null, land.x, land.y, land.z, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8f, 1.0f);
            return;
        }
        double v = Math.min(p.minSpeed(), dist);
        Vec3 next = null;
        // GR6: straight toward the spot; blocked (the lip of a wall or cliff the hook bit into), climb straight up the
        // face first, or (under an overhang) work along it; blocked every way, the player lets go where they are
        for (Vec3 dir : new Vec3[]{to, new Vec3(0, to.y, 0), new Vec3(to.x, 0, to.z)}) {
            double len = dir.length();
            if (len < MIN_PULL_STEP) {
                continue;
            }
            Vec3 cand = feet.add(dir.scale(Math.min(v, len) / len));
            Vec3 move = cand.subtract(player.position());
            if (level.noCollision(player, player.getBoundingBox().move(move).deflate(PULL_CLEARANCE))) {
                next = cand;
                break;
            }
        }
        if (next == null) {
            rider.letGo(null);
            return;
        }
        rider.slideTo(0.0, next.distanceTo(feet), 1, next);
        player.resetFallDistance();
    }

    // ------------------------------------------------------------------ landing

    /**
     * Where a rider arriving at an end of {@code hook}'s rope stands (world, feet): on the hook's end the top of the
     * ship block the hook bit into (or the nearest standable block just above or below it), on the near end the same
     * around the ring, or the thrower's own feet when the thrower holds the rope. {@code ropeEnd} (world)
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
