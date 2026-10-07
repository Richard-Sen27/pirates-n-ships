package com.richardsenger.piratesnships.combat.firearms.client;

import com.richardsenger.piratesnships.combat.firearms.FirearmRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * {@link FirearmClientState} of the physical client: the local player's use session. The hotbar and the inventory
 * draw the very stack instance the player holds, which is also the use item ({@code LivingEntity#startUsingItem}
 * takes the hand stack); after the server resent the slot the use item can briefly be another instance, so the hand
 * slot is compared too.
 */
public final class LocalFirearmClientState implements FirearmClientState {

    @Override
    public int loadingHeldTicks(ItemStack stack) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isUsingItem()) return -1;
        if (player.getUseItem() != stack && player.getItemInHand(player.getUsedItemHand()) != stack) return -1;
        int remaining = player.getUseItemRemainingTicks();
        if (FirearmRules.isAimSession(remaining)) return -1;
        return FirearmRules.heldTicks(remaining);
    }
}
