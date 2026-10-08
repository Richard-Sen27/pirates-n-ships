package com.richardsenger.piratesnships.ship.decor.flag;

/**
 * The slow ripple of a flying flag's cloth (ART3, render only; pure, no world access). {@code FlagClothRenderer}
 * draws the {@link FlagClothModel} cloth as {@link #STRIPS} vertical strips from the hoist to the tip; each strip
 * boundary {@code k} sits at {@code z = TIP_Z * k / STRIPS} and is pushed sideways (along the cloth's normal, model
 * x) by a sine that travels from the pole to the tip:
 * <pre>
 *   x(k, t) = A * s * sin(2 pi (WAVES * s - t / PERIOD_TICKS) + phase),   s = k / STRIPS
 * </pre>
 * The hoist ({@code s = 0}) stays on the pole, the swing grows linearly to the tip. {@code A} follows the wind
 * ({@link #amplitude}); the speed is fixed, so a changing wind never makes the wave jump. Strip widths are three
 * texture columns each, so the texture's 1 texel per model pixel along the cloth is kept.
 * <p>
 * The caller owns the arrays ({@link #fill}): the renderer keeps one set and refills it per flag and frame, so no
 * allocation happens while drawing.
 */
public final class FlagRipple {

    /** Vertical strips the cloth is split into (3 px = 3 texture columns each). */
    public static final int STRIPS = 8;
    /** Wavelengths along the cloth from hoist to tip. */
    public static final float WAVES = 1.25f;
    /** Ticks for one crest to pass a point: a slow wave. */
    public static final float PERIOD_TICKS = 36f;
    /** Sideways swing at the tip in calm air and in a strong wind [blocks]. */
    public static final float MIN_AMPLITUDE = 0.6f / 16f;
    public static final float MAX_AMPLITUDE = 1.6f / 16f;
    /** Wind speed [blocks/s] at which the swing reaches {@link #MAX_AMPLITUDE} (the default clear-weather maximum). */
    public static final float FULL_WIND = 12f;

    private FlagRipple() {
    }

    /** Swing at the tip for a wind speed in blocks per second (negative or NaN: calm). */
    public static float amplitude(double windStrength) {
        double f = Double.isNaN(windStrength) ? 0.0 : Math.min(1.0, Math.max(0.0, windStrength / FULL_WIND));
        return (float) (MIN_AMPLITUDE + (MAX_AMPLITUDE - MIN_AMPLITUDE) * f);
    }

    /** A steady phase per flagpole, so neighbouring flags do not wave in step. */
    public static float phase(long blockPosKey) {
        long h = blockPosKey * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        return (float) ((h >>> 40) / (double) (1L << 24) * Math.PI * 2.0);
    }

    /** Distance of strip boundary {@code k} from the pole along the cloth, as a fraction (0 hoist, 1 tip). */
    public static float along(int k) {
        return (float) k / STRIPS;
    }

    /** z of strip boundary {@code k} in the cloth's frame (pointing north, yaw 0). */
    public static float z(int k) {
        return FlagClothModel.HOIST_Z + (FlagClothModel.TIP_Z - FlagClothModel.HOIST_Z) * along(k);
    }

    /** u of strip boundary {@code k} (0..1 over the whole texture): its texture column. */
    public static float u(int k) {
        return FlagClothModel.CLOTH_U1 * along(k);
    }

    /**
     * Fills the sideways offset of every strip boundary ({@code x}, {@code STRIPS + 1} entries) and the x and z of the
     * unit front-face (+x side) normal there ({@code nx}, {@code nz}, same length) for a time in ticks.
     */
    public static void fill(double timeTicks, float phase, float amplitude, float[] x, float[] nx, float[] nz) {
        double w = 2.0 * Math.PI * (timeTicks / PERIOD_TICKS);
        double k2 = 2.0 * Math.PI * WAVES;
        float length = FlagClothModel.HOIST_Z - FlagClothModel.TIP_Z;
        for (int k = 0; k <= STRIPS; k++) {
            double s = along(k);
            double arg = k2 * s - w + phase;
            x[k] = (float) (amplitude * s * Math.sin(arg));
            // dx/ds, then the slope along the cloth's real direction (-z): dx/d(-z) = dx/ds / length
            double dxds = amplitude * (Math.sin(arg) + s * k2 * Math.cos(arg));
            double slope = dxds / length;
            // The cloth runs along (slope, 0, -1); its +x side normal is (1, 0, slope), normalised.
            double inv = 1.0 / Math.sqrt(1.0 + slope * slope);
            nx[k] = (float) inv;
            nz[k] = (float) (slope * inv);
        }
    }
}
