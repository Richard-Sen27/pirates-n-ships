package com.richardsenger.piratesnships.sailing.helm;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.HelmBlock;
import net.minecraft.resources.ResourceLocation;

/**
 * Wiring of wheel steering (docs/design.md §5.3, HELM1) into the sailing module: each method is called from the
 * matching {@code SailingModule} hook.
 */
public final class HelmSetup {

    /** The wheel's hand-made parent model; every wood's child {@code block/<wood>_helm_wheel} is drawn by the renderer. */
    public static final ResourceLocation WHEEL_MODEL = Constants.id("block/helm_wheel");
    /** The whole helm for the items. */
    static final ResourceLocation ITEM_MODEL = Constants.id("block/helm_item");

    private HelmSetup() {
    }

    public static void registerConfig() {
        HelmConfig.init();
    }

    public static void registerContent() {
        HelmContent.init();
        HelmBlock.setBlockEntityFactory(HelmBlockEntity::new);
    }

    public static void registerPayloads() {
        Services.NETWORK.registerToServer(HelmWheelPayload.TYPE, HelmWheelPayload.CODEC, HelmService::handleWheel);
        Services.NETWORK.registerToServer(HelmReleasePayload.TYPE, HelmReleasePayload.CODEC, HelmService::handleRelease);
        Services.NETWORK.registerToClient(HelmSessionPayload.TYPE, HelmSessionPayload.CODEC,
                (payload, player) -> com.richardsenger.piratesnships.sailing.helm.client.HelmSteeringClient.onSession(payload));
    }

    public static void registerEvents() {
        HelmBlock.setSteeringHandler(HelmService::steer);
        CommonEvents.LEVEL_TICK_END.register(HelmService::onLevelTick);
        CommonEvents.PLAYER_LOGOUT.register(HelmService::onLogout);
        CommonEvents.SERVER_STOPPED.register(server -> HelmService.onServerStopped());
    }

    public static void initClient() {
        com.richardsenger.piratesnships.sailing.helm.client.HelmClient.init();
    }

    public static void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .add(HelmService.KEY_RUDDER, "Rudder %s° %s")
                .add(HelmService.KEY_BUSY, "Someone else is at the wheel"));
        data.models(m -> {
            // hand-made Blockbench models (art/models/helm.bbmodel, design.md §4.8) with the wood variables (WOOD4):
            // block/helm is the pedestal (its children and block states come from ship.assembly), block/helm_wheel the
            // wheel drawn by client/HelmWheelRenderer (a stand-alone model, no block state; one child per wood,
            // block/<wood>_helm_wheel), and block/helm_item both together for every wood's item
            AssemblyContent.HELMS.childModels(m, WHEEL_MODEL);
            AssemblyContent.HELMS.itemModels(m, ITEM_MODEL);
        });
    }
}
