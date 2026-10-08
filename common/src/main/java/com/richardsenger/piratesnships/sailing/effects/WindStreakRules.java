package com.richardsenger.piratesnships.sailing.effects;

/**
 * Pure rules of the wind streaks (WD1, docs/design.md §5.1 and §5.4 "Seeing wind and waves"): white streaks that drift
 * with the synced wind through the air over the sea, so the player sees the true wind. Client-only visuals, no gameplay
 * effect. The client spawner ({@code sailing.effects.client.SeaEffectsClient}) feeds in the synced wind sample and the
 * client config ({@code wind_effects.*}).
 *
 * @param density     streaks per tick per block/s of wind speed ({@code wind_effects.density})
 * @param minStrength no streaks below this wind speed [blocks/s] ({@code wind_effects.min_strength})
 * @param gustBoost   extra share of streaks at the peak of a gust ({@code wind_effects.gust_boost}; 1 = twice as many)
 * @param radius      horizontal distance from the camera [blocks] ({@code wind_effects.radius})
 * @param height      top of the band above the sea surface [blocks] ({@code wind_effects.height})
 * @param lifeTicks   life of one streak [ticks] ({@code wind_effects.life_ticks})
 */
public record WindStreakRules(double density, double minStrength, double gustBoost, double radius, double height,
                              int lifeTicks) {

    /** Streaks start at least this far from the camera, so none is born in the player's face [blocks]. */
    public static final double INNER_RADIUS = 3.0;
    /** The lowest streaks fly this far above the sea surface [blocks]. */
    public static final double BOTTOM = 1.0;
    /** Shortest and longest streak [blocks]. */
    public static final double MIN_LENGTH = 1.2, MAX_LENGTH = 4.5;
    /** Seconds of travel a streak's length shows (a motion-blur trail). */
    public static final double TRAIL_SECONDS = 0.18;

    public static final WindStreakRules DEFAULTS = new WindStreakRules(0.12, 4.0, 1.0, 24.0, 12.0, 40);

    public WindStreakRules {
        density = Math.max(0.0, density);
        minStrength = Math.max(0.0, minStrength);
        gustBoost = Math.max(0.0, gustBoost);
        radius = Math.max(INNER_RADIUS, radius);
        height = Math.max(BOTTOM, height);
        lifeTicks = Math.max(1, lifeTicks);
    }

    /**
     * Mean streaks per tick for a wind of {@code strength} blocks/s with gust intensity {@code gust} in [0, 1]:
     * {@code density × strength × (1 + gustBoost × gust)}, nothing below {@link #minStrength} (calm air).
     */
    public double rate(double strength, double gust) {
        if (!(strength > 0.0) || strength < minStrength) {
            return 0.0;
        }
        double g = Math.max(0.0, Math.min(1.0, gust));
        return density * strength * (1.0 + gustBoost * g);
    }

    /** Distance a streak drifts in its life [blocks] at {@code strength} blocks/s. */
    public double travel(double strength) {
        return Math.max(0.0, strength) * lifeTicks / 20.0;
    }

    /**
     * Where a streak starts: a point evenly spread over the ring around the camera, moved upwind by half the distance it
     * will drift, so the streaks are spread evenly around the camera halfway through their life instead of piling up
     * downwind.
     *
     * @param dirX     x of the unit direction the wind blows toward
     * @param dirZ     z of that direction
     * @param strength wind speed [blocks/s]
     * @param u1       uniform in [0, 1): angle in the ring
     * @param u2       uniform in [0, 1): radius in the ring
     * @return {@code {x, z}}
     */
    public double[] start(double camX, double camZ, double dirX, double dirZ, double strength, double u1, double u2) {
        double[] p = SpawnRules.ringPoint(camX, camZ, INNER_RADIUS, radius, u1, u2);
        double back = travel(strength) * 0.5;
        p[0] -= dirX * back;
        p[1] -= dirZ * back;
        return p;
    }

    /**
     * Height of a streak: evenly between {@link #BOTTOM} and {@link #height} above the sea surface {@code seaY} (deck
     * height up into the rigging).
     *
     * @param u uniform in [0, 1)
     */
    public double y(double seaY, double u) {
        return seaY + BOTTOM + u * (height - BOTTOM);
    }

    /** Velocity along the wind [blocks/tick]: the streak flies at the wind's own speed. */
    public static double speedPerTick(double strength) {
        return Math.max(0.0, strength) / 20.0;
    }

    /** Length of a streak [blocks]: the trail of {@link #TRAIL_SECONDS} of travel, clamped. */
    public static double length(double strength) {
        return Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, Math.max(0.0, strength) * TRAIL_SECONDS));
    }

    /**
     * Whether a camera at {@code camY} is close enough to the band over the sea {@code seaY} to see streaks: not deeper
     * than a few blocks under the surface and not higher than the band's top plus the radius.
     */
    public boolean inRange(double camY, double seaY) {
        return camY > seaY - 4.0 && camY < seaY + height + radius;
    }

    /**
     * The unit vector across a streak that runs along the horizontal unit axis {@code (axisX, 0, axisZ)} and is seen
     * from the camera at {@code (px, py, pz)} relative to the streak: {@code axis × p}, normalized, so the quad turns
     * about its axis to face the camera. Straight up when the camera looks along the axis.
     *
     * @return {@code {x, y, z}}
     */
    public static double[] facingSide(double axisX, double axisZ, double px, double py, double pz) {
        double cx = -axisZ * py, cy = axisZ * px - axisX * pz, cz = axisX * py;
        double n = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (n < 1.0e-6) {
            return new double[] {0.0, 1.0, 0.0};
        }
        return new double[] {cx / n, cy / n, cz / n};
    }
}
