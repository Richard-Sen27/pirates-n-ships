package com.richardsenger.piratesnships.seachest.client;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.seachest.SeaChestConfig;
import com.richardsenger.piratesnships.seachest.SeaChestContent;
import com.richardsenger.piratesnships.seachest.SeaChestWearing;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Client side of the sea chest (physical client only): the floating chest's renderer and the sprint-key release. */
public final class SeaChestClient {

    private SeaChestClient() {
    }

    public static void init() {
        ClientEvents.registerEntityRenderer(SeaChestContent.ENTITY, SeaChestRenderer::new);
        ClientEvents.CLIENT_TICK_START.register(SeaChestClient::releaseSprintKey);
    }

    /**
     * The local player decides to sprint inside its own movement step, before the player tick hook can clear the
     * flag, and would sprint every tick while the key is held. Releasing the key before the tick leaves only the
     * double-tap-forward start, which the tick hook ends after one tick (and which can't retrigger while forward is
     * held). A toggled sprint key is toggled off.
     */
    private static void releaseSprintKey(Minecraft mc) {
        if (mc.player == null || !SeaChestConfig.ENABLED.get() || !SeaChestWearing.isWearing(mc.player)) {
            return;
        }
        KeyMapping sprint = mc.options.keySprint;
        if (sprint.isDown()) {
            sprint.setDown(false);
            if (sprint.isDown()) {
                // ToggleKeyMapping in toggle mode ignores setDown(false); setDown(true) flips it off
                sprint.setDown(true);
            }
        }
    }
}
