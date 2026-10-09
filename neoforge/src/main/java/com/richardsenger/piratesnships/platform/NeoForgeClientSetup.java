package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.sounds.Music;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.function.Supplier;

/** Client-only wiring: forwards {@link ClientEvents} registrations and ticks, enables NeoForge's config screen. */
public final class NeoForgeClientSetup {

    private NeoForgeClientSetup() {
    }

    /** ITC1: our item colour handlers, each for its items (resolved now, after registration). */
    static void registerItemColors(RegisterColorHandlersEvent.Item e) {
        ClientEvents.itemColors().forEach(c -> e.register(c.color(), c.items().stream().map(Supplier::get).toArray(ItemLike[]::new)));
    }

    /**
     * Our HUD layers by their {@link ClientEvents.HudOrder}: above all in registration order, or right below the chat
     * (HUD4; each one goes directly under {@code CHAT}, so they also keep their registration order).
     */
    static void registerHudLayers(RegisterGuiLayersEvent e) {
        for (ClientEvents.HudLayer l : ClientEvents.hudLayers()) {
            switch (l.order()) {
                case ABOVE_ALL -> e.registerAboveAll(l.id(), l.layer());
                case BELOW_CHAT -> e.registerBelow(VanillaGuiLayers.CHAT, l.id(), l.layer());
            }
        }
    }

    public static void attach(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // enqueueWork: on the main thread after parallel setup (PAL requires its layer factories to be registered here)
        modBus.addListener(FMLClientSetupEvent.class, e -> e.enqueueWork(() -> ClientEvents.CLIENT_SETUP.invoker().onSetup()));
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> ClientEvents.keyMappings().forEach(e::register));
        modBus.addListener(RegisterGuiLayersEvent.class, NeoForgeClientSetup::registerHudLayers);
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, e -> {
            ClientEvents.entityRenderers().forEach(r -> registerEntity(e, r));
            ClientEvents.blockEntityRenderers().forEach(r -> registerBlockEntity(e, r));
        });
        modBus.addListener(EntityRenderersEvent.RegisterLayerDefinitions.class,
                e -> ClientEvents.modelLayers().forEach(l -> e.registerLayerDefinition(l.location(), l.definition())));
        ClientEvents.setAdditionalModelKey(ModelResourceLocation::standalone);
        modBus.addListener(ModelEvent.RegisterAdditional.class,
                e -> ClientEvents.additionalModels().forEach(id -> e.register(ModelResourceLocation.standalone(id))));
        modBus.addListener(RegisterClientExtensionsEvent.class, e -> NeoForgeArmorModels.register(ClientEvents.armorModels(), e::registerItem));
        modBus.addListener(RegisterColorHandlersEvent.Block.class, e -> ClientEvents.blockColors().forEach(c ->
                e.register(c.color(), c.blocks().stream().map(Supplier::get).toArray(Block[]::new))));
        modBus.addListener(RegisterColorHandlersEvent.Item.class, NeoForgeClientSetup::registerItemColors);

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, e -> ClientEvents.CLIENT_TICK_START.invoker().onTick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> ClientEvents.CLIENT_TICK_END.invoker().onTick(Minecraft.getInstance()));
        // after the frame's mouse movement turned the player, before the frame is drawn
        NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Pre.class, e -> ClientEvents.RENDER_FRAME_PRE.invoker().onTick(Minecraft.getInstance()));
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
        NeoForge.EVENT_BUS.addListener(ComputeFovModifierEvent.class,
                e -> e.setNewFovModifier(ClientEvents.COMPUTE_FOV.invoker().modify(e.getPlayer(), e.getNewFovModifier())));
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles.class,
                e -> e.setRoll(ClientEvents.COMPUTE_CAMERA_ROLL.invoker().modify((float) e.getPartialTick(), e.getRoll())));
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, e -> {
            if (e.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                // the partial tick the level renderer itself uses (LevelRenderer#renderLevel)
                ClientEvents.RENDER_AFTER_TRANSLUCENT.invoker().onRender(e.getCamera(), e.getFrustum(),
                        e.getPartialTick().getGameTimeDeltaPartialTick(false));
            }
        });
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
