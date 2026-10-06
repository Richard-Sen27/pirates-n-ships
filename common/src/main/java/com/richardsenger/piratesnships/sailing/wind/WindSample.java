package com.richardsenger.piratesnships.sailing.wind;

import org.joml.Vector3d;

/**
 * The wind at one place and time (docs/design.md §5.1). World frame, horizontal only.
 *
 * <p>Direction convention: the wind blows <em>toward</em> {@code (dirX, 0, dirZ)}. The compass bearing uses
 * Minecraft's compass: 0° = north (−Z), 90° = east (+X), 180° = south (+Z), 270° = west (−X). A "north wind" (from
 * the north) therefore has {@link #towardDegrees()} = 180.
 *
 * @param dirX              x of the unit direction the wind blows toward
 * @param dirZ              z of the unit direction the wind blows toward
 * @param towardDegrees     compass bearing the wind blows toward, in [0, 360)
 * @param strength          wind speed [blocks/s], including weather, region and gust
 * @param weatherMultiplier the weather factor that went into {@code strength} (1 in clear weather)
 * @param gust              current gust intensity in [0, 1] (0 = no gust); for HUD and visuals
 */
public record WindSample(double dirX, double dirZ, double towardDegrees, double strength, double weatherMultiplier,
                         double gust) {

    /** No wind at all. */
    public static final WindSample CALM = new WindSample(0.0, 1.0, 180.0, 0.0, 1.0, 0.0);

    /** Builds a sample from a compass bearing (degrees the wind blows toward) and a strength. */
    public static WindSample of(double towardDegrees, double strength, double weatherMultiplier, double gust) {
        double deg = normalizeDegrees(towardDegrees);
        double rad = Math.toRadians(deg);
        return new WindSample(Math.sin(rad), -Math.cos(rad), deg, strength, weatherMultiplier, gust);
    }

    /** Compass bearing the wind comes from, in [0, 360). */
    public double fromDegrees() {
        return normalizeDegrees(towardDegrees + 180.0);
    }

    /** Whether a gust is currently blowing. */
    public boolean gusting() {
        return gust > 0.0;
    }

    /** Wind velocity in the world frame [blocks/s], written into {@code dest}. */
    public Vector3d velocity(Vector3d dest) {
        return dest.set(dirX * strength, 0.0, dirZ * strength);
    }

    /** Maps any angle in degrees into [0, 360). */
    public static double normalizeDegrees(double deg) {
        double d = deg % 360.0;
        if (d < 0) {
            d += 360.0;
        }
        return d >= 360.0 ? 0.0 : d;
    }
}
