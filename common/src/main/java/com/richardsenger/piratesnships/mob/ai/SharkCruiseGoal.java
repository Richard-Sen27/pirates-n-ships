package com.richardsenger.piratesnships.mob.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Idle cruising: vanilla's random swimming, but the goal is moved down until it has {@link #CRUISE_DEPTH} blocks of
 * water above it (or the bottom is reached), so a shark cruises a few blocks below the surface instead of breaching.
 * Targets outside the water are dropped (the goal tries again later).
 */
public class SharkCruiseGoal extends RandomSwimmingGoal {

    public static final int CRUISE_DEPTH = 2;

    public SharkCruiseGoal(PathfinderMob mob, double speed, int interval) {
        super(mob, speed, interval);
    }

    @Override
    protected @Nullable Vec3 getPosition() {
        Vec3 v = super.getPosition();
        if (v == null) return null;
        Level level = mob.level();
        BlockPos p = BlockPos.containing(v);
        // Vanilla returns its last try even when none of its 10 tries was water (air above the surface, a wall)
        if (!water(level, p)) return null;
        for (int i = 0; i < CRUISE_DEPTH && !waterAbove(level, p) && water(level, p.below()); i++) p = p.below();
        return new Vec3(v.x, p.getY() + 0.2, v.z);
    }

    private static boolean waterAbove(Level level, BlockPos p) {
        for (int i = 1; i <= CRUISE_DEPTH; i++) if (!water(level, p.above(i))) return false;
        return true;
    }

    private static boolean water(Level level, BlockPos p) {
        return level.getFluidState(p).is(FluidTags.WATER);
    }
}
