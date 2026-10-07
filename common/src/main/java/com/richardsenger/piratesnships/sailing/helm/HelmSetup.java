package com.richardsenger.piratesnships.sailing.helm;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.HelmBlock;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.DelegatedModel;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.resources.ResourceLocation;

/**
 * Wiring of wheel steering (docs/design.md §5.3, HELM1) into the sailing module: each method is called from the
 * matching {@code SailingModule} hook.
 */
public final class HelmSetup {

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
                .block(HelmContent.WHEEL_MODEL, "Helm Wheel")
                .add(HelmService.KEY_HOLDING, "At the wheel (%s): move the mouse or hold strafe left/right to turn it, let go of use to release")
                .add(HelmService.KEY_RUDDER, "Rudder %s° %s")
                .add(HelmService.KEY_BUSY, "Someone else is at the wheel"));
        data.models(m -> {
            // hand-made Blockbench models (art/models/helm.bbmodel, design.md §4.8): block/helm is the pedestal (its
            // block state comes from ship.assembly), block/helm_wheel the wheel drawn by client/HelmWheelRenderer, and
            // block/helm_item both together for the item
            m.blockStates().accept(MultiVariantGenerator.multiVariant(HelmContent.WHEEL_MODEL.get(), Variant.variant()
                    .with(VariantProperties.MODEL, ModelLocationUtils.getModelLocation(HelmContent.WHEEL_MODEL.get()))));
            ResourceLocation item = ModelLocationUtils.getModelLocation(AssemblyContent.HELM.get().asItem());
            m.models().accept(item, new DelegatedModel(Constants.id("block/helm_item")));
        });
    }
}
