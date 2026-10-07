package com.richardsenger.piratesnships.platform.event;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.Music;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import org.jetbrains.annotations.Nullable;

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
 * ClientEvents.ITEM_TOOLTIP.register((stack, context, flag, player, lines) -> lines.addAll(MyTooltips.lines(stack)));
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

    /**
     * An item tooltip was built (client thread), after vanilla and the item itself added their lines, so other code
     * can add lines too. {@code lines} is the mutable tooltip: index 0 is the item's name, later lines may include
     * the advanced-tooltip id and component count at the end. {@code player} is the client player, or {@code null}
     * while the tooltip is built without a world (e.g. search tree indexing at startup): don't assume a level.
     */
    public static final Event<ItemTooltip> ITEM_TOOLTIP = Event.create(ls -> (st, ctx, f, p, lines) -> ls.forEach(l -> l.onTooltip(st, ctx, f, p, lines)));

    /**
     * The music manager asks which situational music should play (client thread, every client tick, also in menus).
     * {@code vanillaChoice} is what vanilla (and loader listeners before us) picked. Return a {@link Music} to play
     * instead, or {@code null} to leave the choice alone; the first non-null result wins. A replacement waits for the
     * playing track to end unless it {@linkplain Music#replaceCurrentMusic() replaces the current music}.
     */
    public static final Event<SelectMusic> SELECT_MUSIC = Event.create(ls -> vanilla -> {
        for (SelectMusic l : ls) {
            Music m = l.select(vanilla);
            if (m != null) return m;
        }
        return null;
    });

    /**
     * A streamed sound (music, records, long sounds) started playing on its audio channel. Fired on the <b>sound
     * thread</b>, after vanilla set the channel's pitch and volume, so a listener may adjust the channel (e.g.
     * {@code channel.setVolume}). Don't touch the level or other game state here, and keep it short. Vanilla resets the
     * volume when the player moves that category's slider.
     */
    public static final Event<SoundStreamStarted> SOUND_STREAM_STARTED = Event.create(ls -> (sound, channel) -> ls.forEach(l -> l.onStarted(sound, channel)));

    /**
     * An attack, use or pick-block input is about to run (client thread, inside the client tick's key handling): a
     * new click, the repeated use while the use key is held, or the continued attack on a block while the attack key
     * is held. Return {@link InteractionKeyResult#CANCEL} to stop vanilla's action (entity attack, block breaking,
     * item or block use, pick block) and its hand swing; the first non-{@code PASS} result wins. For {@code USE} it is
     * fired per hand, main hand first; a cancel stops the off hand too. Fired from NeoForge's
     * {@code InputEvent.InteractionKeyMappingTriggered}. Used by {@code combat.melee.client.MeleeInput}.
     */
    public static final Event<InteractionKey> INTERACTION_KEY = Event.create(ls -> (mc, input, hand) -> {
        for (InteractionKey l : ls) {
            InteractionKeyResult r = l.onInteraction(mc, input, hand);
            if (r != InteractionKeyResult.PASS) return r;
        }
        return InteractionKeyResult.PASS;
    });

    /** Which interaction key fired {@link #INTERACTION_KEY}. */
    public enum InteractionInput { ATTACK, USE, PICK_BLOCK }

    /** Result of an {@link #INTERACTION_KEY} listener. */
    public enum InteractionKeyResult {
        /** Let later listeners and vanilla handle the input. */
        PASS,
        /** Cancel vanilla's action and its hand swing. */
        CANCEL
    }

    @FunctionalInterface public interface InteractionKey { InteractionKeyResult onInteraction(Minecraft minecraft, InteractionInput input, InteractionHand hand); }

    @FunctionalInterface public interface SelectMusic { @Nullable Music select(@Nullable Music vanillaChoice); }

    @FunctionalInterface public interface SoundStreamStarted { void onStarted(SoundInstance sound, Channel channel); }

    @FunctionalInterface public interface ClientTick { void onTick(Minecraft minecraft); }

    @FunctionalInterface public interface ClientDisconnect { void onDisconnect(Minecraft minecraft); }

    @FunctionalInterface public interface ItemTooltip { void onTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, @Nullable Player player, List<Component> lines); }

    /** A HUD layer, drawn above the vanilla HUD in registration order. */
    public record HudLayer(ResourceLocation id, LayeredDraw.Layer layer) { }

    public record EntityRenderer<E extends Entity>(Supplier<? extends EntityType<? extends E>> type, EntityRendererProvider<E> provider) { }

    public record BlockEntityRenderer<B extends BlockEntity>(Supplier<? extends BlockEntityType<? extends B>> type, BlockEntityRendererProvider<B> provider) { }

    public record ModelLayer(ModelLayerLocation location, Supplier<LayerDefinition> definition) { }

    /** A block colour handler for some blocks (faces with a {@code tintindex} in their model). */
    public record BlockColorHandler(BlockColor color, List<Supplier<? extends Block>> blocks) { }

    private static final List<KeyMapping> KEY_MAPPINGS = new ArrayList<>();
    private static final List<HudLayer> HUD_LAYERS = new ArrayList<>();
    private static final List<EntityRenderer<?>> ENTITY_RENDERERS = new ArrayList<>();
    private static final List<BlockEntityRenderer<?>> BLOCK_ENTITY_RENDERERS = new ArrayList<>();
    private static final List<ModelLayer> MODEL_LAYERS = new ArrayList<>();
    private static final List<BlockColorHandler> BLOCK_COLORS = new ArrayList<>();

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

    /**
     * A block colour handler (vanilla {@link BlockColor}): model faces with a {@code tintindex} are multiplied by
     * {@code color.getColor(state, level, pos, tintIndex)} (RGB, alpha ignored). Called while a chunk section is meshed,
     * possibly off the client thread; {@code level} is then the meshing region, whose {@code getBlockEntity} sees the
     * client's block entities (also on Sable ships). {@code level} and {@code pos} are null for items and particles.
     * The colour is baked into the mesh: after the data it reads changes, re-mesh the block (client
     * {@code level.sendBlockUpdated(pos, state, state, Block.UPDATE_IMMEDIATE)}). Forwarded from NeoForge's
     * {@code RegisterColorHandlersEvent.Block}. Example: {@code ship.decor.client.ShipDecorClient} (banner flags).
     */
    @SafeVarargs
    public static synchronized void registerBlockColor(BlockColor color, Supplier<? extends Block>... blocks) {
        BLOCK_COLORS.add(new BlockColorHandler(color, List.of(blocks)));
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
    public static synchronized List<BlockColorHandler> blockColors() { return List.copyOf(BLOCK_COLORS); }
}
