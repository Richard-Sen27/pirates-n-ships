package com.richardsenger.piratesnships.mob.squad;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Walking in file (MOB2): each member follows the one in front of him (the first follows the officer). Pure: the
 * pace from the distance to the man in front, and the check that the file is closed up.
 */
public final class FileFollowing {

    /** How a member keeps his place. */
    public enum Pace { STOP, WALK, RUN }

    /** Blocks behind the man in front at which a member stops. */
    public static final double SPACING = 1.5;
    /** A standing member sets off again once the man in front is this far (hysteresis, no stop-and-go). */
    public static final double RESUME = 2.25;
    /** Beyond this a member runs to catch up. */
    public static final double RUN_BEYOND = 6.0;
    /** The file is closed up when every member is at most this far from the man in front. */
    public static final double MAX_GAP = 3.0;

    /** Speed modifiers of the member's navigation. */
    public static final double WALK_SPEED = 0.85;
    public static final double RUN_SPEED = 1.25;

    private FileFollowing() {
    }

    /**
     * The pace of a member {@code distance} blocks behind the man in front.
     *
     * @param moving the member is walking now (a walking member keeps going until {@link #SPACING})
     */
    public static Pace pace(double distance, boolean moving) {
        if (distance > RUN_BEYOND) return Pace.RUN;
        if (distance <= SPACING) return Pace.STOP;
        if (!moving && distance <= RESUME) return Pace.STOP;
        return Pace.WALK;
    }

    /** The largest distance between neighbours of {@code file} (leader first); 0 for fewer than two. */
    public static double maxGap(List<Vec3> file) {
        double max = 0;
        for (int i = 1; i < file.size(); i++) max = Math.max(max, file.get(i).distanceTo(file.get(i - 1)));
        return max;
    }

    /** Every member at most {@link #MAX_GAP} behind the man in front. */
    public static boolean closedUp(List<Vec3> file) {
        return maxGap(file) <= MAX_GAP;
    }
}
