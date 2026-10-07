package com.richardsenger.piratesnships.combat.firearms.client;

import com.richardsenger.piratesnships.combat.firearms.FirearmItem;
import com.richardsenger.piratesnships.combat.firearms.FirearmRules;
import com.richardsenger.piratesnships.combat.firearms.FirearmsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Sneaking while aiming lowers the gun without firing (docs/design.md §8.1, {@code firearms.aim.lower_on_sneak}).
 *
 * <p>At the end of the client tick, if the local player sneaks during an aim session, the use is released at once
 * through {@code MultiPlayerGameMode#releaseUsingItem}, which sends {@code RELEASE_USE_ITEM}. Packet order: in a
 * client tick vanilla sends the use-key release from {@code Minecraft#handleKeybinds} <i>before</i> the level ticks
 * the player, and {@code LocalPlayer#sendPosition} (in the player's tick, after {@code aiStep} read the input) sends
 * {@code PRESS_SHIFT_KEY}. {@code CLIENT_TICK_END} runs after both, so this tick's sneak command is already on the
 * wire when this release is sent; the server handles both on its main thread in that order and
 * {@code FirearmItem#releaseUsing} sees the player sneaking. No payload is needed. (A player who lets go of the use
 * key in the very tick they press sneak still fires: vanilla's own release goes out first.)
 */
public final class FirearmLowering {

    private FirearmLowering() {
    }

    /** {@code CLIENT_TICK_END} listener. */
    public static void onClientTickEnd(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || !player.isUsingItem()) return;
        if (!(player.getUseItem().getItem() instanceof FirearmItem)) return;
        if (FirearmRules.lowers(player.isShiftKeyDown(), FirearmRules.isAimSession(player.getUseItemRemainingTicks()),
                FirearmsConfig.LOWER_ON_SNEAK.get())) {
            mc.gameMode.releaseUsingItem(player);
        }
    }
}
