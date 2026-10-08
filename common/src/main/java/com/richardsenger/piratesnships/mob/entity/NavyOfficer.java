package com.richardsenger.piratesnships.mob.entity;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.law.bounty.PirateTier;
import com.richardsenger.piratesnships.law.turnin.OfficerTurnIns;
import com.richardsenger.piratesnships.mob.MobKind;
import com.richardsenger.piratesnships.mob.captain.PirateCaptain;
import com.richardsenger.piratesnships.mob.ai.DuelistAttackGoal;
import com.richardsenger.piratesnships.mob.ai.VanillaSwordGoal;
import com.richardsenger.piratesnships.mob.squad.SquadLeaderGoal;
import com.richardsenger.piratesnships.mob.squad.SquadService;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Navy officer (docs/design.md §9): a skilled saber duelist ({@link DuelistAttackGoal}, skill
 * {@code mobs.officer_skill}), hostile like the soldiers. Takes bounty proofs and shackled prisoners from players he
 * is not hostile to ({@link OfficerTurnIns}, docs/design.md §13.2). A garrison officer leads a squad of his outpost's
 * soldiers on patrol (MOB2, {@code mob.squad}: {@link SquadLeaderGoal}, the squad's brain ticked from
 * {@link #customServerAiStep}). Quests come later. Hitting or killing it is a crime ({@code #pirates_n_ships:navy}).
 */
public class NavyOfficer extends SeafarerMob {

    public NavyOfficer(EntityType<? extends NavyOfficer> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return humanoid(30.0, 0.3, 3.0);
    }

    @Override
    public MobKind kind() {
        return MobKind.NAVY_OFFICER;
    }

    @Override
    protected void equip() {
        setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CombatContent.SABER.get()));
    }

    @Override
    protected void addCombatGoals() {
        goalSelector.addGoal(2, new DuelistAttackGoal(this, 1.0));
        goalSelector.addGoal(3, new VanillaSwordGoal(this, 1.0));
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(4, new SquadLeaderGoal(this));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        SquadService.tickLeader(this);
    }

    /** Turn-ins: a bounty proof in the hand used, or an empty main hand with a shackled prisoner close by. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        boolean hostile = !level().isClientSide && (attacksOnSight(player) || hasGrudge(player));
        InteractionResult turnIn = OfficerTurnIns.interact(this, player, hand, hostile, NavyOfficer::pirateTier);
        return turnIn != InteractionResult.PASS ? turnIn : super.mobInteract(player, hand);
    }

    /**
     * The navy's reward rank of a captured NPC: a named pirate captain (BOS1) is a captain, every other pirate counts
     * as a deckhand until pirates have ranks.
     */
    public static @Nullable PirateTier pirateTier(LivingEntity prisoner) {
        if (prisoner instanceof PirateCaptain) return PirateTier.CAPTAIN;
        return prisoner instanceof Pirate ? PirateTier.DECKHAND : null;
    }

    @Override
    protected SoundEvent ambientSound() {
        return SoundEvents.VINDICATOR_AMBIENT;
    }

    @Override
    protected SoundEvent hurtSound() {
        return SoundEvents.VINDICATOR_HURT;
    }

    @Override
    protected SoundEvent deathSound() {
        return SoundEvents.VINDICATOR_DEATH;
    }
}
