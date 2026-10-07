package com.richardsenger.piratesnships.ship.decor.flag;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/**
 * Which way a flag points (pure): exactly downwind, as a compass bearing (§4.7 "flags flutter in the wind
 * direction", §5.1). The wind vector is the direction the wind blows <em>toward</em> ({@code WindSample.dirX/Z}).
 * Bearings use Minecraft's compass, as {@code WindSample#towardDegrees()}: 0 = north (−Z), 90 = east (+X),
 * 180 = south (+Z), 270 = west (−X), always in {@code [0, 360)}.
 * <p>
 * On a ship the cloth is drawn in the ship's plot frame, so the world wind is first turned into that frame with
 * {@link #shipFrame} ({@link #downwindAngle}).
 */
public final class FlagWind {

    /** A horizontal wind vector shorter than this is a dead calm: the flag keeps its angle. */
    public static final double CALM = 1.0e-6;

    private FlagWind() {
    }

    /** The compass bearing of the horizontal vector {@code (x, z)}, or {@code current} when it is (almost) zero. */
    public static float bearing(double x, double z, float current) {
        if (Math.hypot(x, z) < CALM) return current;
        return FlagYaw.wrap((float) Math.toDegrees(Math.atan2(x, -z)));
    }

    /** The unit vector {@code {x, z}} of a compass bearing (inverse of {@link #bearing}). */
    public static double[] toward(double bearingDegrees) {
        double rad = Math.toRadians(bearingDegrees);
        return new double[]{Math.sin(rad), -Math.cos(rad)};
    }

    /**
     * The horizontal world wind {@code (worldX, worldZ)} expressed in a ship's plot frame: rotated by the inverse of
     * {@code orientation} (body/plot to world, as {@code ShipBody#orientation}). Returns {@code {x, z}} of the
     * plot-frame vector; the vertical part a heeled ship gives it is dropped (the flag only turns about the pole).
     */
    public static double[] shipFrame(double worldX, double worldZ, Quaterniondc orientation) {
        Vector3d v = orientation.transformInverse(new Vector3d(worldX, 0, worldZ));
        return new double[]{v.x, v.z};
    }

    /**
     * The bearing the cloth points to in the frame it is drawn in: downwind of the world wind {@code (worldX, worldZ)}
     * (the direction it blows toward, any length), turned into the ship's plot frame when {@code orientation} is given
     * (null on land: the world bearing). No snapping. A calm, or a ship heeled so far that the wind has no horizontal
     * part in its frame, keeps {@code current}.
     */
    public static float downwindAngle(double worldX, double worldZ, @Nullable Quaterniondc orientation, float current) {
        double length = Math.hypot(worldX, worldZ);
        if (length < CALM) return current;
        if (orientation == null) return bearing(worldX, worldZ, current);
        double[] local = shipFrame(worldX, worldZ, orientation);
        // Relative threshold: a strong wind on a ship heeled almost onto its side has no usable direction either.
        if (Math.hypot(local[0], local[1]) < 1.0e-3 * length) return current;
        return bearing(local[0], local[1], current);
    }
}
