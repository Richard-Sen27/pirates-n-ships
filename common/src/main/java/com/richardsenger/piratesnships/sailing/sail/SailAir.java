package com.richardsenger.piratesnships.sailing.sail;

import com.richardsenger.piratesnships.sailing.force.EfficiencyCurve;

/**
 * How the air meets a sail, for its look (VIS1b, render only; pure, no world access). The force model
 * ({@code sailing.force.SailForceModel}) assumes the crew trims the sheets optimally, so a sail has no yard or boom
 * angle: whether it draws depends on where the apparent wind comes from off the bow ({@code β}, its
 * {@link EfficiencyCurve}) and, for the look, on how squarely it meets the drawn cloth. The client works this out from
 * what it already has (the synced wind, the ship's render pose); nothing extra is synced.
 *
 * <p>All vectors are horizontal {@code (x, z)} in the frame the cloth is built in (the ship's plot frame on a ship, the
 * world on land). The apparent wind is a flow vector: the direction it blows <em>toward</em>, as {@code WindSample}.
 *
 * <p><b>The bow.</b> The client does not know where a ship's bow is (the helm decides it on the server). A square
 * sail's yards run across the ship, so its bow lies along the cloth's out axis, a stay runs along the ship with its tack
 * forward ({@code docs/guide.md}, Sails), so a stay's bow lies toward the tack. The sign comes from the ship's motion
 * ({@link #bowSign}): a ship that has moved clearly along that axis has its bow that way. Until then a square sail has
 * no known bow ({@code 0}) and reads the wind as coming from astern ({@link #beta}), a stay assumes its tack forward.
 */
public final class SailAir {

    /** Drive coefficient at which a sail counts as fully drawing; it fills smoothly from 0 up to this. */
    public static final double FULL_DRIVE = 0.1;
    /** |cos| between the apparent wind and the cloth's normal below which the cloth luffs (wind along the cloth). */
    public static final double EDGE_ON = 0.1;
    /** ... and above which the cloth meets the wind squarely enough to fill. */
    public static final double SQUARE_ON = 0.4;
    /** Speed along the bow axis [blocks/s] above which the ship's motion sets the bow. */
    public static final double MOVING = 1.0;

    private SailAir() {
    }

    /**
     * Angle off the bow the apparent wind {@code (ax, az)} comes from, 0 (head to wind) to 180 (dead astern), with the
     * unit bow {@code (bx, bz)} times {@code bowSign}; with {@code bowSign == 0} (bow unknown) the wind is read as
     * coming from astern, i.e. the larger of the two angles. A calm gives 180.
     */
    public static double beta(double ax, double az, double bx, double bz, int bowSign) {
        double speed = Math.hypot(ax, az);
        if (speed < 1.0e-9) {
            return 180.0;
        }
        double s = bowSign == 0 ? 1.0 : Math.signum(bowSign);
        // the wind comes from -a: cos beta = (-a . b) / |a|, sin beta = |a x b| / |a|
        double cos = -(ax * bx + az * bz) * s;
        double sin = Math.abs(ax * bz - az * bx);
        double deg = Math.toDegrees(Math.atan2(sin, cos));
        return bowSign == 0 ? Math.max(deg, 180.0 - deg) : deg;
    }

    /** The bow sign after the ship moved with {@code velocityAlong} [blocks/s] along the bow axis. */
    public static int bowSign(int current, double velocityAlong) {
        if (velocityAlong > MOVING) {
            return 1;
        }
        if (velocityAlong < -MOVING) {
            return -1;
        }
        return current;
    }

    /**
     * |cos| of the angle between the apparent wind {@code (ax, az)} and the cloth's normal {@code (nx, nz)} (unit): 1
     * when the wind blows straight into the cloth, 0 when it runs along it. A calm gives 0.
     */
    public static double squareness(double ax, double az, double nx, double nz) {
        double speed = Math.hypot(ax, az);
        return speed < 1.0e-9 ? 0.0 : Math.min(1.0, Math.abs(ax * nx + az * nz) / speed);
    }

    /**
     * How much the sail draws, 0 (luffing) to 1 (full): its drive coefficient at {@code betaDeg} filled smoothly up to
     * {@link #FULL_DRIVE} (none in the no-go zone, where the drive is zero or negative), times how squarely the wind
     * meets the cloth (none when it runs along the cloth, full from {@link #SQUARE_ON}).
     */
    public static float fill(EfficiencyCurve curve, double betaDeg, double squareness) {
        double drive = smoothstep(curve.drive(betaDeg) / FULL_DRIVE);
        double square = smoothstep((squareness - EDGE_ON) / (SQUARE_ON - EDGE_ON));
        return (float) (drive * square);
    }

    static double smoothstep(double x) {
        double c = Math.min(1.0, Math.max(0.0, x));
        return c * c * (3.0 - 2.0 * c);
    }
}
