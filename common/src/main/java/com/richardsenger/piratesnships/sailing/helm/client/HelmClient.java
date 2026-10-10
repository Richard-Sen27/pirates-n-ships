package com.richardsenger.piratesnships.sailing.helm.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.sailing.helm.HelmContent;
import com.richardsenger.piratesnships.sailing.helm.HelmSetup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.decor.wood.client.WoodModels;
import net.minecraft.resources.ResourceLocation;

/** Client setup of wheel steering (HELM1), from {@code SailingModule.initClient()}. */
public final class HelmClient {

    /**
     * The hand-made wheel model ({@code models/block/helm_wheel.json}); {@link HelmWheelRenderer} draws its child of the
     * helm's wood ({@code block/<wood>_helm_wheel}, WOOD4) as a stand-alone model.
     */
    static final ResourceLocation WHEEL_MODEL = HelmSetup.WHEEL_MODEL;

    private HelmClient() {
    }

    public static void init() {
        HelmSteeringClient.init();
        WoodModels.register(AssemblyContent.HELMS, WHEEL_MODEL);
        ClientEvents.registerBlockEntityRenderer(HelmContent.HELM_ENTITY, HelmWheelRenderer::new);
        ClientEvents.registerHudLayer(Constants.id("helm_rudder"), HelmOverlay::render);
    }
}
