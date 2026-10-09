package com.richardsenger.piratesnships.sailing.oars.client;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.sailing.oars.OarsContent;
import com.richardsenger.piratesnships.sailing.oars.OarsSync;

/** Client setup of the oars (OAR1): the rail rower's seat draws nothing; the rowing label is forgotten on disconnect. */
public final class OarsClient {

    private OarsClient() {
    }

    public static void init() {
        ClientEvents.registerEntityRenderer(OarsContent.OAR_SEAT, net.minecraft.client.renderer.entity.NoopRenderer::new);
        ClientEvents.CLIENT_DISCONNECT.register(mc -> OarsSync.clearClient());
    }
}
