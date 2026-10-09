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
 * <p><b>The bow (VIS1c).</b> The server sends each ship's bow (the helm decides it) to every client that renders the
 * ship ({@code sailing.ship.ShipBowSync}), so a sail on a ship reads the apparent wind off the real bow
 * ({@link Bow#read}): head to wind luffs from the first frame, and a push astern changes nothing. Only while the bow is
 * unknown (a sail on land, a ship that is not ours, the frames before the payload arrives) does the guess of VIS1b
 * stand in: a square sail's yards run across the ship, so its bow lies along the cloth's out axis, a stay runs along
 * the ship with its tack forward ({@code docs/guide.md}, Sails), so a stay's bow lies toward the tack, and the sign comes
 * from the ship's motion ({@link #bowSign}): a ship that has moved clearly along that axis has its bow that way. Until
 * then a square sail has no known bow ({@code 0}) and reads the wind as coming from astern ({@link #beta}), a stay
 * assumes its tack forward.
 *
 * <p><b>Braced yards (VIS1c).</b> With the bow known, whether the cloth draws follows the force model's own rule: the
 * yards and booms count as trimmed to the optimum, so the cloth draws by its {@link EfficiencyCurve} drive at the
 * apparent angle alone ({@link #fillBraced}), and a beam reach draws though the wind runs along the drawn cloth. The
 * cloth itself is still drawn square to its yards (render only). Without a bow the guess keeps the VIS1b test
 * ({@link #fill}): the drive times how squarely the wind meets the cloth.
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
     * How much a sail draws with its yards or boom braced to the optimum, as the force model assumes (VIS1c): its drive
     * coefficient at {@code betaDeg} filled smoothly up to {@link #FULL_DRIVE}. None in the no-go zone and head to wind,
     * where the drive is zero or negative; full on a beam reach and downwind.
     */
    public static float fillBraced(EfficiencyCurve curve, double betaDeg) {
        return (float) smoothstep(curve.drive(betaDeg) / FULL_DRIVE);
    }

    /**
     * Where a sail's bow lies, for {@link #beta}: the ship's synced bow when known, else the VIS1b guess along the sail's
     * own bow axis. One per renderer, refilled per sail and frame (no allocation).
     */
    public static final class Bow {
        /** The bow direction (plot frame, horizontal, unit) to pass to {@link #beta} with {@link #sign}. */
        public double x;
        public double z;
        /** +1, -1, or 0 for an unknown sign (only without a known bow). */
        public int sign;
        /** Whether this is the ship's real bow (draw by {@link #fillBraced}) rather than a guess (by {@link #fill}). */
        public boolean known;
        /** The motion guess to keep for the next frame (the sail's {@code Look.bowSign}). */
        public int guess;

        /**
         * @param shipDx            plot x of the ship's synced bow ({@code BowFrame.dx}), 0 with {@code shipDz} when unknown
         * @param shipDz            plot z of it
         * @param axisX             x of the sail's own bow axis (unit; a square sail's out axis, a stay's tack direction)
         * @param axisZ             z of it
         * @param guessed           the guessed sign so far
         * @param velocityAlongAxis the sail's speed along that axis [blocks/s], for the guess
         */
        public Bow read(int shipDx, int shipDz, double axisX, double axisZ, int guessed, double velocityAlongAxis) {
            if (shipDx != 0 || shipDz != 0) {
                known = true;
                x = shipDx;
                z = shipDz;
                sign = 1;
                double along = shipDx * axisX + shipDz * axisZ;
                guess = along > 0.5 ? 1 : along < -0.5 ? -1 : guessed; // what the guess should have found
            } else {
                known = false;
                x = axisX;
                z = axisZ;
                guess = bowSign(guessed, velocityAlongAxis);
                sign = guess;
            }
            return this;
        }
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
