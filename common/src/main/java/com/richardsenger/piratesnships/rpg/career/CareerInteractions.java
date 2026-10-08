package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.law.brig.BrigService;
import com.richardsenger.piratesnships.law.proof.BountyProofItem;
import com.richardsenger.piratesnships.law.turnin.OfficerTurnIns;
import com.richardsenger.piratesnships.mob.entity.NavyOfficer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Opens the career screen at a navy officer (docs/design.md §15, CAR1), a {@code CommonEvents.ENTITY_INTERACT}
 * listener. It takes only the gesture the officer's own turn-ins leave free ({@link OfficerTurnIns}: proof, prisoners,
 * coins): the main hand empty, not sneaking, no bounty proof in the off hand, no prisoner of the player's near the
 * officer, and the officer not a prisoner itself. A hostile officer refuses with a message. Anything else passes, so
 * the turn-ins in {@code NavyOfficer.mobInteract} still run.
 */
public final class CareerInteractions {

    private CareerInteractions() {
    }

    /** Whether the gesture is the career one (both sides; the prisoner state is synced). */
    public static boolean isCareerGesture(Player player, Entity target, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !(target instanceof NavyOfficer officer) || !officer.isAlive()) return false;
        if (!player.getMainHandItem().isEmpty() || player.isShiftKeyDown()) return false;
        if (player.getOffhandItem().getItem() instanceof BountyProofItem) return false;
        if (BrigService.isPrisoner(officer)) return false;
        return OfficerTurnIns.heldPrisonersNear(officer, player).isEmpty();
    }

    public static InteractionResult onEntityInteract(Player player, Entity target, InteractionHand hand) {
        if (!Careers.enabled() || player.isSpectator() || !isCareerGesture(player, target, hand)) return InteractionResult.PASS;
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        NavyOfficer officer = (NavyOfficer) target;
        if (CareerBackend.hostile(officer, sp)) {
            sp.displayClientMessage(Component.translatable(CareerText.HOSTILE).withStyle(ChatFormatting.RED), true);
            officer.playSound(SoundEvents.VILLAGER_NO, 1.0f, 0.9f);
            return InteractionResult.CONSUME;
        }
        CareerBackend.open(sp, officer);
        officer.playSound(SoundEvents.VILLAGER_AMBIENT, 1.0f, 0.9f);
        return InteractionResult.CONSUME;
    }
}
