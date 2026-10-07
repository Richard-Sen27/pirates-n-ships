package com.richardsenger.piratesnships.law.brig;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.mob.entity.Sailor;
import com.richardsenger.piratesnships.station.winch.CaptainsWhistleItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The prisoner's side of the LA2 interactions (docs/design.md §13.3), a {@code CommonEvents.ENTITY_INTERACT} listener
 * so it runs before the target's own interaction (a villager's trades) and before the held item's:
 * <ul>
 *   <li><b>Press-gang:</b> the captain's whistle used on a shackled {@link Sailor} standing on the player's own ship
 *       (owned by the player or by nobody) turns it into a crew member ({@link PrisonerOutcomes#pressGang}). Off the
 *       ship: "Bring them aboard your ship first." The whistle on anything else keeps its own behaviour.</li>
 *   <li><b>Release:</b> sneak-use with an empty main hand on one's own prisoner sets it free
 *       ({@link PrisonerOutcomes#release}) and records the release.</li>
 * </ul>
 * The officer's side (fines, ransoms) is {@code law.turnin.OfficerTurnIns}. Behind {@code flags_brig.prisoner_interactions}.
 * On the client the listener answers SUCCESS where the server will most likely act (the prisoner state is synced), so
 * the arm swings and the whistle's radial menu does not open; the server decides.
 */
public final class PrisonerInteractions {

    public static final String MSG = "message." + Constants.MOD_ID + ".brig.";

    private PrisonerInteractions() {
    }

    public static InteractionResult onEntityInteract(Player player, Entity target, InteractionHand hand) {
        if (!(target instanceof LivingEntity living) || !LawConfig.PRISONER_INTERACTIONS.get()) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof CaptainsWhistleItem) return pressGang(player, living);
        boolean held = BrigService.state(living).heldBy(player.getUUID());
        if (PrisonerInteractionRules.releases(true, player.isShiftKeyDown(), hand == InteractionHand.MAIN_HAND, stack.isEmpty(), held)) {
            return release(player, living);
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult pressGang(Player player, LivingEntity target) {
        if (!(target instanceof Sailor) || !BrigService.isPrisoner(target)) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        PrisonerOutcomes.PressGangResult r = PrisonerOutcomes.pressGang(target, player);
        if (!r.success()) {
            player.displayClientMessage(Component.translatable(MSG + "pressgang." + r.failure().name().toLowerCase(java.util.Locale.ROOT))
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        player.sendSystemMessage(Component.translatable(MSG + "pressgang.done", r.crew().getName()).withStyle(ChatFormatting.GOLD));
        r.crew().level().playSound(null, r.crew().blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0f, 0.8f);
        r.crew().level().playSound(null, r.crew().blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 0.8f, 1.0f);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult release(Player player, LivingEntity target) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        Component name = target.getName().copy();
        PrisonerOutcomes.ReleaseResult r = PrisonerOutcomes.release(target, player);
        if (!r.success()) return InteractionResult.PASS;
        player.sendSystemMessage(Component.translatable(MSG + "release.done", name).withStyle(ChatFormatting.GOLD));
        target.level().playSound(null, target.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 0.8f, 1.2f);
        return InteractionResult.CONSUME;
    }
}
