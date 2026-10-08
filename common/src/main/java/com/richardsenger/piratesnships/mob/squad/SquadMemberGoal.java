package com.richardsenger.piratesnships.mob.squad;

import com.richardsenger.piratesnships.mob.entity.NavySoldier;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * A soldier's walking as squad member (MOB2): on patrol (and after a fight, to re-form) he follows the man in front of
 * him in file ({@link FileFollowing}: {@value FileFollowing#SPACING} blocks behind, running when more than
 * {@value FileFollowing#RUN_BEYOND} behind); when the squad returns, and whenever he finds himself away from his post
 * with nothing to do, he walks back to his post ({@link HomeWalk}). Fighting goes first.
 */
public class SquadMemberGoal extends Goal {

    static final double HOME_SPEED = 0.9;

    private final NavySoldier soldier;
    private final HomeWalk home;
    private int repath;
    private FileFollowing.Pace pace = FileFollowing.Pace.STOP;

    public SquadMemberGoal(NavySoldier soldier) {
        this.soldier = soldier;
        this.home = new HomeWalk(soldier, HOME_SPEED);
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private static boolean following(Squad squad) {
        return squad != null && (squad.state() == SquadState.PATROLLING || squad.state() == SquadState.FIGHTING);
    }

    @Override
    public boolean canUse() {
        if (soldier.getTarget() != null) return false;
        Squad squad = SquadService.memberSquad(soldier);
        if (following(squad)) return true;
        if (squad != null && squad.state() == SquadState.RETURNING) return !home.settled();
        return home.strayed();
    }

    @Override
    public boolean canContinueToUse() {
        if (soldier.getTarget() != null) return false;
        Squad squad = SquadService.memberSquad(soldier);
        return following(squad) || !home.settled();
    }

    @Override
    public void start() {
        repath = 0;
        pace = FileFollowing.Pace.STOP;
    }

    @Override
    public void stop() {
        soldier.getNavigation().stop();
        home.reset();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Squad squad = SquadService.memberSquad(soldier);
        if (!following(squad) || !(soldier.level() instanceof ServerLevel level)) {
            home.tick();
            return;
        }
        SeafarerMob ahead = squad.predecessor(level, soldier);
        double distance = soldier.distanceTo(ahead);
        FileFollowing.Pace next = FileFollowing.pace(distance, pace != FileFollowing.Pace.STOP);
        soldier.getLookControl().setLookAt(ahead, 30f, 30f);
        if (next == FileFollowing.Pace.STOP) {
            if (pace != FileFollowing.Pace.STOP) soldier.getNavigation().stop();
            pace = next;
            return;
        }
        if (next != pace || --repath <= 0 || soldier.getNavigation().isDone()) {
            double speed = next == FileFollowing.Pace.RUN ? FileFollowing.RUN_SPEED : FileFollowing.WALK_SPEED;
            SquadReach.wide(soldier, () -> soldier.getNavigation().moveTo(ahead, speed));
            repath = 10;
        }
        pace = next;
    }
}
