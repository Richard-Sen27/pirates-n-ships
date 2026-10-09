package com.richardsenger.piratesnships.platform;

import com.mojang.blaze3d.systems.RenderSystem;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.client.ConfigScreenFactoryRegistry;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Client-only wiring on Fabric (FAB2), the twin of {@code NeoForgeClientSetup}: forwards the {@link ClientEvents}
 * registrations and events to Fabric API. Hooks without a Fabric API event are fired by this module's client mixins
 * ({@code fabric.mixin.client}, {@code pirates_n_ships.fabric.mixins.json}): {@code INTERACTION_KEY} and
 * {@code RENDER_FRAME_PRE} ({@code MixinMinecraft}), {@code COMPUTE_FOV} ({@code MixinAbstractClientPlayer}),
 * {@code COMPUTE_CAMERA_ROLL} ({@code MixinCamera}), {@code SELECT_MUSIC} ({@code MixinMusicManager}) and
 * {@code SOUND_STREAM_STARTED} ({@code MixinSoundEngine}). docs/fabric.md has the full hook table with every
 * difference to NeoForge.
 *
 * <p>Fabric registers immediately, so everything is registered here, at client init, after every mod's main entry
 * point (the registries are filled) and before the first resource load (models, renderers, layers).
 */
public final class FabricClientSetup {

    /**
     * The variant under which Fabric API's model loading stores the models added through
     * {@code ModelLoadingPlugin.Context#addModels} ({@code ModelLoadingConstants.RESOURCE_SPECIAL_VARIANT}, an impl
     * constant; {@code FabricBakedModelManager#getModel(ResourceLocation)} looks them up the same way).
     * {@code FabricClientSetupTest} checks the constant.
     */
    static final String ADDITIONAL_MODEL_VARIANT = "fabric_resource";

    private FabricClientSetup() {
    }

    public static void attach() {
        // Config screen: Forge Config API Port's NeoForge screen, shown by Mod Menu (FCAP ships the Mod Menu entry point)
        ConfigScreenFactoryRegistry.INSTANCE.register(Constants.MOD_ID, ConfigurationScreen::new);

        // Registrations (NeoForge: the mod bus registration events)
        ClientEvents.keyMappings().forEach(KeyBindingHelper::registerKeyBinding);
        ClientEvents.entityRenderers().forEach(FabricClientSetup::registerEntity);
        ClientEvents.blockEntityRenderers().forEach(FabricClientSetup::registerBlockEntity);
        ClientEvents.modelLayers().forEach(l -> EntityModelLayerRegistry.registerModelLayer(l.location(), () -> l.definition().get()));
        ClientEvents.setAdditionalModelKey(id -> new ModelResourceLocation(id, ADDITIONAL_MODEL_VARIANT));
        ModelLoadingPlugin.register(ctx -> ctx.addModels(ClientEvents.additionalModels()));
        FabricArmorModels.register(ClientEvents.armorModels());
        ClientEvents.blockColors().forEach(c ->
                ColorProviderRegistry.BLOCK.register(c.color(), c.blocks().stream().map(Supplier::get).toArray(Block[]::new)));
        registerRenderLayers();
        HudRenderCallback.EVENT.register(FabricClientSetup::renderHudLayers);

        // Events (NeoForge: the game bus)
        ClientTickEvents.START_CLIENT_TICK.register(mc -> ClientEvents.CLIENT_TICK_START.invoker().onTick(mc));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> ClientEvents.CLIENT_TICK_END.invoker().onTick(mc));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> ClientEvents.CLIENT_DISCONNECT.invoker().onDisconnect(mc));
        // Fabric's callback has no player argument: the client player, null while no world is loaded (as on NeoForge
        // while the search trees are built)
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) ->
                ClientEvents.ITEM_TOOLTIP.invoker().onTooltip(stack, context, flag, Minecraft.getInstance().player, lines));
        // After particles (NeoForge: right after the translucent block layer); the flood surface's render type picks
        // its own output target, so the bound target does not matter (docs/fabric.md)
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> ClientEvents.RENDER_AFTER_TRANSLUCENT.invoker()
                .onRender(ctx.camera(), ctx.frustum(), ctx.tickCounter().getGameTimeDeltaPartialTick(false)));
        // Client side of ENTITY_JOIN_LEVEL (the server side is FabricEventForwarder's): after the entity was added,
        // so not cancellable (no listener cancels)
        ClientEntityEvents.ENTITY_LOAD.register((e, level) -> CommonEvents.ENTITY_JOIN_LEVEL.invoker().onJoin(e, level));

        // NeoForge: FMLClientSetupEvent.enqueueWork. Fabric has no later setup stage before the first resource load;
        // the client config is loaded by now (Forge Config API Port loads it when it is registered).
        ClientEvents.CLIENT_SETUP.invoker().onSetup();
    }

    /**
     * Our HUD layers above the whole vanilla HUD (NeoForge: {@code RegisterGuiLayersEvent#registerAboveAll}), in
     * registration order, each {@link LayeredDraw#Z_SEPARATION} above the last as in NeoForge's layer manager. Fabric's
     * callback fires after vanilla's layers, whose depth values reach far above ours, so the depth buffer is cleared
     * first (vanilla clears it right after the HUD anyway). Drawn while the HUD is hidden too, as on NeoForge: layers
     * check {@code hideGui} themselves.
     */
    private static void renderHudLayers(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.DeltaTracker delta) {
        List<ClientEvents.HudLayer> layers = ClientEvents.hudLayers();
        if (layers.isEmpty()) return;
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        graphics.pose().pushPose();
        for (ClientEvents.HudLayer layer : layers) {
            layer.layer().render(graphics, delta);
            graphics.pose().translate(0.0F, 0.0F, LayeredDraw.Z_SEPARATION);
        }
        graphics.pose().popPose();
    }

    /** Puts every block of ours whose model names a {@code render_type} into that layer ({@link FabricRenderLayers}). */
    private static void registerRenderLayers() {
        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(Constants.MOD_ID);
        if (mod.isEmpty()) return;
        int count = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals(Constants.MOD_ID)) continue;
            FabricRenderLayers.Result result = FabricRenderLayers.layerOf(id.getNamespace(), id.getPath(),
                    path -> FabricRenderLayers.readJson(path, p -> open(mod.get(), p)));
            result.problems().forEach(p -> Constants.LOG.warn("Render layer of {}: {}", id, p));
            if (result.layer().isEmpty()) continue;
            BlockRenderLayerMap.INSTANCE.putBlock(block, switch (result.layer().get()) {
                case CUTOUT -> RenderType.cutout();
                case CUTOUT_MIPPED -> RenderType.cutoutMipped();
                case TRANSLUCENT -> RenderType.translucent();
                case TRIPWIRE -> RenderType.tripwire();
                case SOLID -> RenderType.solid();
            });
            count++;
        }
        Constants.LOG.debug("Fabric block render layers from model render_type: {} block(s)", count);
    }

    private static InputStream open(ModContainer mod, String path) {
        Optional<Path> file = mod.findPath(path);
        if (file.isEmpty()) return null;
        try {
            return Files.newInputStream(file.get());
        } catch (IOException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Entity> void registerEntity(ClientEvents.EntityRenderer<E> r) {
        EntityRendererRegistry.register((EntityType<E>) r.type().get(), r.provider());
    }

    @SuppressWarnings("unchecked")
    private static <B extends BlockEntity> void registerBlockEntity(ClientEvents.BlockEntityRenderer<B> r) {
        // widened for mods by Fabric's transitive access wideners
        BlockEntityRenderers.register((BlockEntityType<B>) r.type().get(), r.provider());
    }
}
