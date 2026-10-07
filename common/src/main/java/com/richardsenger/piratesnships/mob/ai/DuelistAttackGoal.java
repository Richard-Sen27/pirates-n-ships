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
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Sword fighting through the melee engine (docs/design.md §8.5 "NPC duelists"): the mob walks into reach and feeds
 * {@link DuelistBrain}'s decisions to {@link MeleeService} exactly like a player's keys (slash, thrust, guard, parry),
 * so its attacks have the same telegraphed wind-up and the same parry windows, a player's parry staggers it, and its
 * own parries open a riposte window that the brain uses at once. Skill: {@code mobs.pirate_skill} /
 * {@code mobs.officer_skill}, scaled by {@code melee.npc_skill_multiplier}. With the tier's feint frequency (and
 * {@code melee.npc_feints} on) an attack is started as a feint: the brain aborts it when the opponent parries or
 * guards, and the real follow-up comes without the usual pause.
 *
 * <p>Only while {@code melee.skill_based_combat} is on and the mob holds a skill-based sword; otherwise
 * {@link VanillaSwordGoal} fights with vanilla melee.
 */
public class DuelistAttackGoal extends Goal {

    private static final int REPATH_TICKS = 5;

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
    private int repath;

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
        repath = 0;
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
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        WeaponDefinition w = weapon();
        if (target == null || w == null) return;
        long now = mob.level().getGameTime();
        mob.getLookControl().setLookAt(target, 30f, 30f);

        double gap = mob.distanceTo(target) - target.getBbWidth() / 2;
        double reach = Math.min(w.slash().reach(), w.thrust().reach());
        if (gap > reach * 0.75) {
            if (--repath <= 0) {
                repath = REPATH_TICKS;
                mob.getNavigation().moveTo(target, speed);
            }
        } else {
            mob.getNavigation().stop();
        }

        CombatState self = MeleeService.state(mob);
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
        boolean inReach = gap <= reach - 0.2;
        if (inReach || attacking) MobAim.face(mob, target);

        DuelistBrain.View view = new DuelistBrain.View(self, opp, inReach, threatened,
                attackSeenAt < 0 ? -1 : (int) (now - attackSeenAt), MeleeConfig.PARRY_WINDOW.get(), attackCooldown,
                MobConfig.DUELIST_GUARD_STAMINA.get().floatValue(), feintPlanned);
        SkillTier tier = MobConfig.skill(mob.kind()).tier(MeleeConfig.NPC_SKILL.get());
        switch (DuelistBrain.decide(view, tier, parryRoll, thrustRoll)) {
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

    /** Bookkeeping when an own attack started: new rolls, and whether this one is a feint. */
    private void attackStarted(SkillTier tier, boolean riposte) {
        feintPlanned = MeleeConfig.NPC_FEINTS.get() && DuelistBrain.planFeint(tier, feintRoll, riposte, afterFeint);
        afterFeint = false;
        thrustRoll = mob.getRandom().nextDouble();
        feintRoll = mob.getRandom().nextDouble();
    }
}
