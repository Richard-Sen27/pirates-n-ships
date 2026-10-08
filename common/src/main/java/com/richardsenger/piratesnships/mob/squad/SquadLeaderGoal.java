package com.richardsenger.piratesnships.mob.squad;

import com.richardsenger.piratesnships.mob.entity.NavyOfficer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * The officer's walking as squad leader (MOB2): on patrol he walks to the current waypoint and stands while the file
 * closes up and during the pause there ({@link Squad} decides when to go on); returning, and whenever he finds himself
 * away from his garrison post with nothing to do (after a fight), he walks back to his post and stands there facing
 * its way. Fighting goes first (the combat goals have the higher priority).
 */
public class SquadLeaderGoal extends Goal {

    static final double PATROL_SPEED = 0.7;
    static final double HOME_SPEED = 0.8;

    private final NavyOfficer officer;
    private final HomeWalk home;
    private int repath;

    public SquadLeaderGoal(NavyOfficer officer) {
        this.officer = officer;
        this.home = new HomeWalk(officer, HOME_SPEED);
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private boolean patrolling(Squad squad) {
        return squad != null && squad.state() == SquadState.PATROLLING;
    }

    private boolean goingHome(Squad squad) {
        if (squad != null && squad.state() == SquadState.RETURNING) return !home.settled();
        return (squad == null || squad.state() == SquadState.AT_POST) && home.strayed();
    }

    @Override
    public boolean canUse() {
        if (officer.getTarget() != null) return false;
        Squad squad = SquadService.of(officer);
        return patrolling(squad) || goingHome(squad);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void stop() {
        officer.getNavigation().stop();
        home.reset();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Squad squad = SquadService.of(officer);
        if (!patrolling(squad)) {
            home.tick();
            return;
        }
        BlockPos wp = squad.currentWaypoint();
        if (squad.phase() != Squad.Phase.WALK || wp == null) {
            officer.getNavigation().stop();
            return;
        }
        if (--repath <= 0 || officer.getNavigation().isDone()) {
            SquadReach.wide(officer, () -> officer.getNavigation().moveTo(wp.getX() + 0.5, wp.getY(), wp.getZ() + 0.5, 0, PATROL_SPEED)); // accuracy 0: onto the waypoint
            repath = 20;
        }
    }
}
