package com.richardsenger.piratesnships.law.content;

import com.richardsenger.piratesnships.law.brig.BrigService;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Shackles (design.md §13.3): use on a weakened mob (or a wanted player) to capture it, on your own prisoner to
 * start or stop leading it, on a prisoner whose chain is loose to take it over. Rules in {@code law.brig}.
 * Mobs whose own interaction comes first (villagers with trades, wandering traders) are handled through
 * {@link #onEntityInteract}, which runs before that interaction.
 */
public class ShacklesItem extends Item {

    public ShacklesItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        return BrigService.useShackles(stack, player, target);
    }

    /**
     * {@code CommonEvents.ENTITY_INTERACT} listener: shackles in {@code hand} used on a target the brig claims
     * ({@link BrigService#claimsShackleClick}) are handled here, before the target's own interaction, which is then
     * skipped (no trade screen). Anything else passes on to vanilla and {@link #interactLivingEntity}. Server only:
     * on the client it always passes, the server's answer decides.
     */
    public static InteractionResult onEntityInteract(Player player, Entity target, InteractionHand hand) {
        if (player.level().isClientSide || !(target instanceof LivingEntity living)) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof ShacklesItem) || !BrigService.claimsShackleClick(player, living)) return InteractionResult.PASS;
        return BrigService.useShackles(stack, player, living);
    }
}
