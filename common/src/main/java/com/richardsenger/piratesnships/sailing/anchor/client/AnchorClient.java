package com.richardsenger.piratesnships.sailing.anchor.client;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.sailing.anchor.AnchorContent;

/** Client setup of the visible anchor (physical client only, from {@code SailingModule.initClient()}). */
public final class AnchorClient {

    private AnchorClient() {
    }

    public static void init() {
        ClientEvents.registerModelLayer(AnchorRenderer.LAYER, AnchorRenderer::createLayer);
        ClientEvents.registerEntityRenderer(AnchorContent.ANCHOR, AnchorRenderer::new);
    }
}
