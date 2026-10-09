package com.richardsenger.piratesnships.crew.lean;

import com.richardsenger.piratesnships.crew.npc.CrewMember;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * The idle crew member's lean on the rail (RAIL5, {@link RailLean}) as a goal ahead of its stroll: every second
 * (staggered by entity id) an idle crew member tries to claim a spot; while it walks there and leans the goal holds
 * its movement and look, and it ends with the lean.
 */
public class RailLeanGoal extends Goal {

    /** How often an idle crew member looks for a spot [ticks]. */
    public static final int TRY_INTERVAL = 20;

    private final CrewMember crew;

    public RailLeanGoal(CrewMember crew) {
        this.crew = crew;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(crew.level() instanceof ServerLevel level) || (crew.tickCount + crew.getId()) % TRY_INTERVAL != 0) {
            return false;
        }
        return RailLean.claim(level, crew) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return crew.lean() != null && crew.level() instanceof ServerLevel && RailLeanConfig.ENABLED.get() && RailLean.idle(crew);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (crew.level() instanceof ServerLevel level && !RailLean.tick(level, crew)) {
            RailLean.end(level, crew);
        }
    }

    @Override
    public void stop() {
        if (crew.level() instanceof ServerLevel level) {
            RailLean.end(level, crew);
        }
    }
}
