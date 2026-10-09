package com.richardsenger.piratesnships.sailing.oars;

import java.util.List;

/**
 * The thrust of a rowing crew (OAR1, docs/design.md §5.3b), pure.
 *
 * <h2>Thrust</h2>
 * Every rower adds {@code thrust_per_rower} along the bow at the ship's centre of mass, a rower seated on an oar bench
 * {@code bench_bonus} times that; the whole is scaled by the crew's work speed (the upkeep's factor: a hungry, thirsty
 * or drunk crew rows slower, as it hauls slower) and by the <b>fade</b> {@code 1 − v / max_speed} (clamped to 0..1),
 * with {@code v} the ship's speed along the bow. So the oars push hardest from rest, never drive the ship past
 * {@code max_speed} on their own (the force reaches 0 there) and add nothing to a ship the sails already drive faster.
 * Going astern ({@code v < 0}) the fade stays 1.
 *
 * <h2>Side torque</h2>
 * A rower acts at {@code side_torque} blocks out to its side of the centre line, so an unequal number of rowers per
 * side (by thrust) yaws the ship a little: in the ship frame (x port, z bow) a port rower's force {@code f} at
 * {@code (L, 0, 0)} gives {@code r × F = (0, −L·f, 0)}, a starboard rower's {@code (0, +L·f, 0)}; a rower without a
 * side (a bench facing fore or aft) none.
 *
 * <h2>Shipping oars by themselves</h2>
 * {@link #watch}: while a sail is set and the ship runs faster than {@code max_speed × ship_oars_speed_factor} along
 * the bow, a counter rises by one per tick, otherwise it falls back to 0; at {@code ship_oars_ticks} the crew ship oars.
 * The margin above {@code max_speed} and the count are the hysteresis: the oars alone never reach {@code max_speed},
 * a gust or a wave has to last.
 */
public final class OarRules {

    /** The slowest work speed (as {@code station.Stations.MIN_WORK_SPEED}): rowing never stops for hunger alone. */
    public static final double MIN_WORK_SPEED = 0.05;

    /**
     * @param thrustPerRower      force of one rower along the bow [kpg·blocks/s²]
     * @param benchBonus          factor on a rower seated on an oar bench
     * @param maxSpeed            speed along the bow at which the oars' force has faded to 0 [blocks/s]
     * @param sideTorque          lever of a rower's force about the centre line [blocks]; 0 = no yaw
     * @param shipOarsSpeedFactor the crew ship oars above {@code maxSpeed} times this, under sail
     * @param shipOarsTicks       ticks the ship must stay that fast first
     */
    public record Params(double thrustPerRower, double benchBonus, double maxSpeed, double sideTorque,
                         double shipOarsSpeedFactor, int shipOarsTicks) {
        public static final Params DEFAULTS = new Params(40.0, 1.25, 3.0, 0.5, 1.1, 40);
    }

    /** Which side of the ship a rower pulls on. */
    public enum Side {
        PORT(1), STARBOARD(-1), NONE(0);

        private final int sign;

        Side(int sign) {
            this.sign = sign;
        }

        /** +1 port, −1 starboard, 0 none: the sign of the rower's lever along the ship frame's x (port). */
        public int sign() {
            return sign;
        }

        public Side opposite() {
            return switch (this) {
                case PORT -> STARBOARD;
                case STARBOARD -> PORT;
                case NONE -> NONE;
            };
        }
    }

    /** One rower at its oar: seated on a bench or standing at the rail, on a side. */
    public record Rower(boolean bench, Side side) {
    }

    /**
     * The oars' push on the ship, ship frame.
     *
     * @param force     along the bow (+z) [kpg·blocks/s²]
     * @param yawTorque about the ship's up axis (+y) [kpg·blocks²/s²]
     * @param fade      the speed fade that was applied, 0..1
     */
    public record Thrust(double force, double yawTorque, double fade) {
        public static final Thrust NONE = new Thrust(0.0, 0.0, 0.0);
    }

    private OarRules() {
    }

    /** {@code 1 − v / max}, clamped to 0..1; 0 for a non-positive {@code max}. */
    public static double fade(double forwardSpeed, double maxSpeed) {
        if (!(maxSpeed > 0.0)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, 1.0 - forwardSpeed / maxSpeed));
    }

    /** The force of one rower before the fade: the thrust, the bench bonus and the work speed (clamped to its range). */
    public static double rowerForce(Rower rower, double workSpeed, Params p) {
        double work = Math.max(MIN_WORK_SPEED, Math.min(1.0, Double.isFinite(workSpeed) ? workSpeed : 1.0));
        return Math.max(0.0, p.thrustPerRower()) * (rower.bench() ? Math.max(0.0, p.benchBonus()) : 1.0) * work;
    }

    /** The oars' thrust of {@code rowers} at the ship's speed along the bow {@code forwardSpeed}. */
    public static Thrust thrust(List<Rower> rowers, double workSpeed, double forwardSpeed, Params p) {
        if (rowers.isEmpty()) {
            return Thrust.NONE;
        }
        double fade = fade(forwardSpeed, p.maxSpeed());
        double force = 0.0;
        double lever = 0.0; // Σ side · f
        for (Rower r : rowers) {
            double f = rowerForce(r, workSpeed, p);
            force += f;
            lever += r.side().sign() * f;
        }
        return new Thrust(force * fade, -Math.max(0.0, p.sideTorque()) * lever * fade, fade);
    }

    /**
     * One tick of the shipping-oars watch: the new count of consecutive fast ticks. Fast = a sail is set and the
     * ship's speed along the bow exceeds {@code max_speed × ship_oars_speed_factor}.
     */
    public static int watch(int fastTicks, double forwardSpeed, boolean sailsSet, Params p) {
        boolean fast = sailsSet && forwardSpeed > p.maxSpeed() * p.shipOarsSpeedFactor();
        return fast ? fastTicks + 1 : 0;
    }

    /** Whether the crew ship oars by themselves after {@code fastTicks} fast ticks ({@link #watch}). */
    public static boolean shipOars(int fastTicks, Params p) {
        return fastTicks >= Math.max(1, p.shipOarsTicks());
    }
}
