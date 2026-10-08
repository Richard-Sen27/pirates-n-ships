package com.richardsenger.piratesnships.world.treasure;

import java.util.Locale;

/**
 * Where a treasure lies from the reader of its map (TM1), pure: one of eight compass points and the horizontal
 * distance in whole blocks ("NW, 340 blocks"). North is -z and east is +x, as on the chart. Within {@link #HERE} blocks
 * the map just says the treasure is here.
 *
 * @param point    the compass point toward the site ({@code null} when {@link #here})
 * @param distance horizontal distance in blocks, rounded
 */
public record TreasureBearing(Point point, int distance) {

    /** Closer than this (blocks) the map says "dig here". */
    public static final int HERE = 3;

    /** The eight compass points, clockwise from north. */
    public enum Point {
        N, NE, E, SE, S, SW, W, NW;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The bearing from {@code (fromX, fromZ)} to {@code (toX, toZ)} (block or entity coordinates). */
    public static TreasureBearing of(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        if (distance < HERE) return new TreasureBearing(null, distance);
        // 0 degrees = north (-z), 90 = east (+x)
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        int index = Math.floorMod((int) Math.round(degrees / 45.0), 8);
        return new TreasureBearing(Point.values()[index], distance);
    }

    public boolean here() {
        return point == null;
    }

    /** The plain English text, as the lang file reads it (tests and logs). */
    public String english() {
        return here() ? "X marks the spot: dig here!" : point.name() + ", " + distance + " blocks";
    }
}
