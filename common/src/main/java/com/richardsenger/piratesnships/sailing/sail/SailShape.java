package com.richardsenger.piratesnships.sailing.sail;

import com.richardsenger.piratesnships.sailing.force.SailTrim;

/**
 * The moving shape of a sail's cloth in the wind (VIS1b, docs/design.md §4.8 visual backlog (3); render only, pure, no
 * world access). The sail renderers ({@code sailing/client/YardClothRenderer}, {@code StayClothRenderer}) build the
 * cloth as a grid and push every grid point along the cloth's normal by {@link #square} or {@link #triangle}; the
 * flag's {@code FlagRipple} is the model.
 *
 * <ul>
 *   <li><b>Drawing</b> ({@link SailAir#fill} 1): the cloth bellies out to leeward, deepest in the middle, the depth
 *       growing with the apparent wind up to {@link #cap} ({@code sail_visuals.max_belly} for a 4-block drop), and
 *       breathing slowly ({@link #BREATH} over {@link #BREATH_PERIOD_TICKS}).</li>
 *   <li><b>Luffing</b> (fill 0: the wind runs along the cloth, or comes from ahead in the no-go zone): a slack cloth
 *       with a fast flutter that travels across it, its swing growing with the wind
 *       ({@code sail_visuals.flutter_amplitude} in a full wind).</li>
 *   <li><b>Reefed</b> (half trim): the same at {@link #REEF_FACTOR} of the depth and swing.</li>
 *   <li><b>Furled</b>: nothing; the renderers keep drawing the bundle as before.</li>
 *   <li><b>Calm</b>: a slight sag ({@link #SLACK} of the cap) to leeward, no breathing, no flutter.</li>
 * </ul>
 * Belly, flutter and the side the cloth bellies to are eased by a {@link Look} per sail, so a change of wind, trim or
 * side never makes the cloth jump. Wave speeds are fixed (only amplitudes follow the wind), so the waves never jump
 * either.
 */
public final class SailShape {

    /** Apparent wind [blocks/s] at which a drawing sail reaches its full belly and a luffing one its full flutter. */
    public static final float FULL_WIND = 12f;
    /** Drop [blocks] that {@code max_belly} is given for; bigger sails belly deeper in proportion. */
    public static final float REFERENCE_DROP = 4f;
    /** The cap scales with the drop between these factors of {@code max_belly}. */
    public static final float MIN_SIZE_FACTOR = 0.25f;
    public static final float MAX_SIZE_FACTOR = 2f;
    /** Belly and flutter of a reefed (half trim) sail, against a full one. */
    public static final float REEF_FACTOR = 0.6f;
    /** Sag of a slack cloth in a calm, as a fraction of the cap. */
    public static final float SLACK = 0.1f;
    /** Breathing of a drawing belly: ± this fraction over this period. */
    public static final float BREATH = 0.07f;
    public static final float BREATH_PERIOD_TICKS = 90f;
    /** Flutter: ticks for one crest to pass, waves across the cloth, waves per block down. */
    public static final float FLUTTER_PERIOD_TICKS = 9f;
    public static final float FLUTTER_WAVES_ACROSS = 1.5f;
    public static final float FLUTTER_WAVES_PER_BLOCK = 0.35f;
    /** How fast belly and flutter follow their targets: time constant [ticks]. */
    public static final float EASE_TICKS = 10f;
    /** How fast the cloth crosses to the other side when the wind flips it: side units (-1..1) per tick. */
    public static final float SIDE_PER_TICK = 0.1f;
    /** Velocity smoothing time constant [ticks] for the ship's motion at the sail. */
    public static final float VELOCITY_TICKS = 10f;
    /** Longest frame gap [ticks] eased in one step; a longer gap (pause, chunk reload) just continues. */
    public static final float MAX_STEP_TICKS = 20f;

    private SailShape() {
    }

    /** Deepest belly [blocks] of a sail with a drop of {@code drop} blocks for {@code max_belly}. */
    public static float cap(float drop, float maxBelly) {
        float f = Math.min(MAX_SIZE_FACTOR, Math.max(MIN_SIZE_FACTOR, drop / REFERENCE_DROP));
        return Math.max(0f, maxBelly) * f;
    }

    /** Wind factor 0..1 of an apparent wind speed [blocks/s] (negative or NaN: calm). */
    public static float wind(double apparentSpeed) {
        if (Double.isNaN(apparentSpeed) || apparentSpeed <= 0.0) {
            return 0f;
        }
        return (float) Math.min(1.0, apparentSpeed / FULL_WIND);
    }

    /**
     * The belly the cloth settles to [blocks, ≥ 0]: from the calm sag up to {@link #cap} as the drawing wind grows,
     * the sag only while luffing, reefed at {@link #REEF_FACTOR}, none furled.
     *
     * @param fill how much the sail draws ({@link SailAir#fill})
     */
    public static float bellyTarget(SailTrim trim, float drop, double apparentSpeed, float fill, float maxBelly) {
        if (trim == SailTrim.FURLED) {
            return 0f;
        }
        float cap = cap(drop, maxBelly);
        float slack = SLACK * cap;
        float f = Math.min(1f, Math.max(0f, fill));
        float b = slack + (cap - slack) * wind(apparentSpeed) * f;
        return trim == SailTrim.HALF ? b * REEF_FACTOR : b;
    }

    /** The flutter swing the cloth settles to [blocks, ≥ 0]: only while it luffs, growing with the wind. */
    public static float flutterTarget(SailTrim trim, float drop, double apparentSpeed, float fill, float flutterAmplitude) {
        if (trim == SailTrim.FURLED) {
            return 0f;
        }
        float size = Math.min(1.5f, Math.max(0.5f, drop / REFERENCE_DROP));
        float f = 1f - Math.min(1f, Math.max(0f, fill));
        float a = Math.max(0f, flutterAmplitude) * size * wind(apparentSpeed) * f;
        return trim == SailTrim.HALF ? a * REEF_FACTOR : a;
    }

    /** The furthest any point can move off the cloth's rest plane [blocks], for culling boxes. */
    public static float maxReach(float maxBelly, float flutterAmplitude) {
        return Math.max(0f, maxBelly) * MAX_SIZE_FACTOR * (1f + BREATH) + Math.max(0f, flutterAmplitude) * 1.5f;
    }

    /** A steady phase per sail (from its block position), so neighbouring sails do not move in step. */
    public static float phase(long blockPosKey) {
        long h = blockPosKey * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        return (float) ((h >>> 40) / (double) (1L << 24) * Math.PI * 2.0);
    }

    /**
     * Displacement [blocks] of a square sail's cloth along its out axis (positive: the {@code +out} side) at a grid
     * point, on top of the clearance from the yards ({@link ClothGeometry#clearance}).
     *
     * @param t        across the cloth, 0 at the negative edge to 1 at the positive one
     * @param s        down the drawn cloth, 0 at the upper yard to 1 at its foot
     * @param footFree whether the foot hangs free (reefed) instead of being held by the lower yard
     * @param height   how far the drawn cloth hangs [blocks]
     * @param time     game time [ticks]
     */
    public static float square(Look look, float t, float s, boolean footFree, float height, double time, float phase) {
        float across = 1f - (2f * t - 1f) * (2f * t - 1f);
        float down = footFree ? (float) Math.sin(Math.PI * 0.5 * s) : (float) Math.sin(Math.PI * s);
        float belly = look.side * look.belly * across * down * breath(look, time, phase);
        if (look.flutter <= 0f) {
            return belly;
        }
        double arg = 2.0 * Math.PI * (FLUTTER_WAVES_ACROSS * t + FLUTTER_WAVES_PER_BLOCK * s * height - time / FLUTTER_PERIOD_TICKS) + phase;
        return belly + look.flutter * down * (0.4f + 0.6f * across) * (float) Math.sin(arg);
    }

    /**
     * Displacement [blocks] of a triangular sail's cloth along its normal (positive: the {@code +normal} side) at the
     * grid point {@code head + tack * u + clew * v}: zero on all three edges (stay, mast, foot), deepest in the middle;
     * the flutter travels from the stay toward the clew.
     *
     * @param size the cloth's size [blocks] for the flutter's wavelength (its longer edge)
     */
    public static float triangle(Look look, float u, float v, float size, double time, float phase) {
        float env = Math.max(0f, 27f * u * v * (1f - u - v));
        float belly = look.side * look.belly * env * breath(look, time, phase);
        if (look.flutter <= 0f) {
            return belly;
        }
        double arg = 2.0 * Math.PI * (FLUTTER_WAVES_ACROSS * v + FLUTTER_WAVES_PER_BLOCK * u * size - time / FLUTTER_PERIOD_TICKS) + phase;
        return belly + look.flutter * env * (float) Math.sin(arg);
    }

    /** The breathing factor of a drawing belly; a calm sag (no wind in the look) does not breathe. */
    private static float breath(Look look, double time, float phase) {
        return 1f + BREATH * look.drawing * (float) Math.sin(2.0 * Math.PI * time / BREATH_PERIOD_TICKS + phase);
    }

    /**
     * The eased look of one sail on the client, plus the ship's motion at the sail (for the apparent wind and the bow).
     * Mutable; the renderer keeps one per sail and updates it once per frame.
     */
    public static final class Look {
        /** Belly depth [blocks, ≥ 0], flutter swing [blocks, ≥ 0], how much it draws (0..1, for breathing). */
        public float belly;
        public float flutter;
        public float drawing;
        /** The side the cloth bellies to, -1..1, crossing smoothly when the wind flips it. */
        public float side;
        /** The bow along the sail's bow axis: +1, -1 or 0 (unknown), see {@link SailAir}. */
        public int bowSign;
        /** Smoothed velocity of the sail [blocks/s], frame-independent (world on a ship, zero on land). */
        public double velocityX;
        public double velocityZ;

        private double time = Double.NaN;
        private double lastX;
        private double lastZ;
        private double lastPositionTime = Double.NaN;

        public Look(int side, int bowSign) {
            this.side = side >= 0 ? 1f : -1f;
            this.bowSign = bowSign;
        }

        /** Whether {@link #approach} has run once. */
        public boolean started() {
            return !Double.isNaN(time);
        }

        /**
         * Feeds the sail's world position at game time {@code now} [ticks]; the velocity is the smoothed difference.
         * A jump faster than 60 blocks/s (teleport, a ship reloaded) or a long gap restarts the estimate.
         */
        public void trackPosition(double x, double z, double now) {
            double dt = now - lastPositionTime;
            if (Double.isNaN(dt) || dt > MAX_STEP_TICKS || dt < 0) {
                lastX = x;
                lastZ = z;
                lastPositionTime = now;
                return;
            }
            if (dt < 1.0e-3) {
                return;
            }
            double vx = (x - lastX) / dt * 20.0;
            double vz = (z - lastZ) / dt * 20.0;
            lastX = x;
            lastZ = z;
            lastPositionTime = now;
            if (Math.hypot(vx, vz) > 60.0) {
                velocityX = 0;
                velocityZ = 0;
                return;
            }
            double a = 1.0 - Math.exp(-dt / VELOCITY_TICKS);
            velocityX += (vx - velocityX) * a;
            velocityZ += (vz - velocityZ) * a;
        }

        /** No position (on land): the sail does not move. */
        public void still() {
            velocityX = 0;
            velocityZ = 0;
            lastPositionTime = Double.NaN;
        }

        /**
         * Eases the look toward its targets at game time {@code now} [ticks]; the first call jumps straight to them.
         *
         * @param sideTarget the side the cloth should belly to ({@code ClothSide}, +1 or -1)
         * @param fill       how much the sail draws now (eased into {@link #drawing})
         */
        public void approach(float bellyTarget, float flutterTarget, int sideTarget, float fill, double now) {
            float sideGoal = sideTarget >= 0 ? 1f : -1f;
            if (Double.isNaN(time)) {
                belly = bellyTarget;
                flutter = flutterTarget;
                drawing = fill;
                side = sideGoal;
                time = now;
                return;
            }
            double dt = Math.min(MAX_STEP_TICKS, Math.max(0.0, now - time));
            time = now;
            if (dt <= 0.0) {
                return;
            }
            float a = (float) (1.0 - Math.exp(-dt / EASE_TICKS));
            belly += (bellyTarget - belly) * a;
            flutter += (flutterTarget - flutter) * a;
            drawing += (fill - drawing) * a;
            float step = (float) (SIDE_PER_TICK * dt);
            float d = sideGoal - side;
            side = Math.abs(d) <= step ? sideGoal : side + Math.signum(d) * step;
        }
    }
}
