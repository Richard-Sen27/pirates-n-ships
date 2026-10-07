package com.richardsenger.piratesnships.hazards;

/**
 * When and where hazards form (docs/design.md §12), pure. A spawn check runs every {@code spawn_check_interval_ticks}
 * and rolls once per player and hazard kind: waterspouts only while the world is thundering and the player is at sea
 * (an ocean biome under open sky), whirlpools at any time but only in the deep ocean; never while the player already
 * has {@code max_per_player} of that kind near them.
 */
public final class SpawnRules {

    private SpawnRules() {
    }

    /** Whether a spawn check runs on this game tick. */
    public static boolean isCheckTick(long gameTime, int interval) {
        return interval > 0 && gameTime % interval == 0;
    }

    /** The waterspout roll for one player: {@code roll} uniform in [0, 1). */
    public static boolean rollWaterspout(boolean enabled, boolean thundering, boolean atSea, int aliveNear, int max,
                                         double chance, double roll) {
        return enabled && thundering && atSea && aliveNear < max && roll < chance;
    }

    /** The whirlpool roll for one player: {@code roll} uniform in [0, 1). */
    public static boolean rollWhirlpool(boolean enabled, boolean deepOcean, int aliveNear, int max, double chance, double roll) {
        return enabled && deepOcean && aliveNear < max && roll < chance;
    }

    /**
     * The horizontal offset from the player for a spawn attempt: {@code angleRoll} and {@code distanceRoll} uniform in
     * [0, 1), the distance uniform in the band [min, max] (a reversed band is swapped).
     */
    public static double[] offset(double angleRoll, double distanceRoll, double minDistance, double maxDistance) {
        double lo = Math.min(minDistance, maxDistance);
        double hi = Math.max(minDistance, maxDistance);
        double d = lo + (hi - lo) * distanceRoll;
        double a = angleRoll * Math.PI * 2;
        return new double[] {Math.cos(a) * d, Math.sin(a) * d};
    }

    /** How far around a player hazards count toward the per-player cap: the band's far edge plus the hazard radius. */
    public static double capRange(double maxDistance, double radius) {
        return Math.max(0, maxDistance) + Math.max(0, radius);
    }
}
