package com.richardsenger.piratesnships.mob.ai;

import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.MusketRules;
import com.richardsenger.piratesnships.mob.entity.NavySoldier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A navy soldier's musket fight ({@link MusketRules}): keep distance, aim, fire through {@code FirearmService}, reload
 * ({@code mobs.musket_reload_ticks}, counted by the soldier), and shove a target that closes in with the musket butt
 * (damage and knockback from config). Simple on purpose: no cover, no volleys.
 */
public class MusketeerGoal extends Goal {

    private static final int REPATH_TICKS = 5;
    private static final double BACK_OFF_DISTANCE = 4.0;

    private final NavySoldier mob;
    private final double speed;
    private int aimed;
    private int shoveCooldown;
    private int repath;

    public MusketeerGoal(NavySoldier mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        // keepsTarget: the goal selector ticks before customServerAiStep re-checks a target set from outside
        return t != null && t.isAlive() && mob.keepsTarget(t);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        aimed = 0;
        repath = 0;
    }

    @Override
    public void stop() {
        aimed = 0;
        mob.setMusketAiming(false);
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        if (shoveCooldown > 0) shoveCooldown--;
        mob.getLookControl().setLookAt(target, 30f, 30f);
        double distance = mob.distanceTo(target);
        boolean sight = mob.getSensing().hasLineOfSight(target);
        MusketRules.Decision d = MusketRules.decide(distance, sight, mob.musketReady(), aimed, shoveCooldown == 0, MobConfig.musket());

        switch (d.move()) {
            case HOLD -> mob.getNavigation().stop();
            case APPROACH -> {
                if (--repath <= 0) {
                    repath = REPATH_TICKS;
                    mob.getNavigation().moveTo(target, speed);
                }
            }
            case BACK_OFF -> {
                if (--repath <= 0) {
                    repath = REPATH_TICKS;
                    Vec3 away = mob.position().subtract(target.position()).multiply(1, 0, 1);
                    away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
                    Vec3 to = mob.position().add(away.scale(BACK_OFF_DISTANCE));
                    mob.getNavigation().moveTo(to.x, to.y, to.z, speed);
                }
            }
        }

        if (d.aim()) {
            aimed++;
            MobAim.face(mob, target);
        } else {
            aimed = 0;
        }
        mob.setMusketAiming(d.aim());

        if (d.fire()) {
            MobAim.face(mob, target);
            mob.fireMusket();
            aimed = 0;
        }
        if (d.shove()) {
            MobAim.face(mob, target);
            mob.swing(InteractionHand.MAIN_HAND);
            mob.musketShoved();
            target.hurt(mob.damageSources().mobAttack(mob), MobConfig.SHOVE_DAMAGE.get().floatValue());
            target.knockback(MobConfig.SHOVE_KNOCKBACK.get(), mob.getX() - target.getX(), mob.getZ() - target.getZ());
            shoveCooldown = MobConfig.SHOVE_COOLDOWN.get();
        }
    }
}
