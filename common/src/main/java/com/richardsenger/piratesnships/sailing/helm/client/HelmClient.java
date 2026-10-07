package com.richardsenger.piratesnships.sailing.helm.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.sailing.helm.HelmContent;
import net.minecraft.resources.ResourceLocation;

/** Client setup of wheel steering (HELM1), from {@code SailingModule.initClient()}. */
public final class HelmClient {

    /** The hand-made wheel model ({@code models/block/helm_wheel.json}), a stand-alone model drawn by {@link HelmWheelRenderer}. */
    static final ResourceLocation WHEEL_MODEL = Constants.id("block/helm_wheel");

    private HelmClient() {
    }

    public static void init() {
        HelmSteeringClient.init();
        ClientEvents.registerAdditionalModel(WHEEL_MODEL);
        ClientEvents.registerBlockEntityRenderer(HelmContent.HELM_ENTITY, HelmWheelRenderer::new);
        ClientEvents.registerHudLayer(Constants.id("helm_rudder"), HelmOverlay::render);
    }
}
