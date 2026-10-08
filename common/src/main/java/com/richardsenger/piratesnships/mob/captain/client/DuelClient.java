package com.richardsenger.piratesnships.mob.captain.client;

import com.richardsenger.piratesnships.combat.melee.MeleeService;
import com.richardsenger.piratesnships.mob.captain.PirateCaptain;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Client side of the duel challenge (BOS1, physical client only). With a melee-engine sword in hand the melee input
 * takes over the use key ({@code combat.melee.client.MeleeInput} cancels vanilla's use), so vanilla never sends the
 * entity interaction the challenge needs. This sends it itself: when the use key goes down while sneaking with such a
 * sword and the crosshair is on a pirate captain, it runs vanilla's own interaction ({@code MultiPlayerGameMode#interact},
 * which sends the interact packet). With a vanilla sword vanilla does it already, so this stays out of the way.
 * The use key is polled once per tick: a click shorter than a tick may be missed (try again).
 */
public final class DuelClient {

    private static boolean wasDown;

    private DuelClient() {
    }

    public static void init() {
        ClientEvents.CLIENT_TICK_END.register(DuelClient::tick);
        ClientEvents.CLIENT_DISCONNECT.register(mc -> wasDown = false);
    }

    static void tick(Minecraft mc) {
        boolean down = mc.options.keyUse.isDown();
        boolean pressed = down && !wasDown;
        wasDown = down;
        LocalPlayer player = mc.player;
        if (!pressed || player == null || mc.gameMode == null || mc.screen != null || player.isSpectator()) return;
        if (!player.isShiftKeyDown() || !MeleeService.skillBasedCombat() || MeleeService.weaponInHand(player).isEmpty()) return;
        if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof PirateCaptain captain)) return;
        mc.gameMode.interact(player, captain, InteractionHand.MAIN_HAND);
    }
}
