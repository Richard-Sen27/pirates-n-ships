package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.sounds.Music;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.function.Supplier;

/** Client-only wiring: forwards {@link ClientEvents} registrations and ticks, enables NeoForge's config screen. */
public final class NeoForgeClientSetup {

    private NeoForgeClientSetup() {
    }

    public static void attach(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(RegisterKeyMappingsEvent.class, e -> ClientEvents.keyMappings().forEach(e::register));
        modBus.addListener(RegisterGuiLayersEvent.class, e -> ClientEvents.hudLayers().forEach(l -> e.registerAboveAll(l.id(), l.layer())));
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, e -> {
            ClientEvents.entityRenderers().forEach(r -> registerEntity(e, r));
            ClientEvents.blockEntityRenderers().forEach(r -> registerBlockEntity(e, r));
        });
        modBus.addListener(EntityRenderersEvent.RegisterLayerDefinitions.class,
                e -> ClientEvents.modelLayers().forEach(l -> e.registerLayerDefinition(l.location(), l.definition())));
        modBus.addListener(RegisterColorHandlersEvent.Block.class, e -> ClientEvents.blockColors().forEach(c ->
                e.register(c.color(), c.blocks().stream().map(Supplier::get).toArray(Block[]::new))));

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, e -> ClientEvents.CLIENT_TICK_START.invoker().onTick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> ClientEvents.CLIENT_TICK_END.invoker().onTick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, e -> ClientEvents.ITEM_TOOLTIP.invoker().onTooltip(e.getItemStack(), e.getContext(), e.getFlags(), e.getEntity(), e.getToolTip()));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> ClientEvents.CLIENT_DISCONNECT.invoker().onDisconnect(Minecraft.getInstance()));
        // LOW: situational music (ours depends on the ship and biome) should run after broad biome/dimension listeners
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, SelectMusicEvent.class, e -> {
            Music music = ClientEvents.SELECT_MUSIC.invoker().select(e.getMusic());
            if (music != null) e.setMusic(music);
        });
        NeoForge.EVENT_BUS.addListener(InputEvent.InteractionKeyMappingTriggered.class, e -> {
            ClientEvents.InteractionInput input = e.isAttack() ? ClientEvents.InteractionInput.ATTACK
                    : e.isUseItem() ? ClientEvents.InteractionInput.USE : ClientEvents.InteractionInput.PICK_BLOCK;
            if (ClientEvents.INTERACTION_KEY.invoker().onInteraction(Minecraft.getInstance(), input, e.getHand())
                    == ClientEvents.InteractionKeyResult.CANCEL) {
                e.setCanceled(true);
                e.setSwingHand(false);
            }
        });
        // fired on the sound thread, inside the channel's executor
        NeoForge.EVENT_BUS.addListener(PlayStreamingSourceEvent.class, e -> ClientEvents.SOUND_STREAM_STARTED.invoker().onStarted(e.getSound(), e.getChannel()));
    }

    @SuppressWarnings("unchecked")
    private static <E extends Entity> void registerEntity(EntityRenderersEvent.RegisterRenderers e, ClientEvents.EntityRenderer<E> r) {
        e.registerEntityRenderer((EntityType<E>) r.type().get(), r.provider());
    }

    @SuppressWarnings("unchecked")
    private static <B extends BlockEntity> void registerBlockEntity(EntityRenderersEvent.RegisterRenderers e, ClientEvents.BlockEntityRenderer<B> r) {
        e.registerBlockEntityRenderer((BlockEntityType<B>) r.type().get(), r.provider());
    }
}
