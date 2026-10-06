package com.richardsenger.piratesnships.platform.event;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client-only events and registrations. Only touch this class from client code: a module's {@code initClient()}
 * should delegate to a separate client class (e.g. {@code ship.client.ShipClient.init()}).
 *
 * <pre>{@code
 * ClientEvents.CLIENT_TICK_END.register(mc -> WindHud.tick(mc));
 * ClientEvents.CLIENT_DISCONNECT.register(mc -> ClientWind.reset());
 * ClientEvents.registerHudLayer(Constants.id("wind"), WindHud::render);
 * ClientEvents.registerEntityRenderer(ShipEntities.SHARK, SharkRenderer::new);
 * }</pre>
 */
public final class ClientEvents {

    private ClientEvents() {
    }

    public static final Event<ClientTick> CLIENT_TICK_START = Event.create(ls -> mc -> ls.forEach(l -> l.onTick(mc)));
    public static final Event<ClientTick> CLIENT_TICK_END = Event.create(ls -> mc -> ls.forEach(l -> l.onTick(mc)));

    /**
     * Fired on the client thread when the client leaves a world or server (disconnect, quit to title, kick, and also
     * before a new single-player world starts). Clear client-side caches of server data here (synced stores, wind,
     * ...). The level and player may already be gone; do not rely on them. May fire more than once per session.
     */
    public static final Event<ClientDisconnect> CLIENT_DISCONNECT = Event.create(ls -> mc -> ls.forEach(l -> l.onDisconnect(mc)));

    @FunctionalInterface public interface ClientTick { void onTick(Minecraft minecraft); }

    @FunctionalInterface public interface ClientDisconnect { void onDisconnect(Minecraft minecraft); }

    /** A HUD layer, drawn above the vanilla HUD in registration order. */
    public record HudLayer(ResourceLocation id, LayeredDraw.Layer layer) { }

    public record EntityRenderer<E extends Entity>(Supplier<? extends EntityType<? extends E>> type, EntityRendererProvider<E> provider) { }

    public record BlockEntityRenderer<B extends BlockEntity>(Supplier<? extends BlockEntityType<? extends B>> type, BlockEntityRendererProvider<B> provider) { }

    public record ModelLayer(ModelLayerLocation location, Supplier<LayerDefinition> definition) { }

    private static final List<KeyMapping> KEY_MAPPINGS = new ArrayList<>();
    private static final List<HudLayer> HUD_LAYERS = new ArrayList<>();
    private static final List<EntityRenderer<?>> ENTITY_RENDERERS = new ArrayList<>();
    private static final List<BlockEntityRenderer<?>> BLOCK_ENTITY_RENDERERS = new ArrayList<>();
    private static final List<ModelLayer> MODEL_LAYERS = new ArrayList<>();

    public static synchronized void registerKeyMapping(KeyMapping mapping) {
        KEY_MAPPINGS.add(mapping);
    }

    public static synchronized void registerHudLayer(ResourceLocation id, LayeredDraw.Layer layer) {
        HUD_LAYERS.add(new HudLayer(id, layer));
    }

    public static synchronized <E extends Entity> void registerEntityRenderer(Supplier<? extends EntityType<? extends E>> type, EntityRendererProvider<E> provider) {
        ENTITY_RENDERERS.add(new EntityRenderer<>(type, provider));
    }

    public static synchronized <B extends BlockEntity> void registerBlockEntityRenderer(Supplier<? extends BlockEntityType<? extends B>> type, BlockEntityRendererProvider<B> provider) {
        BLOCK_ENTITY_RENDERERS.add(new BlockEntityRenderer<>(type, provider));
    }

    public static synchronized void registerModelLayer(ModelLayerLocation location, Supplier<LayerDefinition> definition) {
        MODEL_LAYERS.add(new ModelLayer(location, definition));
    }

    // Read by the loader module when its registration events fire.

    public static synchronized List<KeyMapping> keyMappings() { return List.copyOf(KEY_MAPPINGS); }
    public static synchronized List<HudLayer> hudLayers() { return List.copyOf(HUD_LAYERS); }
    public static synchronized List<EntityRenderer<?>> entityRenderers() { return List.copyOf(ENTITY_RENDERERS); }
    public static synchronized List<BlockEntityRenderer<?>> blockEntityRenderers() { return List.copyOf(BLOCK_ENTITY_RENDERERS); }
    public static synchronized List<ModelLayer> modelLayers() { return List.copyOf(MODEL_LAYERS); }
}
