package com.richardsenger.piratesnships.sailing.helm;

/**
 * Pure wheel logic of the helm (docs/design.md §5.3, HELM1). The wheel angle is continuous, in degrees, positive =
 * turned clockwise as the helmsman sees it = rudder to starboard (the sign convention of
 * {@link com.richardsenger.piratesnships.sailing.force.RudderModel}). It is limited to ±{@link #lockAngle} (half the
 * turns lock to lock), and the rudder follows it linearly: {@code rudder = wheel / lock · maxRudderAngle}.
 */
public final class WheelMath {

    private WheelMath() {
    }

    /** Wheel angle at either lock: half of {@code turnsLockToLock} full turns, i.e. {@code turns · 180°}. */
    public static double lockAngle(double turnsLockToLock) {
        return Math.max(0.0, turnsLockToLock) * 180.0;
    }

    /** {@code wheel} limited to ±{@code lock}; NaN becomes midships. */
    public static double clampWheel(double wheel, double lock) {
        if (Double.isNaN(wheel)) {
            return 0.0;
        }
        return Math.max(-lock, Math.min(lock, wheel));
    }

    /** Rudder angle in degrees (positive = starboard) of a wheel angle: linear, clamped at the locks. */
    public static double rudderAngle(double wheel, double lock, double maxRudderAngle) {
        if (lock <= 0.0) {
            return 0.0;
        }
        return clampWheel(wheel, lock) / lock * maxRudderAngle;
    }

    /** The wheel angle that gives {@code fraction} (−1..1) of full rudder: used to show click steps on the wheel. */
    public static double wheelForFraction(double fraction, double lock) {
        return clampWheel(fraction * lock, lock);
    }

    /**
     * The per-tick turning budget of one helmsman: the net change of the wheel within one server tick is limited to
     * ±{@code maxPerTick}, however many deltas arrive in that tick.
     *
     * @param usedThisTick net change already applied in this tick
     * @param delta        requested change
     * @return the part of {@code delta} that may be applied
     */
    public static double allowedDelta(double usedThisTick, double delta, double maxPerTick) {
        if (Double.isNaN(delta) || Double.isInfinite(delta) || maxPerTick <= 0.0) {
            return 0.0;
        }
        double net = Math.max(-maxPerTick, Math.min(maxPerTick, usedThisTick + delta));
        double used = Math.max(-maxPerTick, Math.min(maxPerTick, usedThisTick));
        return net - used;
    }

    /** Integrates one delta into the wheel angle: budget first, then the locks. */
    public static Step turn(double wheel, double usedThisTick, double delta, double maxPerTick, double lock) {
        double allowed = allowedDelta(usedThisTick, delta, maxPerTick);
        return new Step(clampWheel(wheel + allowed, lock), usedThisTick + allowed);
    }

    /** Result of {@link #turn}: the new wheel angle and the tick's budget used so far (requested, before the locks). */
    public record Step(double wheel, double usedThisTick) {
    }

    /** Which way the rudder lies, for the helmsman's overlay. */
    public enum Side { MIDSHIPS, PORT, STARBOARD }

    /** The side of a rudder angle; anything that rounds to 0° is midships. */
    public static Side side(double rudderAngle) {
        if (Math.round(Math.abs(rudderAngle)) == 0) {
            return Side.MIDSHIPS;
        }
        return rudderAngle > 0 ? Side.STARBOARD : Side.PORT;
    }
}
