package com.richardsenger.piratesnships.station.helm;

/**
 * The NPC helmsman's steering rule (WS3a, docs/design.md §6, §10.4): a proportional controller on the heading error
 * with a deadband and yaw-rate anticipation. Pure: no world access.
 *
 * <p>Angles are compass bearings in degrees (0 = north, 90 = east, clockwise, the convention of
 * {@code SailingRuntime#headingDegrees}); a positive rudder angle is to starboard, which turns the bow clockwise
 * (heading grows), as the HELM1 and spike 3 tests measure. So the rudder has the sign of the heading error.
 *
 * <pre>
 *   error     = bearing − heading, wrapped to (−180, 180]
 *   predicted = error − yawRate · anticipation      (where the error will be after {@code anticipation} seconds)
 *   rudder    = 0                                   if |error| ≤ deadband and |predicted| ≤ deadband
 *             = clamp(gain · predicted, ±maxRudder) otherwise
 * </pre>
 *
 * The anticipation term eases the rudder off, and puts it the other way, while the bow still swings toward the
 * bearing, so a slow, heavy hull does not overshoot.
 */
public final class CourseKeeper {

    /**
     * @param gain                rudder degrees per degree of (predicted) heading error
     * @param deadbandDegrees     heading errors at most this large leave the rudder midships
     * @param anticipationSeconds how far ahead the yaw rate is projected
     */
    public record Params(double gain, double deadbandDegrees, double anticipationSeconds) {
        public static final Params DEFAULTS = new Params(2.0, 3.0, 1.5);

        public Params {
            gain = Math.max(0.0, gain);
            deadbandDegrees = Math.max(0.0, deadbandDegrees);
            anticipationSeconds = Math.max(0.0, anticipationSeconds);
        }
    }

    private CourseKeeper() {
    }

    /**
     * The rudder angle [degrees, positive = starboard] that steers from {@code heading} toward {@code bearing}.
     *
     * @param yawRate degrees per second, positive = the bow swings clockwise (to starboard)
     */
    public static double rudder(double heading, double bearing, double yawRate, double maxRudder, Params params) {
        double max = Math.max(0.0, maxRudder);
        double error = error(heading, bearing);
        if (!Double.isFinite(error)) {
            return 0.0;
        }
        double rate = Double.isFinite(yawRate) ? yawRate : 0.0;
        double predicted = error - rate * params.anticipationSeconds();
        if (Math.abs(error) <= params.deadbandDegrees() && Math.abs(predicted) <= params.deadbandDegrees()) {
            return 0.0;
        }
        double r = params.gain() * predicted;
        return Math.max(-max, Math.min(max, r));
    }

    /** {@code bearing − heading} wrapped to (−180, 180]: positive = the bearing lies to starboard. */
    public static double error(double heading, double bearing) {
        double e = (bearing - heading) % 360.0;
        if (e <= -180.0) e += 360.0;
        if (e > 180.0) e -= 360.0;
        return e;
    }

    /** Compass bearing [0, 360) from (fromX, fromZ) to (toX, toZ) in world coordinates (north = −z). */
    public static double bearing(double fromX, double fromZ, double toX, double toZ) {
        double b = Math.toDegrees(Math.atan2(toX - fromX, -(toZ - fromZ)));
        return b < 0.0 ? b + 360.0 : b;
    }

    /** Yaw rate [degrees per second] from two headings {@code ticks} game ticks apart; 0 when {@code ticks ≤ 0}. */
    public static double yawRate(double previousHeading, double heading, long ticks) {
        if (ticks <= 0 || !Double.isFinite(previousHeading) || !Double.isFinite(heading)) {
            return 0.0;
        }
        return error(previousHeading, heading) / (ticks / 20.0);
    }

    /** Whether a horizontal offset to the waypoint lies within {@code radius}. */
    public static boolean arrived(double dx, double dz, double radius) {
        return dx * dx + dz * dz <= radius * radius;
    }

    /**
     * Work ticks the helm station books for a course of {@code distance} blocks at {@code speed} blocks per second:
     * at least {@code minTicks}, at most {@code maxTicks}. Only an estimate: a course that is not done when it runs
     * out is taken up again ({@code HelmStation#complete}).
     */
    public static int estimateTicks(double distance, double speed, int minTicks, int maxTicks) {
        double s = speed > 0.0 ? speed : 1.0;
        double ticks = Math.max(0.0, distance) / s * 20.0;
        if (!Double.isFinite(ticks)) {
            return maxTicks;
        }
        return (int) Math.max(minTicks, Math.min(maxTicks, Math.ceil(ticks)));
    }
}
