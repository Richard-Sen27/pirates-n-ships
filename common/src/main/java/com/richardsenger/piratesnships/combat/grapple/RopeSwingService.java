package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Hanging on, swinging on and climbing one's own hand-held grappling rope (GR7, docs/design.md §8.3; the rules are
 * {@link RopeSwing}).
 *
 * <p><b>The rope's length</b> lives on the hook (server, synced to every client: {@link GrapplingHookEntity#swingLength},
 * {@link GrapplingHookEntity#syncedSwingLength}) and is kept every game tick of a latched hook by {@link #tickRope}: free
 * while the thrower stands on the ground, held once they are off it below the hook, shortened by a pull.
 *
 * <p><b>The swing</b> ({@link #apply}) runs at the end of each player tick ({@code CommonEvents.PLAYER_TICK_END}) on the
 * side that moves the player, as the ratline assist does ({@code ship.rigging.RatlineNetAssist}, RL3/RL6): the client for
 * the local player ({@code client.ClientRopeSwing}, which predicts the same length rule and adopts the server's value
 * whenever it changes), the server for a player it moves itself (a GameTest mock player). The server stays authoritative
 * over the length and corrects a real player only when they get well beyond it ({@link #SERVER_SLACK}).
 *
 * <p><b>On a ship</b> the hook moves with the hull: the pendulum is worked out relative to the hook's own motion (a
 * {@link Pivot}: its world position now and its velocity, Sable's velocity of the plot point). A player Sable carries
 * with the ship itself ({@code SubLevelEntityCollision#collide}, see RL3) gets no extra velocity, as in RL3.
 *
 * <p><b>Pulling</b> ({@link #pull}): a use of the thrower's own latched hand-held rope with an empty hand or a hook in it
 * shortens the rope by {@code rope_climb_step} (no more often than {@code pull_cooldown_ticks}, shown as the hook
 * item's cooldown), which draws the player up toward the hook; within reach of the hook the pull puts them on the
 * standing spot there ({@link RopeSlideService#landing}). GR6's climb rule stays: the hook must be at least
 * {@code climb_min_rise} above the feet, or above the hand of a player already hanging on the rope.
 */
public final class RopeSwingService {

    /** A real player this far [blocks] beyond the rope's length on the server is corrected (lag and cheats). */
    static final double SERVER_SLACK = 3.0;
    /** A pull that would leave the hand within this distance [blocks] of the hook lands the climber at the hook. */
    static final double ARRIVE_LENGTH = 1.0;
    /** How far [blocks] from the hand the standing spot at the hook may be for a pull to put the climber there. */
    static final double ARRIVE_REACH = 4.0;
    /** How high above the standing spot an arriving climber is put [blocks], so they drop onto it. */
    static final double LANDING_NUDGE = 0.1;

    /** Where the rope's hook end is and how it moves. */
    public interface Pivot {
        /** The hook (world) now. */
        Vec3 position();

        /** The hook's own velocity [blocks/tick, world]: its ship's motion at the hook, zero on a world block. */
        Vec3 velocity();

        /** Whether Sable carries {@code player} with the hook's ship itself (then the player's velocity is relative already). */
        boolean carries(Player player);
    }

    private RopeSwingService() {
    }

    // ------------------------------------------------------------------ who holds the rope

    /**
     * Whether {@code player} holds their hand-held rope (both sides): the toggles, alive, not a spectator, not flying or
     * gliding, not riding, not sneaking (sneaking lets go; with the hook in a ship it freezes the rope for hauling, GR5),
     * and the main hand empty or holding a hook (any other item in hand lets go).
     */
    public static boolean holds(Player player) {
        if (!GrappleConfig.ENABLED.get() || !GrappleConfig.SWING.get()) {
            return false;
        }
        if (!player.isAlive() || player.isSpectator() || player.getAbilities().flying || player.isFallFlying()
                || player.isPassenger() || player.isShiftKeyDown()) {
            return false;
        }
        GrappleLaunch.Held main = GrapplingHookItem.heldOf(player.getMainHandItem());
        return main == GrappleLaunch.Held.EMPTY || main == GrappleLaunch.Held.HOOK;
    }

    /** Where the player holds the rope (world): {@link GrapplingHookEntity#HAND_BELOW_EYES} below the eyes. */
    public static Vec3 hand(Player player) {
        return player.getEyePosition().subtract(0, GrapplingHookEntity.HAND_BELOW_EYES, 0);
    }

    // ------------------------------------------------------------------ server: the rope's length

    /** One game tick of a latched hook (server): keeps the rope's length for its thrower ({@link RopeSwing#track}). */
    static void tickRope(ServerLevel level, GrapplingHookEntity hook, @Nullable ShipBody ship, Vec3 hookWorld, @Nullable Entity owner) {
        if (!(owner instanceof Player player) || hook.nearEndFixed() || player.level() != level) {
            hook.setSwingLength(Double.NaN);
            return;
        }
        Vec3 hand = hand(player);
        double d = hand.distanceTo(hookWorld);
        double length = RopeSwing.track(hook.swingLength(), d, holds(player), player.onGround(), hookWorld.y > hand.y,
                hook.ropeLength());
        hook.setSwingLength(length);
        if (!RopeSwing.holding(length)) {
            return;
        }
        player.resetFallDistance(); // the rope holds them: only a drop after letting go hurts
        if (player instanceof ServerPlayer && d > length + SERVER_SLACK) {
            apply(player, serverPivot(hook, ship, hookWorld), length);
            player.hurtMarked = true; // sends the corrected velocity to the client
        }
    }

    /** The server's pivot of {@code hook}: on its ship (velocity per second from Sable, ÷ 20) or a fixed world point. */
    static Pivot serverPivot(GrapplingHookEntity hook, @Nullable ShipBody ship, Vec3 hookWorld) {
        Vec3 plot = hook.plotPos();
        return new Pivot() {
            @Override
            public Vec3 position() {
                return hookWorld;
            }

            @Override
            public Vec3 velocity() {
                return ship != null && plot != null ? ship.velocityAt(plot).scale(1.0 / 20.0) : Vec3.ZERO;
            }

            @Override
            public boolean carries(Player player) {
                if (ship == null) {
                    return false;
                }
                ShipBody on = ShipEntities.standingOrRiding(player);
                return on != null && on.id().equals(ship.id());
            }
        };
    }

    // ------------------------------------------------------------------ the swing (both sides)

    /** {@code PLAYER_TICK_END} on the server: a player the server moves itself (a GameTest mock player). */
    public static void onPlayerTick(Player player) {
        if (!(player.level() instanceof ServerLevel level) || player instanceof ServerPlayer) {
            return; // real players are moved by their client (client.ClientRopeSwing)
        }
        GrapplingHookEntity hook = GrappleService.hookOf(player);
        if (hook == null || hook.state() != GrapplingHookEntity.State.LATCHED || hook.nearEndFixed()
                || !RopeSwing.holding(hook.swingLength())) {
            return;
        }
        Vec3 far = hook.ropeFarEnd(level);
        if (far == null) {
            return;
        }
        ShipBody ship = hook.shipId() == null ? null : SableShips.byId(level, hook.shipId());
        apply(player, serverPivot(hook, ship, far), hook.swingLength());
    }

    /**
     * The swing's correction of {@code player}'s velocity for the next tick on a rope of {@code length} around
     * {@code pivot} ({@link RopeSwing#step}) with this tick's walking input; vanilla's air drag and air control are
     * taken out only out of the water.
     */
    public static void apply(Player player, Pivot pivot, double length) {
        Vec3 rel = hand(player).subtract(pivot.position());
        Vec3 pv = pivot.carries(player) ? Vec3.ZERO : pivot.velocity();
        RopeSwing.Input input = new RopeSwing.Input(player.zza, player.xxa, player.getYRot(), RopeSwing.airControl(player.isSprinting()));
        Vec3 v = RopeSwing.step(rel, player.getDeltaMovement(), pv, length, input, !player.isInWater(), GrappleConfig.swingParams());
        player.setDeltaMovement(v);
        player.resetFallDistance();
    }

    // ------------------------------------------------------------------ pulling (server)

    /**
     * The thrower uses their own latched hand-held rope (from {@link RopeSlideService#tryBoard}): one pull, unless the
     * last one was less than {@code pull_cooldown_ticks} ago (then nothing, {@link RopeSlideService.Board#COOLDOWN}) or
     * the hook is no climb (GR6, the tie-off hint).
     */
    static RopeSlideService.Board pull(ServerLevel level, Player player, GrapplingHookEntity hook) {
        if (!GrappleConfig.SWING.get()) {
            return RopeSlideService.Board.BUSY; // the rope never holds the player: nothing to climb
        }
        if (player.getCooldowns().isOnCooldown(CombatContent.GRAPPLING_HOOK.get())) {
            return RopeSlideService.Board.COOLDOWN;
        }
        Vec3 far = hook.ropeFarEnd(level);
        if (far == null) {
            return RopeSlideService.Board.NOT_LATCHED;
        }
        Vec3 hand = hand(player);
        double length = hook.swingLength();
        boolean hanging = RopeSwing.holding(length) && far.y > hand.y;
        if (!hanging && !RopeSlideService.isClimb(far.y, player.getY(), GrappleConfig.CLIMB_MIN_RISE.get())) {
            RopeSlideService.hintTieOff(level, player);
            return RopeSlideService.Board.NOT_A_CLIMB; // GR6: level, below or across a gap: tie the rope off to use it as a line
        }
        int cooldown = GrappleConfig.PULL_COOLDOWN_TICKS.get();
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(CombatContent.GRAPPLING_HOOK.get(), cooldown);
        }
        hook.countPull(); // GR8: every client shows the hand-over-hand (RopePose)
        double d = hand.distanceTo(far);
        double step = GrappleConfig.ROPE_CLIMB_STEP.get();
        if (RopeSwing.arrives(length, d, step, ARRIVE_LENGTH)) {
            Vec3 land = RopeSlideService.landing(level, hook, true, far);
            if (land.distanceTo(hand) <= ARRIVE_REACH) {
                hook.setSwingLength(Double.NaN);
                player.teleportTo(land.x, land.y + LANDING_NUDGE, land.z);
                player.setDeltaMovement(Vec3.ZERO);
                player.resetFallDistance();
                player.hurtMarked = true;
                level.playSound(null, land.x, land.y, land.z, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8f, 1.0f);
                return RopeSlideService.Board.OK;
            }
        }
        hook.setSwingLength(RopeSwing.pull(length, d, step));
        player.resetFallDistance();
        level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 0.5f, 1.4f);
        return RopeSlideService.Board.OK;
    }
}
