package com.richardsenger.piratesnships.almanac.client;

/**
 * The geometry of the Almanac's 3D spotlights (ALM10), pure so JUnit can check it: how far the model has turned, how
 * much it leans toward the reader, and the scale that keeps a turning model inside its card. The client side
 * ({@link Spotlight3dRenderer}) measures the model once and draws it with these numbers.
 */
public final class Spotlight3dView {

    /** The turn: 20 degrees a second, one degree a game tick. */
    public static final float DEGREES_PER_SECOND = 20f;
    /** The lean toward the reader, so the top of a block or a mob's head shows. */
    public static final float TILT_DEGREES = 20f;
    /** Room left between the turning model and the card's inner edge, in GUI pixels. */
    public static final int MARGIN = 3;

    private Spotlight3dView() {
    }

    /** The turn in degrees (0 to 360) after {@code ticks} ticks and {@code partialTick} of the next. */
    public static float angle(long ticks, float partialTick) {
        double seconds = (ticks + (double) partialTick) / 20.0;
        return (float) ((seconds * DEGREES_PER_SECOND) % 360.0);
    }

    /**
     * The axis-aligned extent of a model in its own units (blocks), measured from its vertices.
     */
    public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {

        /** No vertex at all. */
        public static final Bounds EMPTY = new Bounds(0f, 0f, 0f, 0f, 0f, 0f);

        public float centerX() {
            return (minX + maxX) / 2f;
        }

        public float centerY() {
            return (minY + maxY) / 2f;
        }

        public float centerZ() {
            return (minZ + maxZ) / 2f;
        }

        public float height() {
            return maxY - minY;
        }

        /** The radius of the circle the model sweeps when it turns about the vertical axis through its centre. */
        public float turnRadius() {
            float hx = (maxX - minX) / 2f, hz = (maxZ - minZ) / 2f;
            return (float) Math.sqrt(hx * hx + hz * hz);
        }

        public boolean isEmpty() {
            return height() <= 0f && turnRadius() <= 0f;
        }
    }

    /**
     * GUI pixels per model unit so the model, turning about its vertical axis and leaning by {@link #TILT_DEGREES},
     * stays inside {@code w x h} GUI pixels whatever the turn: its swept circle across, its height and the circle's
     * lean along. 0 for an empty model or an empty box.
     */
    public static float fitScale(Bounds b, int w, int h) {
        if (b.isEmpty() || w <= 0 || h <= 0) return 0f;
        double t = Math.toRadians(TILT_DEGREES);
        double across = 2.0 * b.turnRadius();
        double along = b.height() * Math.cos(t) + 2.0 * b.turnRadius() * Math.sin(t);
        double s = Double.MAX_VALUE;
        if (across > 0) s = Math.min(s, w / across);
        if (along > 0) s = Math.min(s, h / along);
        return s == Double.MAX_VALUE ? 0f : (float) s;
    }
}
