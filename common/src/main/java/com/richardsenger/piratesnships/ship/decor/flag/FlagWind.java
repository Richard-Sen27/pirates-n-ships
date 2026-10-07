package com.richardsenger.piratesnships.ship.decor.flag;

import net.minecraft.core.Direction;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/**
 * Which way a flag points (pure): downwind, snapped to the nearest horizontal direction (§4.7 "flags flutter in the
 * wind direction", §5.1). The wind vector is the direction the wind blows <em>toward</em> ({@code WindSample.dirX/Z}).
 * On a ship the flag's {@code FACING} is stored in plot coordinates, so the world wind is first turned into the ship's
 * frame with {@link #shipFrame}.
 */
public final class FlagWind {

    private FlagWind() {
    }

    /** The downwind direction, or {@code current} in a dead calm (zero vector). */
    public static Direction downwind(double dirX, double dirZ, Direction current) {
        if (Math.abs(dirX) < 1.0e-6 && Math.abs(dirZ) < 1.0e-6) return current;
        if (Math.abs(dirX) > Math.abs(dirZ)) return dirX > 0 ? Direction.EAST : Direction.WEST;
        return dirZ > 0 ? Direction.SOUTH : Direction.NORTH;
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

    /** {@link #downwind} of the world wind as seen from a ship with this orientation (a plot-frame direction). */
    public static Direction downwindOnShip(double worldX, double worldZ, Quaterniondc orientation, Direction current) {
        double[] local = shipFrame(worldX, worldZ, orientation);
        return downwind(local[0], local[1], current);
    }
}
