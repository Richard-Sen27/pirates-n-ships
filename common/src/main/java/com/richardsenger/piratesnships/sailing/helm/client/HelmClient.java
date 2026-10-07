package com.richardsenger.piratesnships.sailing.helm.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.sailing.helm.HelmContent;

/** Client setup of wheel steering (HELM1), from {@code SailingModule.initClient()}. */
public final class HelmClient {

    private HelmClient() {
    }

    public static void init() {
        HelmSteeringClient.init();
        ClientEvents.registerBlockEntityRenderer(HelmContent.HELM_ENTITY, HelmWheelRenderer::new);
        ClientEvents.registerHudLayer(Constants.id("helm_rudder"), HelmOverlay::render);
    }
}
