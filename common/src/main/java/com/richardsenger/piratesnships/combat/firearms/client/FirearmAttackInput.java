package com.richardsenger.piratesnships.combat.firearms.client;

import com.richardsenger.piratesnships.combat.firearms.FirearmFirePayload;
import com.richardsenger.piratesnships.combat.firearms.FirearmRules;
import com.richardsenger.piratesnships.combat.firearms.FirearmTrigger;
import com.richardsenger.piratesnships.combat.firearms.FirearmTriggerRules;
import com.richardsenger.piratesnships.combat.firearms.FirearmsConfig;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

/**
 * The attack key pulls the trigger (FA1, docs/design.md §8 "Input", {@code firearms.fire_on_attack}, a synced server
 * value). Two hooks:
 * <ul>
 *   <li>{@code CLIENT_TICK_START} (NeoForge {@code ClientTickEvent.Pre}, which runs before
 *       {@code Minecraft#handleKeybinds}): while the player has a gun to fire ({@link FirearmTrigger#gunHand}), the
 *       attack key's clicks are taken here and one {@link FirearmFirePayload} is sent for them. Taking them first is
 *       needed because vanilla drops attack clicks without any event while an item is in use, i.e. while aiming.</li>
 *   <li>{@link ClientEvents#INTERACTION_KEY}: with a gun to fire, a held attack key (block breaking, continued
 *       attacks) is cancelled without a swing, so a gun never swings, hits or mines.</li>
 * </ul>
 * The server decides everything ({@code FirearmTrigger#pull}); a gun being loaded sends nothing. Client thread only.
 */
public final class FirearmAttackInput {

    private FirearmAttackInput() {
    }

    /** Whether the attack key belongs to the gun right now. */
    static boolean active(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isSpectator() || !player.isAlive()) return false;
        return FirearmTriggerRules.interceptsAttack(FirearmsConfig.ENABLED.get(), FirearmsConfig.FIRE_ON_ATTACK.get(),
                FirearmTrigger.gunHand(player) != null);
    }

    /** {@code CLIENT_TICK_START} listener. */
    public static void onClientTickStart(Minecraft mc) {
        if (mc.screen != null || mc.getOverlay() != null || mc.getConnection() == null || !active(mc)) return;
        boolean clicked = false;
        while (mc.options.keyAttack.consumeClick()) clicked = true;
        if (!clicked) return;
        LocalPlayer player = mc.player;
        // a loading session (reloading) never fires; the server checks it too
        if (player.isUsingItem() && !FirearmRules.isAimSession(player.getUseItemRemainingTicks())) return;
        Services.NETWORK.sendToServer(FirearmFirePayload.INSTANCE);
    }

    /** {@link ClientEvents#INTERACTION_KEY} listener: no swing, hit or mining with a gun to fire. */
    public static ClientEvents.InteractionKeyResult onInteraction(Minecraft mc, ClientEvents.InteractionInput input, InteractionHand hand) {
        if (input != ClientEvents.InteractionInput.ATTACK || !active(mc)) return ClientEvents.InteractionKeyResult.PASS;
        return ClientEvents.InteractionKeyResult.CANCEL;
    }
}
