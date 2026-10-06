package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

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

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, e -> ClientEvents.CLIENT_TICK_START.invoker().onTick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> ClientEvents.CLIENT_TICK_END.invoker().onTick(Minecraft.getInstance()));
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
