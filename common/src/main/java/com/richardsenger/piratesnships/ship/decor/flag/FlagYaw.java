package com.richardsenger.piratesnships.ship.decor.flag;

/**
 * Angle helpers for the flag's continuous yaw (pure): wrapping into {@code [0, 360)}, the shortest signed turn
 * between two bearings, and the smooth approach the client renderer uses so gusts and a turning ship never make the
 * cloth snap (it always takes the short way round, also across 0/360).
 */
public final class FlagYaw {

    /** Time constant of the approach in ticks: after this long about 63 % of a change is shown, after 3× about 95 %. */
    public static final float SMOOTHING_TICKS = 4f;

    private FlagYaw() {
    }

    /** Any angle in degrees mapped into {@code [0, 360)}. */
    public static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d < 0f) d += 360f;
        return d >= 360f ? 0f : d;
    }

    /** The shortest signed turn from {@code from} to {@code to}, in {@code (-180, 180]} degrees. */
    public static float delta(float from, float to) {
        float d = wrap(to - from);
        return d > 180f ? d - 360f : d;
    }

    /**
     * Moves {@code current} toward {@code target} over {@code elapsedTicks} with an exponential approach of time
     * constant {@code smoothingTicks}, the short way round; the result is wrapped. A NaN {@code current} (nothing
     * shown yet) jumps to the target, and so does a non-positive time constant or an elapsed time of more than 20
     * time constants (e.g. the flag was off screen for a while).
     */
    public static float approach(float current, float target, double elapsedTicks, float smoothingTicks) {
        if (Float.isNaN(current) || smoothingTicks <= 0f) return wrap(target);
        double dt = Math.max(0.0, elapsedTicks);
        if (dt > 20.0 * smoothingTicks) return wrap(target);
        float k = (float) (1.0 - Math.exp(-dt / smoothingTicks));
        return wrap(current + delta(current, target) * k);
    }
}
