package com.richardsenger.piratesnships.mob.ai;

import com.richardsenger.piratesnships.combat.melee.MeleeAttachments;
import com.richardsenger.piratesnships.combat.melee.MeleeConfig;
import com.richardsenger.piratesnships.combat.melee.MeleeService;
import com.richardsenger.piratesnships.combat.melee.npc.DuelistBrain;
import com.richardsenger.piratesnships.combat.melee.npc.SkillTier;
import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.combat.melee.weapon.WeaponDefinition;
import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.platform.Services;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Locale;

/**
 * Sword fighting through the melee engine (docs/design.md §8.5 "NPC duelists"): the mob walks into reach and feeds
 * {@link DuelistBrain}'s decisions to {@link MeleeService} exactly like a player's keys (slash, thrust, guard, parry),
 * so its attacks have the same telegraphed wind-up and the same parry windows, a player's parry staggers it, and its
 * own parries open a riposte window that the brain uses at once. Skill: {@code mobs.pirate_skill} /
 * {@code mobs.officer_skill}, scaled by {@code melee.npc_skill_multiplier}. With the tier's feint frequency (and
 * {@code melee.npc_feints} on) an attack is started as a feint: the brain aborts it when the opponent parries or
 * guards, and the real follow-up comes without the usual pause.
 *
 * <p>Closing in on a moving target (M5, {@link ChaseRules}): the mob walks at {@code mobs.duelist_chase_speed} until the
 * target is {@code mobs.duelist_approach_margin} inside its reach, keeps walking during its own wind-up, recalculates
 * its path on a vanilla-like cadence, and starts an attack only when the gap predicted for the first hit frame is
 * {@code mobs.duelist_reach_margin} inside the reach. {@code /pirates mob debug on} traces its states.
 *
 * <p>Only while {@code melee.skill_based_combat} is on and the mob holds a skill-based sword; otherwise
 * {@link VanillaSwordGoal} fights with vanilla melee.
 */
public class DuelistAttackGoal extends Goal {

    /** Ground speed per (attribute × modifier)² on flat ground: zza = speed and the input scales with speed again. */
    private static final double GROUND_SPEED_FACTOR = 1.0 / (1.0 - 0.6 * 0.91);
    /** Heights (blocks) between feet beyond which the target is out of sword reach whatever the horizontal gap. */
    private static final double MAX_HEIGHT_DIFFERENCE = 1.5;

    private final SeafarerMob mob;
    private final double speed;
    private int attackCooldown;
    private long attackSeenAt = -1;
    private double parryRoll;
    private double thrustRoll;
    private double feintRoll;
    /** The current attack was started as a feint and is still in its wind-up. */
    private boolean feintPlanned;
    /** The last attack was a feint: the next one is real and follows without the pause. */
    private boolean afterFeint;
    private boolean wasAttacking;
    // chase state
    private int repathIn;
    private double pathedX, pathedY, pathedZ;
    private boolean walking;
    private double lastGap = -1;
    private double closing;

    public DuelistAttackGoal(SeafarerMob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private @Nullable WeaponDefinition weapon() {
        return MeleeService.weaponInHand(mob).orElse(null);
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        // keepsTarget: the goal selector ticks before customServerAiStep re-checks a target set from outside
        return t != null && t.isAlive() && MeleeService.skillBasedCombat() && weapon() != null && mob.keepsTarget(t);
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
        repathIn = 0;
        walking = false;
        lastGap = -1;
        closing = 0;
        thrustRoll = mob.getRandom().nextDouble();
        feintRoll = mob.getRandom().nextDouble();
    }

    @Override
    public void stop() {
        if (MeleeService.isActive(mob) && MeleeService.state(mob).guardHeld()) MeleeService.guardUp(mob);
        mob.getNavigation().stop();
        attackSeenAt = -1;
        feintPlanned = false;
        afterFeint = false;
        walking = false;
        DuelistDebug.report(mob, "duelist", "stopped", mob.getTarget(), "");
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        WeaponDefinition w = weapon();
        if (target == null || w == null) return;
        long now = mob.level().getGameTime();
        mob.getLookControl().setLookAt(target, 30f, 30f);

        CombatState self = MeleeService.state(mob);
        double reach = Math.min(w.slash().reach(), w.thrust().reach());
        AABB box = target.getBoundingBox();
        double gap = ChaseRules.gap(mob.getX(), mob.getZ(), box.minX, box.minZ, box.maxX, box.maxZ);
        boolean level = Math.abs(target.getY() - mob.getY()) <= MAX_HEIGHT_DIFFERENCE;
        closing = lastGap < 0 ? 0 : ChaseRules.closing(closing, lastGap, gap);
        lastGap = gap;
        double chaseSpeed = speed * MobConfig.DUELIST_CHASE_SPEED.get();
        approach(target, gap, ChaseRules.stopGap(reach, MobConfig.DUELIST_APPROACH_MARGIN.get()), chaseSpeed, self.phase());

        CombatState opp = Services.ATTACHMENTS.has(target, MeleeAttachments.COMBAT_STATE)
                ? MeleeService.state(target) : CombatState.fresh(MeleeConfig.STAMINA_MAX.get().floatValue());

        // telegraph bookkeeping: one parry roll per opponent attack, reaction time counted from its start
        boolean oppAttacking = opp.phase() == Phase.WINDUP || opp.phase() == Phase.ACTIVE;
        if (oppAttacking && attackSeenAt < 0) {
            attackSeenAt = now - (opp.phase() == Phase.WINDUP ? opp.elapsed() : 0);
            parryRoll = mob.getRandom().nextDouble();
        } else if (!oppAttacking) {
            attackSeenAt = -1;
        }

        // pause after each own attack (none after a feint: the real attack follows at once)
        boolean attacking = self.phase().attacking();
        if (wasAttacking && !attacking) {
            int pause = MobConfig.DUELIST_ATTACK_PAUSE.get();
            attackCooldown = afterFeint ? 0 : pause + mob.getRandom().nextInt(pause + 1);
        }
        if (self.phase() != Phase.WINDUP) feintPlanned = false; // the wind-up ran out: the attack was real
        wasAttacking = attacking;
        if (attackCooldown > 0) attackCooldown--;

        WeaponDefinition ow = MeleeService.weapon(target);
        double oppReach = ow == null ? 3.0 : Math.max(ow.slash().reach(), ow.thrust().reach());
        boolean threatened = mob.distanceTo(target) - mob.getBbWidth() / 2 <= oppReach + 0.5 && MobAim.facing(target, mob);
        double ownSpeed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * chaseSpeed;
        boolean inReach = level && ChaseRules.attackReaches(gap, closing, w.slash().windupTicks(), reach,
                MobConfig.DUELIST_REACH_MARGIN.get(), ownSpeed * ownSpeed * GROUND_SPEED_FACTOR);
        if (inReach || attacking) MobAim.face(mob, target);

        DuelistBrain.View view = new DuelistBrain.View(self, opp, inReach, threatened,
                attackSeenAt < 0 ? -1 : (int) (now - attackSeenAt), MeleeConfig.PARRY_WINDOW.get(), attackCooldown,
                MobConfig.DUELIST_GUARD_STAMINA.get().floatValue(), feintPlanned);
        SkillTier tier = MobConfig.skill(mob.kind()).tier(MeleeConfig.NPC_SKILL.get());
        DuelistBrain.Decision decision = DuelistBrain.explain(view, tier, parryRoll, thrustRoll);
        if (DuelistDebug.active()) trace(target, self, decision, gap, reach);
        switch (decision.action()) {
            case SLASH -> {
                if (MeleeService.startSlash(mob, w).accepted()) attackStarted(tier, self.riposteReady());
            }
            case THRUST -> {
                if (MeleeService.startThrust(mob, w).accepted()) attackStarted(tier, self.riposteReady());
            }
            case FEINT -> {
                if (MeleeService.feint(mob).accepted()) {
                    feintPlanned = false;
                    afterFeint = true;
                }
            }
            case GUARD -> MeleeService.guardDown(mob, w);
            case RELEASE_GUARD -> MeleeService.guardUp(mob);
            case PARRY -> MeleeService.parry(mob, w);
            case NONE -> {
            }
        }
    }

    /**
     * Walks towards the target until it is inside the stop gap (with hysteresis), also during the own wind-up and hit
     * frames; a staggered mob stands. New paths follow {@link ChaseRules#repath}.
     */
    private void approach(LivingEntity target, double gap, double stopGap, double chaseSpeed, Phase phase) {
        if (repathIn > 0) repathIn--;
        boolean go = phase != Phase.STAGGERED && ChaseRules.approach(gap, stopGap, walking);
        if (!go) {
            if (walking) mob.getNavigation().stop();
            walking = false;
            return;
        }
        double moved = target.distanceToSqr(pathedX, pathedY, pathedZ);
        boolean pathDone = !walking || mob.getNavigation().isDone();
        // ground navigation refuses to search while airborne (just spawned, knocked back by a hit, jumping) and drops the
        // current path when asked: wait for the landing instead, or each hit taken would cost a failed-search penalty
        boolean canSearch = mob.onGround() || mob.isInWater() || mob.isPassenger();
        if (canSearch && ChaseRules.repath(repathIn, pathDone, moved, mob.getRandom().nextDouble())) {
            pathedX = target.getX();
            pathedY = target.getY();
            pathedZ = target.getZ();
            boolean found = mob.getNavigation().moveTo(target, chaseSpeed);
            repathIn = ChaseRules.nextRepathDelay(mob.getRandom().nextDouble(), found);
            DuelistDebug.stats(mob).repaths++;
            if (!found) DuelistDebug.report(mob, "path", "no path", target, "");
            else DuelistDebug.report(mob, "path", "found", target, "");
        }
        walking = true;
    }

    /** Bookkeeping when an own attack started: new rolls, and whether this one is a feint. */
    private void attackStarted(SkillTier tier, boolean riposte) {
        DuelistDebug.stats(mob).attacksStarted++;
        feintPlanned = MeleeConfig.NPC_FEINTS.get() && DuelistBrain.planFeint(tier, feintRoll, riposte, afterFeint);
        afterFeint = false;
        thrustRoll = mob.getRandom().nextDouble();
        feintRoll = mob.getRandom().nextDouble();
    }

    private void trace(LivingEntity target, CombatState self, DuelistBrain.Decision decision, double gap, double reach) {
        String state = self.phase() != Phase.IDLE ? self.phase().name().toLowerCase(Locale.ROOT)
                + (self.attack() == null ? "" : " " + self.attack().name().toLowerCase(Locale.ROOT))
                : walking ? "approaching" : "holding";
        String detail = String.format(Locale.ROOT, "gap %.2f, reach %.2f, closing %.3f/t, pause %d, %s",
                gap, reach, closing, attackCooldown, mob.level().getDifficulty().getKey());
        DuelistDebug.report(mob, "duelist", state, target, detail);
        DuelistDebug.report(mob, "brain", decision.action().name().toLowerCase(Locale.ROOT) + " (" + decision.reason() + ")",
                target, detail);
    }
}
