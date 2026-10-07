package com.richardsenger.piratesnships.sailing.helm;

/**
 * Pure client-side accumulation of the helmsman's wheel input (HELM1): mouse movement arrives per frame, A/D are read
 * once per tick, and once per tick the sum is drained into one wheel delta for the server. No Minecraft classes, so
 * JUnit can drive it.
 *
 * <p>Mouse input is measured in degrees of view turn the movement would have made (the game's sensitivity applied),
 * so {@code mouseDegreesPerUnit} = 1 turns the wheel as far as the view would have turned.
 */
public final class WheelInput {

    private double mouseDegrees;

    /** One frame's (or tick's) horizontal mouse movement, in degrees of view turn; positive = to the right. */
    public void addMouse(double viewDegrees) {
        if (!Double.isNaN(viewDegrees) && !Double.isInfinite(viewDegrees)) {
            mouseDegrees += viewDegrees;
        }
    }

    /** The mouse movement collected since the last drain, in view degrees (not drained). */
    public double pendingMouse() {
        return mouseDegrees;
    }

    /**
     * Drains this tick's input into one wheel delta in degrees (positive = clockwise = starboard): moving the mouse to
     * the right or holding D turns the wheel clockwise, A counter-clockwise; both keys cancel out. Limited to
     * ±{@code maxPerTick} like the server's budget.
     */
    public double drain(double mouseDegreesPerUnit, boolean left, boolean right, double keyDegreesPerTick, double maxPerTick) {
        double delta = mouseDegrees * mouseDegreesPerUnit;
        mouseDegrees = 0.0;
        if (right && !left) {
            delta += keyDegreesPerTick;
        } else if (left && !right) {
            delta -= keyDegreesPerTick;
        }
        double max = Math.max(0.0, maxPerTick);
        return Math.max(-max, Math.min(max, delta));
    }

    /** Forgets collected input (a new session, or the session ended). */
    public void reset() {
        mouseDegrees = 0.0;
    }

    /** View degrees of a raw mouse movement of {@code dx} screen units at the game's {@code sensitivity} (0..1). */
    public static double viewDegrees(double dx, double sensitivity) {
        // MouseHandler#turnPlayer: d = sens·0.6 + 0.2, turn = dx·d³·8, and Entity#turn scales by 0.15 into degrees
        double d = sensitivity * 0.6 + 0.2;
        return dx * d * d * d * 8.0 * 0.15;
    }
}
