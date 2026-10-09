package com.richardsenger.piratesnships.crew.lean;

import com.richardsenger.piratesnships.ship.decor.railing.RailingRules;

/**
 * Pure timing and pose rules of a crew member leaning on the rail (RAIL5, docs/design.md §4.8 "Railing extras"): how
 * long a lean lasts, the cap per ship, where it stands in the railing's block and the slow head turn to the sea, which
 * the code drives because the rig's animations never key the head ({@code art/README.md}, "Entities").
 */
public final class LeanRules {

    /** Half a crew member's width [blocks] ({@code StationContent}'s crew member is 0.6 wide). */
    public static final double HALF_WIDTH = 0.3;
    /**
     * How far outboard of the railing block's centre the leaner stands [blocks]: its front touches the rail's inner face
     * ({@link RailingRules#RAIL_DEPTH} px from the edge) with 0.02 to spare.
     */
    public static final double LEAN_OFFSET = 0.5 - RailingRules.RAIL_DEPTH / 16.0 - HALF_WIDTH - 0.02;
    /** Distance [blocks, on the ship] at which a walker leaves its path and steps straight into the spot. */
    public static final double APPROACH = 1.6;
    /** Distance [blocks] from the spot at which it has arrived and leans. */
    public static final double ARRIVE = 0.15;
    /** A leaner pushed further than this off its spot steps back onto it. */
    public static final double DRIFT = 0.35;
    /**
     * The head's pitch while leaning (Minecraft pitch, positive = down) [degrees]: lifted against the waist's 24° lean
     * of the {@code lean} animation, so the face looks out about 14° down at the water.
     */
    public static final float HEAD_PITCH = -10f;
    /** The head turn's reach either side of the body [degrees]; within the ±45° the body allows (ST5). */
    public static final float HEAD_TURN = 35f;
    /** One head cycle: looking ahead, then a slow turn to one side and back [ticks]. */
    public static final int HEAD_CYCLE = 140;
    /** Ticks of a cycle spent looking straight out to sea before the turn. */
    public static final int HEAD_STILL = 60;

    private LeanRules() {
    }

    /**
     * A lean's length in ticks: {@code minSeconds} to {@code maxSeconds} (either order) picked by {@code random01} in
     * [0, 1), whole seconds.
     */
    public static int durationTicks(int minSeconds, int maxSeconds, double random01) {
        int lo = Math.min(minSeconds, maxSeconds), hi = Math.max(minSeconds, maxSeconds);
        int seconds = lo + (int) Math.floor(Math.max(0, Math.min(0.999999, random01)) * (hi - lo + 1));
        return 20 * Math.min(hi, seconds);
    }

    /** Whether one more crew member of a ship may lean while {@code leaning} already do, at most {@code cap}. */
    public static boolean mayLean(int leaning, int cap) {
        return leaning < cap;
    }

    /**
     * The head's yaw off the body {@code ticks} into the lean [degrees, Minecraft yaw]: {@link #HEAD_STILL} ticks looking
     * out to sea, then a slow turn to one side and back over the rest of the {@link #HEAD_CYCLE}; every other cycle to
     * the other side (positive first).
     */
    public static float headYaw(long ticks) {
        long t = Math.max(0, ticks);
        long phase = t % HEAD_CYCLE;
        if (phase < HEAD_STILL) {
            return 0f;
        }
        double u = (double) (phase - HEAD_STILL) / (HEAD_CYCLE - HEAD_STILL);
        float sign = (t / HEAD_CYCLE) % 2 == 0 ? 1f : -1f;
        return sign * HEAD_TURN * (float) Math.sin(Math.PI * u);
    }
}
