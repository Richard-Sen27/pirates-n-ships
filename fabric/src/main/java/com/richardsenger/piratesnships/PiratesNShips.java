package com.richardsenger.piratesnships;

import com.richardsenger.piratesnships.platform.FabricAttachmentHelper;
import com.richardsenger.piratesnships.platform.FabricCapabilityHelper;
import com.richardsenger.piratesnships.platform.FabricEventForwarder;
import com.richardsenger.piratesnships.platform.FabricGameTests;
import com.richardsenger.piratesnships.platform.FabricNetworkHelper;
import com.richardsenger.piratesnships.platform.FabricRegistryHelper;
import com.richardsenger.piratesnships.platform.Services;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric entry point (FAB1: both sides' common init, the server-side services, event forwarding and GameTests;
 * docs/fabric.md). Feature modules never add code here; everything is generic over {@code ModModules}.
 *
 * <p>Fabric registers content immediately (the vanilla registries are open during mod initialization), so registry
 * entries, payloads, attachments and configs are in place once {@link PiratesNShipsCommon#init()} returns; what needs
 * every entry to exist (entity attributes, spawn placements, transfer API storages) is applied afterwards.
 *
 * <p>The client entry point is FAB2's: a {@code ClientModInitializer} that calls {@link PiratesNShipsCommon#initClient()}
 * and forwards {@code ClientEvents} like {@code NeoForgeClientSetup}, among them:
 * <ul>
 *   <li>TODO FAB2 {@code ClientEvents.additionalModels()}: {@code ModelLoadingPlugin.register(ctx -> ctx.addModels(...))},
 *       and {@code ClientEvents.setAdditionalModelKey} to the key Fabric bakes those models under.</li>
 *   <li>TODO FAB2 {@code ClientEvents.RENDER_FRAME_PRE}: no Fabric API event fires between the mouse turning the player
 *       and the frame being drawn ({@code WorldRenderEvents.START} is close but later); likely a small mixin at the head
 *       of {@code GameRenderer.render}.</li>
 *   <li>TODO FAB2 {@code ClientEvents.armorModels()} (ART9, the coats with tails): per registration
 *       {@code ArmorRenderer.register((matrices, buffers, stack, entity, slot, light, contextModel) -> ..., items)};
 *       get the provider's model, {@code contextModel.copyPropertiesTo(model)} plus the part visibility (as NeoForge's
 *       {@code ClientHooks.copyModelProperties}), and draw it with {@code ArmorRenderer.renderPart} and the material's
 *       layer texture ({@code textures/models/armor/<material>_layer_1.png}), glint included.</li>
 * </ul>
 */
public class PiratesNShips implements ModInitializer {

    @Override
    public void onInitialize() {
        PiratesNShipsCommon.init();

        ((FabricRegistryHelper) Services.REGISTRY).finish();
        ((FabricCapabilityHelper) Services.CAPABILITIES).finish();
        ((FabricNetworkHelper) Services.NETWORK).attach();
        ((FabricAttachmentHelper) Services.ATTACHMENTS).attach();
        FabricEventForwarder.attach();
        FabricGameTests.register();
    }
}
