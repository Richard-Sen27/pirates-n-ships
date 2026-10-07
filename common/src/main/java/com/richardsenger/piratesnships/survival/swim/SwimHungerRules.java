package com.richardsenger.piratesnships.survival.swim;

/**
 * Swimming hunger (docs/design.md §14), pure. Vanilla charges exhaustion for moving in water in
 * {@code ServerPlayer.checkMovementStatistics}: 0.01 per block (0.0001 per centimetre, the distance rounded to whole
 * centimetres), over the 3D distance while swimming or with the eyes under water, over the horizontal distance while
 * only wading. We charge {@code (multiplier - 1)} times that again, from the player's movement since the last tick.
 */
public final class SwimHungerRules {

    /** Default {@code survival.swim_exhaustion_multiplier}: half again vanilla's swimming hunger. */
    public static final double DEFAULT_MULTIPLIER = 1.5;
    /** Vanilla's exhaustion per centimetre in water ({@code 0.01F * cm * 0.01F}). */
    public static final float VANILLA_EXHAUSTION_PER_CM = 0.01F * 0.01F;
    /** A step longer than this (blocks per tick) is a teleport or a dimension change, not swimming. */
    public static final double MAX_STEP = 8.0;

    /** How vanilla counts a player's movement in water. */
    public enum Medium {
        /** Swimming (sprinting under water): 3D distance. */
        SWIMMING,
        /** Eyes under water: 3D distance. */
        UNDER_WATER,
        /** In water with the eyes above it (wading, treading water): horizontal distance. */
        ON_WATER,
        /** Not in water: nothing. */
        NONE;

        boolean countsHeight() {
            return this == SWIMMING || this == UNDER_WATER;
        }
    }

    private SwimHungerRules() {
    }

    /** Vanilla's medium choice, in its order: swimming, then eyes in water, then in water. */
    public static Medium medium(boolean swimming, boolean eyesInWater, boolean inWater) {
        if (swimming) return Medium.SWIMMING;
        if (eyesInWater) return Medium.UNDER_WATER;
        if (inWater) return Medium.ON_WATER;
        return Medium.NONE;
    }

    /** Whole centimetres vanilla counts for one step ({@code Math.round((float) sqrt(...) * 100F)}); 0 for teleports. */
    public static int centimetres(Medium medium, double dx, double dy, double dz) {
        if (medium == Medium.NONE) return 0;
        double sq = dx * dx + dz * dz + (medium.countsHeight() ? dy * dy : 0.0);
        if (sq > MAX_STEP * MAX_STEP) return 0;
        return Math.round((float) Math.sqrt(sq) * 100.0F);
    }

    /** Vanilla's exhaustion for one step. */
    public static float vanillaExhaustion(Medium medium, double dx, double dy, double dz) {
        return 0.01F * centimetres(medium, dx, dy, dz) * 0.01F;
    }

    /**
     * The exhaustion we add on top of vanilla's for one step: vanilla's × ({@code multiplier} − 1). Zero when disabled,
     * when the multiplier is at most 1, or when the player is exempt (riding, on a ship).
     */
    public static float extraExhaustion(boolean enabled, double multiplier, boolean exempt, Medium medium,
                                        double dx, double dy, double dz) {
        if (!enabled || exempt || multiplier <= 1.0) return 0.0F;
        return (float) (vanillaExhaustion(medium, dx, dy, dz) * (multiplier - 1.0));
    }
}
