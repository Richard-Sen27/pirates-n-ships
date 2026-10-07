package com.richardsenger.piratesnships.seachest;

/**
 * Motion of the floating sea chest (docs/design.md §11 "Placed in water"). Pure, per game tick, in blocks and ticks.
 *
 * <p><b>Buoyancy</b> (boat-like): gravity pulls down, the water pushes up in proportion to how deep the chest is
 * submerged, so that it rests with {@code draft} of its height under water. Fully submerged it rises (capped at
 * {@link Params#maxRise()}); at the surface it settles with a damped bob.
 *
 * <p><b>Wind drift:</b> while floating at the surface (partly above water) the wind accelerates it toward a drift
 * speed of {@code wind speed × drift_factor} (at most {@code max_drift_speed}); with the water's horizontal drag
 * ({@link Params#waterRetention()} of the velocity kept per tick) that is exactly the steady-state speed. Water
 * currents come on top through vanilla fluid pushing.
 */
public final class FloatRules {

    /** Entity gravity per tick (vanilla's 0.04 for items and boats). */
    public static final double GRAVITY = 0.04;

    /**
     * @param draft          fraction of the height under water at rest, in (0, 1]
     * @param verticalDamping fraction of the vertical velocity kept per tick in water
     * @param maxRise        cap on the upward speed [blocks/tick]
     * @param waterRetention fraction of the horizontal velocity kept per tick in water
     */
    public record Params(double draft, double verticalDamping, double maxRise, double waterRetention) {
        public static Params of(double draft) {
            return new Params(draft, 0.85, 0.15, 0.9);
        }
    }

    private FloatRules() {
    }

    /**
     * The vertical velocity after one tick in water.
     *
     * @param vy        vertical velocity now [blocks/tick]
     * @param submerged how deep the bottom of the chest is under the surface [blocks]; clamped to {@code [0, height]}
     * @param height    the chest's height [blocks]
     */
    public static double verticalVelocity(double vy, double submerged, double height, Params p) {
        double d = Math.max(0.0, Math.min(submerged, height));
        double draftDepth = Math.max(1.0e-3, Math.min(1.0, p.draft()) * height);
        double lift = GRAVITY * d / draftDepth;
        double next = (vy - GRAVITY + lift) * p.verticalDamping();
        return Math.min(next, p.maxRise());
    }

    /**
     * Horizontal acceleration from the wind [blocks/tick²], as {x, z}. Zero when the chest is not floating at the
     * surface (the caller passes {@code atSurface}).
     *
     * @param windX        wind velocity x [blocks/s] (toward which it blows)
     * @param windZ        wind velocity z [blocks/s]
     * @param driftFactor  drift speed as a fraction of the wind speed
     * @param maxDriftSpeed cap on the drift speed [blocks/s]
     */
    public static double[] windAcceleration(double windX, double windZ, double driftFactor, double maxDriftSpeed,
                                            boolean atSurface, Params p) {
        double speed = Math.sqrt(windX * windX + windZ * windZ);
        if (!atSurface || speed < 1.0e-9 || driftFactor <= 0.0 || maxDriftSpeed <= 0.0) {
            return new double[]{0.0, 0.0};
        }
        double drift = Math.min(speed * driftFactor, maxDriftSpeed) / 20.0;
        double r = p.waterRetention();
        double accel = drift * (1.0 - r) / r;
        return new double[]{windX / speed * accel, windZ / speed * accel};
    }

    /** Steady-state drift speed [blocks/tick] that a constant {@link #windAcceleration} leads to. */
    public static double steadyDrift(double accel, Params p) {
        double r = p.waterRetention();
        return accel * r / (1.0 - r);
    }
}
