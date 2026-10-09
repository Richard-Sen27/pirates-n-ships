package com.richardsenger.piratesnships.combat.cannon;

/**
 * The drawn barrel angle of one cannon eased between elevation steps (CAN2): when the target angle changes, the drawing
 * runs from the angle shown at that moment to the new one over {@code tiltTicks} with a smoothstep, so a step taken in
 * the middle of a tilt carries on from where the barrel is. The first target is shown at once (a cannon coming into
 * view does not swing up). Pure state on game ticks (with the partial tick), no world access; one per client block
 * entity ({@link CannonBlockEntity#tilt()}), unit tested.
 */
public final class CannonBarrelTilt {

    private boolean started;
    private double from;
    private double to;
    private double startTicks;

    /** The angle to draw at {@code nowTicks} for the current {@code target}; {@code tiltTicks} ≤ 0 jumps at once. */
    public double angle(double target, double nowTicks, double tiltTicks) {
        if (!started) {
            started = true;
            from = to = target;
            startTicks = nowTicks;
        } else if (target != to) {
            from = at(nowTicks, tiltTicks);
            to = target;
            startTicks = nowTicks;
        }
        return at(nowTicks, tiltTicks);
    }

    private double at(double nowTicks, double tiltTicks) {
        if (tiltTicks <= 0) return to;
        double t = Math.max(0.0, Math.min(1.0, (nowTicks - startTicks) / tiltTicks));
        return from + (to - from) * smoothstep(t);
    }

    static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }
}
