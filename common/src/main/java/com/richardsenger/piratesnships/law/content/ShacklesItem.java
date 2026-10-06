package com.richardsenger.piratesnships.law.content;

import com.richardsenger.piratesnships.law.brig.BrigService;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Shackles (design.md §13.3): use on a weakened mob (or a wanted player) to capture it, on your own prisoner to
 * start or stop leading it, on a prisoner whose chain is loose to take it over. Rules in {@code law.brig}.
 * Mobs whose own interaction comes first (e.g. villagers with trades) can't be shackled by hand yet.
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
}
