package com.richardsenger.piratesnships.combat.cannon;

import net.minecraft.world.phys.Vec3;

/**
 * Pure rules of the cannon (docs/design.md §8.2): the loading order, the elevation steps, where the muzzle is and which
 * way it points, the velocity of a fired ball and which blocks a ball may destroy. No world access, unit tested.
 *
 * <p>Geometry is in the cannon's own block frame (the plot frame on a ship): the barrel pivots at
 * {@link #PIVOT_HEIGHT} above the block's bottom, centred in x and z, and the muzzle lies {@link #MUZZLE_LENGTH} from
 * the pivot along the barrel, just outside the block.
 */
public final class CannonRules {

    /** Height of the barrel's pivot (trunnions) above the bottom of the block, in blocks (the model's barrel axis). */
    public static final double PIVOT_HEIGHT = 9.5 / 16.0;
    /** Distance from the pivot to the point the ball leaves from, in blocks: half a block plus a margin past the face. */
    public static final double MUZZLE_LENGTH = 1.0;

    /** What the crew puts in, in this order. */
    public enum Charge { POWDER, BALL }

    /** Result of trying to put something into the cannon. */
    public enum LoadOutcome {
        POWDER_IN, BALL_IN, NEEDS_POWDER_FIRST, ALREADY_POWDERED, ALREADY_LOADED, RELOADING;

        public boolean accepted() {
            return this == POWDER_IN || this == BALL_IN;
        }
    }

    private CannonRules() {
    }

    // ---- loading --------------------------------------------------------------------------------------------------

    /** The loading state machine: powder into an empty (and cooled) barrel, then the ball on top of the powder. */
    public static LoadOutcome load(CannonLoad state, Charge charge, boolean reloading) {
        if (state == CannonLoad.LOADED) return LoadOutcome.ALREADY_LOADED;
        return switch (charge) {
            case POWDER -> state == CannonLoad.POWDER ? LoadOutcome.ALREADY_POWDERED
                    : reloading ? LoadOutcome.RELOADING : LoadOutcome.POWDER_IN;
            case BALL -> state == CannonLoad.POWDER ? LoadOutcome.BALL_IN : LoadOutcome.NEEDS_POWDER_FIRST;
        };
    }

    /** The state after an accepted load. */
    public static CannonLoad after(LoadOutcome outcome, CannonLoad state) {
        return switch (outcome) {
            case POWDER_IN -> CannonLoad.POWDER;
            case BALL_IN -> CannonLoad.LOADED;
            default -> state;
        };
    }

    public static boolean canFire(CannonLoad state) {
        return state == CannonLoad.LOADED;
    }

    /** Ticks of the reload cooldown left at {@code now}; 0 when the cannon may take powder again. */
    public static long reloadLeft(long reloadUntil, long now) {
        return Math.max(0, reloadUntil - now);
    }

    // ---- aiming ---------------------------------------------------------------------------------------------------

    /**
     * Elevation of step {@code index} in degrees: {@code steps} angles evenly from {@code min} to {@code max}
     * (6 steps from −5° to 20° are −5, 0, 5, 10, 15, 20). One step means {@code max}. The index is clamped.
     */
    public static double elevationDegrees(int index, int steps, double min, double max) {
        if (steps <= 1 || max <= min) return max;
        int i = Math.max(0, Math.min(steps - 1, index));
        return min + (max - min) * i / (steps - 1);
    }

    /** The step closest to level (0°), where a new cannon starts. */
    public static int levelStep(int steps, double min, double max) {
        int best = 0;
        for (int i = 1; i < steps; i++) {
            if (Math.abs(elevationDegrees(i, steps, min, max)) < Math.abs(elevationDegrees(best, steps, min, max))) best = i;
        }
        return best;
    }

    /** One step up or down, clamped to the ends. */
    public static int stepElevation(int index, int steps, boolean up) {
        int i = Math.max(0, Math.min(Math.max(0, steps - 1), index));
        return Math.max(0, Math.min(Math.max(0, steps - 1), i + (up ? 1 : -1)));
    }

    /**
     * Unit barrel direction in the block frame for a muzzle facing {@code (dx, dz)} (a horizontal {@code Direction}'s
     * step) raised by {@code elevationDegrees}.
     */
    public static Vec3 muzzleDirection(int dx, int dz, double elevationDegrees) {
        double a = Math.toRadians(elevationDegrees);
        double h = Math.cos(a);
        return new Vec3(dx * h, Math.sin(a), dz * h);
    }

    /** The pivot of the barrel of the cannon block at {@code (x, y, z)}, in the block frame. */
    public static Vec3 pivot(int x, int y, int z) {
        return new Vec3(x + 0.5, y + PIVOT_HEIGHT, z + 0.5);
    }

    /** The point the ball leaves from: {@link #MUZZLE_LENGTH} along {@code direction} from the pivot. */
    public static Vec3 muzzle(int x, int y, int z, Vec3 direction) {
        return pivot(x, y, z).add(direction.normalize().scale(MUZZLE_LENGTH));
    }

    // ---- firing ---------------------------------------------------------------------------------------------------

    /**
     * Start velocity of the ball in blocks per tick: the world barrel direction at {@code muzzleVelocity}, plus the
     * velocity of the muzzle itself ({@code carrierMetresPerSecond}: the ship's velocity at that point, m/s = blocks/s),
     * so a broadside from a moving ship flies true.
     */
    public static Vec3 ballVelocity(Vec3 worldDirection, double muzzleVelocity, Vec3 carrierMetresPerSecond) {
        return worldDirection.normalize().scale(muzzleVelocity).add(carrierMetresPerSecond.scale(1.0 / 20.0));
    }

    /** Recoil impulse on the carrying ship, in the block (body) frame: against the barrel. */
    public static Vec3 recoilImpulse(Vec3 localDirection, double strength) {
        return localDirection.normalize().scale(-strength);
    }

    // ---- impact ---------------------------------------------------------------------------------------------------

    /**
     * Whether a ball may destroy a block: block damage must be on, the block must be breakable at all (destroy speed
     * not negative, so no bedrock), not cannon-proof, and either part of a ship or in the breakable tag (wood and wool
     * on land; terrain never).
     */
    public static boolean destroyable(boolean blockDamage, boolean air, float destroySpeed, boolean onShip,
                                      boolean inBreakableTag, boolean inProofTag) {
        if (!blockDamage || air || destroySpeed < 0 || inProofTag) return false;
        return onShip || inBreakableTag;
    }

    /** How many blocks one hit may destroy: the configured count times the damage multiplier, rounded, at least 0. */
    public static int blocksPerHit(int configured, double multiplier) {
        return (int) Math.max(0, Math.round(configured * multiplier));
    }

    /** Damage of a hit on an entity: the configured damage times the multiplier. */
    public static float entityDamage(double configured, double multiplier) {
        return (float) Math.max(0, configured * multiplier);
    }
}
