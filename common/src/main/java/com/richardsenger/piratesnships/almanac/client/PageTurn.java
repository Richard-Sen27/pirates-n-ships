package com.richardsenger.piratesnships.almanac.client;

/**
 * The page-turn animation's state (ALM3), pure. A turn lasts {@code ticks} client ticks: in the first half the turning
 * page's width eases from full to zero toward the spine, in the second half the new page's width eases from zero to
 * full away from it, with a darkening on the fold. Turning {@link Direction#FORWARD} folds the right page over to the
 * left, {@link Direction#BACK} the left page over to the right. The screen ignores input while {@link #turning()}.
 */
public final class PageTurn {

    public enum Direction { FORWARD, BACK }

    /** Darkness (alpha 0..1) of the fold at its strongest. */
    public static final float MAX_SHADE = 0.45f;

    private Direction direction = Direction.FORWARD;
    private int duration;
    private int elapsed;
    private boolean turning;

    /**
     * Starts a turn of {@code ticks} ticks. Returns false and changes nothing when {@code ticks <= 0} (an instant
     * turn) or a turn is already running.
     */
    public boolean start(Direction dir, int ticks) {
        if (turning || ticks <= 0) return false;
        direction = dir;
        duration = ticks;
        elapsed = 0;
        turning = true;
        return true;
    }

    /** One client tick; true on the tick the turn ends. */
    public boolean tick() {
        if (!turning) return false;
        elapsed++;
        if (elapsed < duration) return false;
        turning = false;
        return true;
    }

    /** Ends a running turn at once (the screen closed); true when one was running. */
    public boolean finishNow() {
        boolean was = turning;
        turning = false;
        return was;
    }

    public boolean turning() {
        return turning;
    }

    public Direction direction() {
        return direction;
    }

    /** How far the turn is, 0..1, between ticks. 1 when no turn runs. */
    public float progress(float partialTick) {
        if (!turning) return 1f;
        return clamp01((elapsed + partialTick) / duration);
    }

    /** True in the second half, when the new page opens. */
    public static boolean secondHalf(float progress) {
        return progress >= 0.5f;
    }

    /** Width of the moving page as a fraction of a page: 1 to 0 in the first half, 0 to 1 in the second. */
    public static float pageWidth(float progress) {
        float p = clamp01(progress);
        return p < 0.5f ? 1f - ease(p * 2f) : ease(p * 2f - 1f);
    }

    /** Darkness of the fold: none at the start and end, strongest when the page stands upright. */
    public static float shade(float progress) {
        return MAX_SHADE * (float) Math.sin(Math.PI * clamp01(progress));
    }

    /** Smoothstep. */
    static float ease(float t) {
        float x = clamp01(t);
        return x * x * (3f - 2f * x);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }
}
