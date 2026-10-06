package com.richardsenger.piratesnships.ship.decor.flag;

import net.minecraft.core.Direction;

/**
 * Which way a flag points (pure): downwind, snapped to the nearest horizontal direction (§4.7 "flags flutter in the
 * wind direction", §5.1). The wind vector is the direction the wind blows <em>toward</em> ({@code WindSample.dirX/Z}).
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
}
