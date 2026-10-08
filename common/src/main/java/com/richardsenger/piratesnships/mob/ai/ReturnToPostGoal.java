package com.richardsenger.piratesnships.mob.ai;

import java.util.EnumSet;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Keeps a stationary mob at its post (BOS1's pirate captain, the harbor master): when it has no target and stands more
 * than {@code returnDistance} blocks from the post's center, it walks back (looked at every 40 ticks) and, arrived
 * within that distance, turns to the post's facing. The post and facing are read each time, so a mob may move or lose
 * its post; no post ({@code null}) = the goal never runs.
 */
public class ReturnToPostGoal extends Goal {

    /** The walking speed modifier of the way back. */
    public static final double DEFAULT_SPEED = 0.8;

    private final PathfinderMob mob;
    private final Supplier<BlockPos> post;
    private final Supplier<Direction> facing;
    private final double returnDistance;
    private final double speed;
    private int cooldown;

    /**
     * @param mob            the posted mob
     * @param post           its post (block position whose center it returns to), or null for none
     * @param facing         the horizontal direction it faces at its post
     * @param returnDistance blocks from the post's center beyond which an idle mob walks back
     * @param speed          speed modifier for the navigation
     */
    public ReturnToPostGoal(PathfinderMob mob, Supplier<BlockPos> post, Supplier<Direction> facing,
                            double returnDistance, double speed) {
        this.mob = mob;
        this.post = post;
        this.facing = facing;
        this.returnDistance = returnDistance;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /** {@link #ReturnToPostGoal(PathfinderMob, Supplier, Supplier, double, double)} at {@link #DEFAULT_SPEED}. */
    public ReturnToPostGoal(PathfinderMob mob, Supplier<BlockPos> post, Supplier<Direction> facing,
                            double returnDistance) {
        this(mob, post, facing, returnDistance, DEFAULT_SPEED);
    }

    private boolean away(BlockPos p) {
        return p.distToCenterSqr(mob.position()) > returnDistance * returnDistance;
    }

    @Override
    public boolean canUse() {
        BlockPos p = post.get();
        if (mob.getTarget() != null || p == null) return false;
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        cooldown = reducedTickDelay(40);
        return away(p);
    }

    @Override
    public void start() {
        BlockPos p = post.get();
        if (p != null) mob.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, speed);
    }

    @Override
    public boolean canContinueToUse() {
        return mob.getTarget() == null && !mob.getNavigation().isDone();
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        BlockPos p = post.get();
        if (p != null && !away(p)) {
            float yaw = facing.get().toYRot();
            mob.setYRot(yaw);
            mob.setYHeadRot(yaw);
            mob.yBodyRot = yaw;
        }
    }
}
