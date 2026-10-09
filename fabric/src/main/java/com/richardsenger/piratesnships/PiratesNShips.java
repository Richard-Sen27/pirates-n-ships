package com.richardsenger.piratesnships;

import net.fabricmc.api.ModInitializer;

/**
 * Fabric entry point. TODO Fabric port (milestone 22): wire services like the NeoForge entry point does, and add a
 * ClientModInitializer that forwards {@code ClientEvents} like {@code NeoForgeClientSetup}, among them:
 * <ul>
 *   <li>TODO {@code ClientEvents.additionalModels()}: {@code ModelLoadingPlugin.register(ctx -> ctx.addModels(...))},
 *       and {@code ClientEvents.setAdditionalModelKey} to the key Fabric bakes those models under.</li>
 *   <li>TODO {@code ClientEvents.RENDER_FRAME_PRE}: no Fabric API event fires between the mouse turning the player and
 *       the frame being drawn ({@code WorldRenderEvents.START} is close but later); likely a small mixin at the head of
 *       {@code GameRenderer.render}.</li>
 *   <li>TODO {@code ClientEvents.armorModels()} (ART9, the coats with tails): per registration
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
    }
}
