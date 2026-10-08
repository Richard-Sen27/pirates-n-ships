package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Hauling a hooked ship by hand (GR5, docs/design.md §8.3 "Hauling"). Server-authoritative; the server knows the
 * sneak key through {@link Player#isShiftKeyDown()}, so no payload is needed.
 *
 * <p><b>Freeze</b> ({@link #tick}, every game tick of a latched hook): while {@link HaulMath#hauls} holds (toggle
 * {@code grapple.hauling}, the hook on a ship, the near end in the thrower's hand rather than tied to a cleat or ring,
 * the thrower sneaking, not riding a rope, not aboard the hooked ship) the rope's length is frozen at the distance from
 * the hand to the hook when sneaking began. Releasing sneak unfreezes it.
 *
 * <p><b>Tension</b> ({@link #onPhysicsTick}, every physics substep): the distance beyond the frozen length pulls the
 * hooked ship toward the hand with {@link HaulMath#tension} ({@code haul_stiffness}, {@code haul_damping}, capped at
 * {@code haul_max_force}), recorded in the force group {@code pirates_n_ships:haul} ({@link ShipBody#applyHaulImpulse}).
 * Like the other rope forces ({@link GrappleService}) the horizontal pull acts at the hook's horizontal position but at
 * the height of the ship's center of mass: it turns the ship about the vertical axis (a ship lying askew swings round)
 * without heeling it, which small block hulls cannot right (docs/sable-notes.md §9.0d). Above the cap the rope slips
 * ({@link HaulMath#slip}): the frozen length grows so the pull stays at the cap. A ship at rest is woken once per game
 * tick (docs/sable-notes.md §9.0i).
 *
 * <p><b>The player</b> feels nothing while standing on the ground or a deck; airborne or swimming, the tension pulls
 * them toward the hook ({@code haul_player_pull}) and the rope stops them from moving farther away.
 *
 * <p>Hooks never latch onto entities ({@link GrapplingHookEntity#onHitEntity} misses), so there is no entity hauling.
 */
public final class GrappleHaul {

    /** One hook's hauling state (server). */
    public static final class State {
        /** Frozen rope length [blocks], NaN when not frozen. */
        private double frozen = Double.NaN;
        /** The rope's hand end (world) as of the last game tick, null when not frozen. */
        private @Nullable Vec3 hand;
        /** Velocity of the hand end [m/s], from the last two game ticks. */
        private Vec3 handVelocity = Vec3.ZERO;
        /** Tension of the last physics substep [kpg·m/s²]. */
        private double tension;
        /** Highest tension of any substep since the rope was frozen [kpg·m/s²]. */
        private double peak;
        /** Substeps with a pull since the rope was frozen. */
        private int pullingSubsteps;

        public boolean frozen() {
            return !Double.isNaN(frozen);
        }

        /** The frozen rope length [blocks], NaN when not frozen. */
        public double frozenLength() {
            return frozen;
        }

        /** Tension of the last physics substep [kpg·m/s²], 0 when not frozen. */
        public double tension() {
            return tension;
        }

        /** Highest tension recorded since the rope was frozen (debug and tests). */
        public double peakTension() {
            return peak;
        }

        /** Physics substeps with a pull since the rope was frozen (debug and tests). */
        public int pullingSubsteps() {
            return pullingSubsteps;
        }

        void clear() {
            frozen = Double.NaN;
            hand = null;
            handVelocity = Vec3.ZERO;
            tension = 0.0;
        }
    }

    private GrappleHaul() {
    }

    /**
     * One game tick of a latched hook: freezes, keeps or clears the rope length, lets an overloaded rope slip, wakes
     * the hooked ship and pulls an airborne thrower. Returns whether the frozen rope is taut now (for the straight
     * rope look).
     */
    static boolean tick(ServerLevel level, GrapplingHookEntity hook, @Nullable ShipBody ship, Vec3 hookWorld, @Nullable Entity owner) {
        State s = hook.haul();
        boolean hauls = owner instanceof Player player && player.isAlive() && player.level() == level && !player.isSpectator()
                && HaulMath.hauls(GrappleConfig.ENABLED.get() && GrappleConfig.HAULING.get(), ship != null,
                !hook.nearEndFixed(), player.isShiftKeyDown(), player.isPassenger(), aboard(level, player, ship));
        if (!hauls) {
            s.clear();
            return false;
        }
        Player player = (Player) owner;
        Vec3 hand = hook.ropeNearEnd(level);
        if (hand == null) {
            s.clear();
            return false;
        }
        double d = hand.distanceTo(hookWorld);
        if (!s.frozen()) {
            s.frozen = d;
            s.peak = 0.0;
            s.pullingSubsteps = 0;
            s.handVelocity = Vec3.ZERO;
        } else if (s.hand != null) {
            s.handVelocity = hand.subtract(s.hand).scale(20.0); // blocks per tick to m/s
        }
        s.hand = hand;
        double k = GrappleConfig.HAUL_STIFFNESS.get();
        double max = GrappleConfig.HAUL_MAX_FORCE.get();
        s.frozen = HaulMath.slip(d, s.frozen, k, max);
        double excess = HaulMath.excess(d, s.frozen);
        if (excess <= 0) {
            return false;
        }
        ship.addVelocity(new Vector3d(), new Vector3d()); // wakes a ship at rest (sable-notes §9.0i)
        if (!player.onGround() || player.isInWater()) {
            pullPlayer(player, hookWorld.subtract(hand).scale(1.0 / d), HaulMath.tension(excess, 0.0, k, 0.0, max), max);
        }
        return true;
    }

    /** The thrower stands on (or rides) the ship the hook holds. */
    private static boolean aboard(ServerLevel level, Player player, @Nullable ShipBody ship) {
        if (ship == null) {
            return false;
        }
        ShipBody under = GrappleService.shipOf(level, player);
        return under != null && under.id().equals(ship.id());
    }

    /**
     * An airborne or swimming player on a taut frozen rope: the rope stops them moving away from the hook ({@code u}
     * points from the hand to the hook) and pulls them toward it.
     */
    private static void pullPlayer(Player player, Vec3 u, double tension, double max) {
        Vec3 v = player.getDeltaMovement();
        double outward = -v.dot(u);
        if (outward > 0) {
            v = v.add(u.scale(outward));
        }
        v = v.add(u.scale(HaulMath.playerPull(tension, max, GrappleConfig.HAUL_PLAYER_PULL.get())));
        player.setDeltaMovement(v);
        player.resetFallDistance();
        if (player instanceof ServerPlayer) {
            player.hurtMarked = true; // sends the new velocity to the client, which moves its own player
        }
    }

    /** Physics substep: every frozen rope pulls its hooked ship toward the hand. */
    public static void onPhysicsTick(ServerLevel level, double timeStep) {
        if (!GrappleConfig.ENABLED.get() || !GrappleConfig.HAULING.get()) {
            return;
        }
        for (GrapplingHookEntity h : GrappleService.activeHooks()) {
            if (h.isRemoved() || h.level() != level || !h.haul().frozen()) {
                continue;
            }
            try {
                pull(level, h, timeStep);
            } catch (RuntimeException e) {
                Constants.LOG.error("Grappling rope hauling failed", e);
            }
        }
    }

    private static void pull(ServerLevel level, GrapplingHookEntity h, double dt) {
        State s = h.haul();
        Vec3 hookPlot = h.plotPos();
        Vec3 hand = s.hand;
        ShipBody ship = h.shipId() == null ? null : SableShips.byId(level, h.shipId());
        if (hookPlot == null || hand == null || ship == null) {
            s.tension = 0.0;
            return;
        }
        Vec3 hookWorld = ship.toWorld(hookPlot);
        Vec3 rope = hookWorld.subtract(hand);
        double d = rope.length();
        double excess = HaulMath.excess(d, s.frozen);
        if (d < 1.0e-6 || excess <= 0) {
            s.tension = 0.0;
            return;
        }
        Vec3 u = rope.scale(1.0 / d); // from the hand to the hook
        double rate = ship.velocityAt(hookPlot).subtract(s.handVelocity).dot(u);
        double t = HaulMath.tension(excess, rate, GrappleConfig.HAUL_STIFFNESS.get(), GrappleConfig.HAUL_DAMPING.get(),
                GrappleConfig.HAUL_MAX_FORCE.get());
        s.tension = t;
        if (t <= 0) {
            return;
        }
        s.peak = Math.max(s.peak, t);
        s.pullingSubsteps++;
        // horizontal part of the pull, toward the hand, at the hook's x/z and the center of mass height (no heel)
        if (Math.hypot(u.x, u.z) < 1.0e-6) {
            return; // a rope straight up or down: nothing to haul along the water
        }
        Vec3 force = new Vec3(-u.x * t, 0, -u.z * t);
        Vector3d com = new Vector3d();
        if (!ship.centerOfMass(com)) {
            return;
        }
        Vector3d comWorld = ship.toWorld(com, new Vector3d());
        Vec3 at = ship.toPlot(new Vec3(hookWorld.x, comWorld.y, hookWorld.z));
        Vector3d local = ship.orientation().transformInverse(new Vector3d(force.x * dt, 0, force.z * dt));
        ship.applyHaulImpulse(new Vector3d(at.x, at.y, at.z), local);
    }
}
