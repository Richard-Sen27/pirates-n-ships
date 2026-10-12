package com.richardsenger.piratesnships.mob.monsters;

import com.richardsenger.piratesnships.combat.melee.MeleeService;
import com.richardsenger.piratesnships.crew.hammock.CrewRest;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.law.brig.BrigService;
import com.richardsenger.piratesnships.mob.MobConfig;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * The crew's side of MOB3 ({@link MonsterRules#respond}): a crew member that a monster hunts (the monster targets it)
 * or hit a moment ago fights it with a weapon in hand and runs from it without one; at its station, boarding or in
 * the brig it stands. A blow from a monster wakes a sleeper ({@link CrewRest#getUp}: the night still counts as one in
 * the hammock). {@code CrewMember} wires the goals and asks {@link #keepsTarget} beside the boarding rule.
 */
public final class CrewDefence {

    /** Scan interval of the target goal. */
    private static final int SCAN_INTERVAL = 10;

    private CrewDefence() {
    }

    /** A weapon in the main hand: one of the melee engine's, a sword, an axe or a trident. */
    public static boolean armed(LivingEntity e) {
        ItemStack held = e.getMainHandItem();
        if (held.isEmpty()) return false;
        return MeleeService.weaponInHand(e).isPresent() || held.getItem() instanceof SwordItem
                || held.getItem() instanceof AxeItem || held.getItem() instanceof TridentItem;
    }

    /** At its station, boarding (the boarding rules lead it) or a prisoner. */
    public static boolean busy(CrewMember crew) {
        return crew.isAtStation() || crew.isBoarding() || BrigService.isPrisoner(crew);
    }

    public static MonsterRules.Response response(CrewMember crew) {
        return MonsterRules.respond(MobConfig.CREW_FIGHT_MONSTERS.get(), armed(crew), busy(crew));
    }

    /** {@code e} is a living monster that hunts {@code crew} or hit it within {@code mobs.grudge_ticks}. */
    public static boolean engaged(CrewMember crew, LivingEntity e) {
        if (!(e instanceof Enemy) || !e.isAlive() || e.isRemoved()) return false;
        if (e instanceof Mob m && m.getTarget() == crew) return true;
        return crew.getLastHurtByMob() == e && crew.tickCount - crew.getLastHurtByMobTimestamp() < MobConfig.GRUDGE_TICKS.get();
    }

    private static boolean inRange(CrewMember crew, LivingEntity e, double factor) {
        double r = MobConfig.CREW_DEFENCE_RANGE.get() * factor;
        return crew.distanceToSqr(e) <= r * r;
    }

    /** Whether {@code crew} keeps fighting {@code target}: an armed, free crew member against a monster after it. */
    public static boolean keepsTarget(CrewMember crew, LivingEntity target) {
        return response(crew) == MonsterRules.Response.FIGHT && engaged(crew, target) && inRange(crew, target, 1.5);
    }

    /** Whether {@code crew} runs from {@code e}: unarmed, free, and {@code e} is a monster after it. */
    public static boolean fleesFrom(CrewMember crew, LivingEntity e) {
        return response(crew) == MonsterRules.Response.FLEE && engaged(crew, e);
    }

    /** The nearest monster within {@code mobs.crew_defence_range} that is after {@code crew}, else {@code null}. */
    public static @Nullable LivingEntity threat(CrewMember crew) {
        double r = MobConfig.CREW_DEFENCE_RANGE.get();
        LivingEntity best = null;
        double bestSq = r * r;
        for (LivingEntity e : crew.level().getEntitiesOfClass(LivingEntity.class, new AABB(crew.blockPosition()).inflate(r),
                e -> e instanceof Enemy && engaged(crew, e))) {
            double d = crew.distanceToSqr(e);
            if (d <= bestSq) {
                best = e;
                bestSq = d;
            }
        }
        return best;
    }

    /** From {@code CrewMember#hurt}: a monster's blow gets a sleeper out of its hammock. */
    public static void onHurt(CrewMember crew, DamageSource source) {
        if (crew.level().isClientSide() || !(source.getEntity() instanceof Enemy) || !MobConfig.CREW_FIGHT_MONSTERS.get()) return;
        if (crew.rest() != null) CrewRest.getUp(crew, false);
    }

    /** The unarmed crew member's flight (priority above every other move goal of the crew). */
    public static Goal fleeGoal(CrewMember crew) {
        return new AvoidEntityGoal<>(crew, LivingEntity.class, e -> fleesFrom(crew, e),
                MobConfig.CREW_DEFENCE_RANGE.get().floatValue(), 1.0, 1.25, e -> true);
    }

    /** Picks the monster an armed crew member fights ({@link #threat}). */
    public static final class TargetGoalImpl extends TargetGoal {

        private final CrewMember crew;
        private @Nullable LivingEntity found;
        private int cooldown;

        public TargetGoalImpl(CrewMember crew) {
            super(crew, false);
            this.crew = crew;
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
            if (response(crew) != MonsterRules.Response.FIGHT) return false;
            found = threat(crew);
            return found != null;
        }

        @Override
        public void start() {
            mob.setTarget(found);
            super.start();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity t = mob.getTarget();
            return t != null && crew.keepsTarget(t);
        }

        /** Clears the target only when nothing else keeps it (a boarding foe set meanwhile stays). */
        @Override
        public void stop() {
            LivingEntity t = mob.getTarget();
            if (t != null && !crew.keepsTarget(t)) mob.setTarget(null);
            targetMob = null;
        }

        @Override
        protected double getFollowDistance() {
            return MobConfig.CREW_DEFENCE_RANGE.get() * 1.5;
        }
    }
}
