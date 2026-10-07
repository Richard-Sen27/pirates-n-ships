package com.richardsenger.piratesnships.mob.ai;

import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.SharkHunt;
import com.richardsenger.piratesnships.mob.SharkRules;
import com.richardsenger.piratesnships.mob.entity.Shark;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The shark's attack on its target, driven by the pure {@link SharkHunt} rhythm: it circles the prey a few blocks out
 * (always aiming a little ahead of where it is on the circle, so it keeps going round), then charges and bites when in
 * reach; in a blood frenzy it charges at once. The last stretch of a charge is a lunge (a push straight at the prey),
 * because the water path ends a block short. Gives up through {@link Shark#loseInterest}.
 */
public class SharkHuntGoal extends Goal {

    /** Blocks from the prey while circling. */
    public static final double CIRCLE_RADIUS = 4.0;
    /** Radians ahead on the circle the shark steers to. */
    private static final double CIRCLE_LEAD = 0.7;
    private static final double CIRCLE_SPEED = 1.0;
    private static final double CHARGE_SPEED = 1.6;
    /** Within this distance a charge turns into a lunge. */
    private static final double LUNGE_DISTANCE = 3.0;
    private static final double LUNGE_SPEED = 0.35;
    private static final int REPATH_TICKS = 5;

    private final Shark shark;
    private final SharkHunt hunt = new SharkHunt();
    private int repath;

    public SharkHuntGoal(Shark shark) {
        this.shark = shark;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public SharkHunt hunt() {
        return hunt;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = shark.getTarget();
        return target != null && target.isAlive() && shark.isInWater();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        hunt.reset();
        repath = 0;
    }

    @Override
    public void stop() {
        shark.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = shark.getTarget();
        if (target == null) return;
        boolean frenzy = SharkRules.frenzy(target.getHealth(), target.getMaxHealth(), MobConfig.SHARK_FRENZY_FRACTION.get());
        shark.getLookControl().setLookAt(target, 30f, 30f);
        switch (hunt.tick(frenzy, shark.inBiteReach(target), MobConfig.sharkHunt())) {
            case GIVE_UP -> shark.loseInterest(target);
            case CIRCLE -> circle(target);
            case CHARGE -> charge(target);
            case BITE -> {
                shark.bite(target);
                repath = 0;
            }
        }
    }

    private void circle(LivingEntity target) {
        if (--repath > 0 && !shark.getNavigation().isDone()) return;
        repath = REPATH_TICKS;
        double angle = Math.atan2(shark.getZ() - target.getZ(), shark.getX() - target.getX()) + CIRCLE_LEAD;
        double y = target.getY() - 0.5;
        for (double r = CIRCLE_RADIUS; r >= 1.5; r -= 1.25) {
            double x = target.getX() + Math.cos(angle) * r, z = target.getZ() + Math.sin(angle) * r;
            BlockPos at = BlockPos.containing(x, y, z);
            if (shark.level().getFluidState(at).is(FluidTags.WATER)) {
                shark.getNavigation().moveTo(x, at.getY(), z, CIRCLE_SPEED);
                return;
            }
        }
        shark.getNavigation().moveTo(target, CIRCLE_SPEED);
    }

    private void charge(LivingEntity target) {
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.3, 0).subtract(shark.position());
        if (to.length() < LUNGE_DISTANCE && shark.isInWater()) {
            Vec3 push = to.normalize().scale(LUNGE_SPEED);
            shark.setDeltaMovement(shark.getDeltaMovement().scale(0.5).add(push));
            float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
            shark.setYRot(yaw);
            shark.yBodyRot = yaw;
            return;
        }
        if (--repath > 0 && !shark.getNavigation().isDone()) return;
        repath = REPATH_TICKS;
        shark.getNavigation().moveTo(target, CHARGE_SPEED);
    }
}
