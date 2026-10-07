package com.richardsenger.piratesnships.mob.ai;

import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Picks the nearest visible entity the mob attacks on sight ({@link SeafarerMob#attacksOnSight}, i.e.
 * {@code HostilityRules}): players within {@code mobs.detection_range}, the other faction within
 * {@code mobs.faction_fight_range}. Scans every 10 ticks while the mob has no target. Unlike vanilla's
 * {@code NearestAttackableTargetGoal} it looks at every living entity in range (players included) through one rule set.
 */
public class HostileTargetGoal extends TargetGoal {

    private static final int SCAN_INTERVAL = 10;

    private final SeafarerMob seafarer;
    private @Nullable LivingEntity found;
    private int cooldown;

    public HostileTargetGoal(SeafarerMob mob) {
        super(mob, true);
        this.seafarer = mob;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() != null) return false;
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        cooldown = reducedTickDelay(SCAN_INTERVAL);
        found = scan();
        return found != null;
    }

    private @Nullable LivingEntity scan() {
        double players = MobConfig.DETECTION_RANGE.get();
        double factions = MobConfig.FACTION_FIGHT_RANGE.get();
        double range = Math.max(players, factions);
        AABB box = mob.getBoundingBox().inflate(range, range / 2, range);
        LivingEntity best = null;
        double bestSq = Double.MAX_VALUE;
        for (LivingEntity e : mob.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != mob && e.isAlive())) {
            double max = e instanceof Player ? players : factions;
            double d = mob.distanceToSqr(e);
            if (d > max * max || d >= bestSq) continue;
            // canAttack: the check canContinueToUse makes (no players on peaceful); without it the target would be
            // picked here and dropped again on the next tick, every scan
            if (!mob.canAttack(e) || !mob.getSensing().hasLineOfSight(e) || !seafarer.attacksOnSight(e)) continue;
            best = e;
            bestSq = d;
        }
        return best;
    }

    @Override
    public void start() {
        mob.setTarget(found);
        super.start();
        DuelistDebug.stats(mob).targetsAcquired++;
        DuelistDebug.report(mob, "target", "acquired", found, "");
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        boolean keep = super.canContinueToUse();
        if (!keep && DuelistDebug.active()) DuelistDebug.report(mob, "target", "dropped (" + dropReason(target) + ")", target, "");
        return keep;
    }

    @Override
    public void stop() {
        if (mob.getTarget() != null) DuelistDebug.stats(mob).targetsLost++;
        super.stop();
    }

    private String dropReason(@Nullable LivingEntity target) {
        if (target == null) return "no target";
        if (!mob.canAttack(target)) return "can't attack: peaceful difficulty or invulnerable";
        double range = getFollowDistance();
        if (mob.distanceToSqr(target) > range * range) return "beyond mobs.detection_range";
        return "out of sight";
    }

    @Override
    protected double getFollowDistance() {
        return MobConfig.DETECTION_RANGE.get();
    }
}
