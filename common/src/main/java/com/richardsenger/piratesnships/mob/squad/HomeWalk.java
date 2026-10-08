package com.richardsenger.piratesnships.mob.squad;

import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import net.minecraft.core.BlockPos;

/**
 * A garrison mob walking back to its post (MOB2): when its squad returns, or when it finds itself away from its post
 * with nothing to do for {@link #STRAY_TICKS} (after a fight, a squad that broke up, an officer who died). At the post
 * it settles ({@link SquadService#settle}): stationary again, facing the post's way.
 */
final class HomeWalk {

    /** Idle ticks away from the post before walking back on its own. */
    static final int STRAY_TICKS = 100;

    private final SeafarerMob mob;
    private final double speed;
    private int strayCount;
    private int repath;

    HomeWalk(SeafarerMob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
    }

    /** At its post and settled (or without a post). */
    boolean settled() {
        return SquadService.post(mob) == null || SquadService.atPost(mob) && mob.isStationary();
    }

    /**
     * Counts idle checks away from the post; true once it has been away for {@link #STRAY_TICKS}. Called from the
     * goal's {@code canUse} (every other tick); a mob at its post resets the count.
     */
    boolean strayed() {
        if (SquadService.post(mob) == null) return false;
        if (SquadService.atPost(mob)) {
            strayCount = 0;
            return !mob.isStationary(); // at the post but not settled yet: settle
        }
        strayCount += 2;
        return strayCount >= STRAY_TICKS;
    }

    void reset() {
        strayCount = 0;
        repath = 0;
    }

    void tick() {
        GarrisonPost post = SquadService.post(mob);
        if (post == null) return;
        if (SquadService.atPost(mob)) {
            if (!mob.isStationary() || !mob.getNavigation().isDone()) SquadService.settle(mob);
            return;
        }
        if (--repath <= 0 || mob.getNavigation().isDone()) {
            BlockPos p = post.pos();
            mob.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, speed); // accuracy 0: onto the post itself
            repath = 20;
        }
    }
}
