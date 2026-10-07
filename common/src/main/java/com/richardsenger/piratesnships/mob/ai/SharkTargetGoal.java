package com.richardsenger.piratesnships.mob.ai;

import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.entity.Shark;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Picks the nearest visible entity the shark hunts on sight ({@link Shark#huntsOnSight}, i.e. {@code SharkRules}):
 * prey in the water within {@code mobs.shark.detection_range}, skipping anything it lost interest in. Scans every 10
 * ticks while the shark has no target and is in the water.
 */
public class SharkTargetGoal extends TargetGoal {

    private static final int SCAN_INTERVAL = 10;

    private final Shark shark;
    private @Nullable LivingEntity found;
    private int cooldown;

    public SharkTargetGoal(Shark shark) {
        super(shark, true);
        this.shark = shark;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() != null || !mob.isInWater()) return false;
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        cooldown = reducedTickDelay(SCAN_INTERVAL);
        found = scan();
        return found != null;
    }

    private @Nullable LivingEntity scan() {
        double range = MobConfig.SHARK_DETECTION_RANGE.get();
        AABB box = mob.getBoundingBox().inflate(range, range / 2, range);
        LivingEntity best = null;
        double bestSq = range * range;
        for (LivingEntity e : mob.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != mob && e.isAlive())) {
            double d = mob.distanceToSqr(e);
            if (d > bestSq) continue;
            if (!shark.huntsOnSight(e) || !mob.getSensing().hasLineOfSight(e)) continue;
            best = e;
            bestSq = d;
        }
        return best;
    }

    @Override
    public void start() {
        mob.setTarget(found);
        super.start();
    }
}
