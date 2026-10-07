package com.richardsenger.piratesnships.mob.ai;

/**
 * Pure rules of a sword fighter closing in on a (moving) target (M5). Distances are measured the way the melee hit
 * geometry measures reach: horizontally, from the attacker's centre (its eye) to the nearest point of the target's
 * bounding-box footprint (the "gap").
 *
 * <ul>
 *   <li><b>Attack timing:</b> an attack is started when the gap predicted for its first hit frame (now, minus the
 *       closing speed times the wind-up) is inside the reach with a margin, so neither a target stepping back nor a
 *       mob still walking in makes the swing land at the edge or short of it. The current gap must be within reach
 *       plus the possible lead too, so a fast approach never swings from far off.</li>
 *   <li><b>Approach:</b> the mob walks until the gap is at most {@code reach - approachMargin} and only starts walking
 *       again once the target is {@link #RESUME_HYSTERESIS} further away (no stop-start stutter at the threshold).
 *       It keeps walking during its own wind-up.</li>
 *   <li><b>Path cadence</b> (like vanilla's {@code MeleeAttackGoal}): a new path at most every
 *       {@link #MIN_REPATH_TICKS}..{@code MIN + REPATH_JITTER} ticks, and only when the target moved a block since the
 *       last path, the path ran out, or a 5% chance says so; after a failed path search a {@link #FAILED_PATH_PENALTY}
 *       (the goal doesn't search while the mob is airborne: ground navigation finds nothing then and drops the current
 *       path, so a mob knocked back by a hit would lose its path and then wait out the penalty).</li>
 * </ul>
 */
public final class ChaseRules {

    public static final int MIN_REPATH_TICKS = 4;
    public static final int REPATH_JITTER = 7;
    public static final int FAILED_PATH_PENALTY = 15;
    public static final double REPATH_TARGET_MOVED = 1.0;
    public static final double RANDOM_REPATH_CHANCE = 0.05;
    public static final double RESUME_HYSTERESIS = 0.4;
    /** Weight of the newest sample in the smoothed closing speed. */
    public static final double CLOSING_SMOOTHING = 0.5;
    /** Closing speeds beyond this (blocks per tick, a fast sprint) are treated as noise (a teleport, a knockback). */
    public static final double MAX_CLOSING_SPEED = 0.6;

    private ChaseRules() {
    }

    /** Horizontal distance from the point {@code (x, z)} to the footprint {@code [minX, maxX] x [minZ, maxZ]} (0 inside). */
    public static double gap(double x, double z, double minX, double minZ, double maxX, double maxZ) {
        double dx = Math.max(0, Math.max(minX - x, x - maxX));
        double dz = Math.max(0, Math.max(minZ - z, z - maxZ));
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Smoothed closing speed (blocks per tick, positive = the gap shrinks) after a tick in which the gap went from {@code before} to {@code now}. */
    public static double closing(double smoothed, double before, double now) {
        double sample = before - now;
        if (Math.abs(sample) > MAX_CLOSING_SPEED) return smoothed;
        return smoothed + CLOSING_SMOOTHING * (sample - smoothed);
    }

    /** The gap expected {@code leadTicks} from now at the current closing speed (never below 0). */
    public static double predictedGap(double gap, double closing, int leadTicks) {
        return Math.max(0, gap - closing * leadTicks);
    }

    /**
     * Whether an attack started now reaches: the predicted gap at its first hit frame is at most {@code reach - margin},
     * and the gap now is at most {@code reach - margin} plus what the mob itself can close during the wind-up.
     *
     * @param maxSelfClosing the farthest the mob can walk in one tick (bounds the lead taken from a fast approach)
     */
    public static boolean attackReaches(double gap, double closing, int windupTicks, double reach, double margin,
                                        double maxSelfClosing) {
        double limit = reach - margin;
        if (predictedGap(gap, closing, windupTicks) > limit) return false;
        return gap <= limit + Math.max(0, maxSelfClosing) * windupTicks;
    }

    /**
     * Whether the mob should be walking towards its target.
     *
     * @param walking whether it walked last tick (hysteresis: it stops at {@code stopGap}, resumes beyond
     *                {@code stopGap + RESUME_HYSTERESIS})
     */
    public static boolean approach(double gap, double stopGap, boolean walking) {
        return walking ? gap > stopGap : gap > stopGap + RESUME_HYSTERESIS;
    }

    /** The stop gap for a weapon reach: {@code reach - approachMargin}, at least a quarter block. */
    public static double stopGap(double reach, double approachMargin) {
        return Math.max(0.25, reach - approachMargin);
    }

    /**
     * Whether to search a new path now.
     *
     * @param ticksLeft        ticks until a recalculation may happen (counted down by the caller)
     * @param pathDone         the navigation has no path or finished it
     * @param targetMovedSq    squared distance the target moved since the last path was made
     * @param roll             0..1 random
     */
    public static boolean repath(int ticksLeft, boolean pathDone, double targetMovedSq, double roll) {
        if (ticksLeft > 0) return false;
        return pathDone || targetMovedSq >= REPATH_TARGET_MOVED * REPATH_TARGET_MOVED || roll < RANDOM_REPATH_CHANCE;
    }

    /**
     * Ticks until the next recalculation may happen, after one was made ({@code jitterRoll} 0..1).
     *
     * @param pathFound the search found a path (else the {@link #FAILED_PATH_PENALTY} is added)
     */
    public static int nextRepathDelay(double jitterRoll, boolean pathFound) {
        int ticks = MIN_REPATH_TICKS + (int) Math.floor(Math.max(0, Math.min(0.999999, jitterRoll)) * REPATH_JITTER);
        return pathFound ? ticks : ticks + FAILED_PATH_PENALTY;
    }
}
