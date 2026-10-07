package com.richardsenger.piratesnships.combat.firearms.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.combat.firearms.FirearmContent;
import com.richardsenger.piratesnships.combat.firearms.FirearmItem;
import com.richardsenger.piratesnships.combat.firearms.FirearmKind;
import com.richardsenger.piratesnships.combat.firearms.FirearmRules;
import com.richardsenger.piratesnships.combat.firearms.FirearmsConfig;
import com.richardsenger.piratesnships.combat.firearms.client.anim.FirearmAnimationDriver;
import com.richardsenger.piratesnships.combat.firearms.client.anim.FirearmAnimationsSetup;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Client setup of the firearms (physical client only, from {@code FirearmsModule.initClient()}). */
public final class FirearmsClient {

    /** The lead ball is drawn as the lead shot item sprite at this scale. */
    private static final float BALL_SCALE = 0.35f;

    /** Item model property: 1 on a loaded gun, 0 otherwise; {@code pistol.json}/{@code musket.json} override on it (P6). */
    public static final ResourceLocation LOADED_PROPERTY = Constants.id("loaded");

    private FirearmsClient() {
    }

    public static void init() {
        ClientEvents.registerEntityRenderer(FirearmContent.LEAD_BALL, ctx -> new ThrownItemRenderer<>(ctx, BALL_SCALE, false));
        ClientEvents.COMPUTE_FOV.register(FirearmsClient::aimFov);
        // aim and reload poses (P3); the cannon's fuse animation is not part of this
        ClientEvents.CLIENT_SETUP.register(FirearmAnimationsSetup::onClientSetup);
        ClientEvents.CLIENT_TICK_END.register(FirearmAnimationDriver::onClientTickEnd);
        // sneaking lowers an aimed gun without firing (P5)
        ClientEvents.CLIENT_TICK_END.register(FirearmLowering::onClientTickEnd);
        // the loading progress on the item bar (P5)
        FirearmClientState.set(new LocalFirearmClientState());
        // the loaded gun shows its cocked-hammer model (P6)
        ClientEvents.CLIENT_SETUP.register(FirearmsClient::registerItemProperties);
    }

    private static void registerItemProperties() {
        ClampedItemPropertyFunction loaded = (stack, level, entity, seed) -> FirearmContent.isLoaded(stack) ? 1.0f : 0.0f;
        ItemProperties.register(CombatContent.PISTOL.get(), LOADED_PROPERTY, loaded);
        ItemProperties.register(CombatContent.MUSKET.get(), LOADED_PROPERTY, loaded);
    }

    /** Zooms in while the player aims a loaded musket (an aim session, not the hold after loading). */
    private static float aimFov(Player player, float fov) {
        if (!player.isUsingItem() || !FirearmRules.isAimSession(player.getUseItemRemainingTicks())) return fov;
        ItemStack stack = player.getUseItem();
        if (!(stack.getItem() instanceof FirearmItem gun) || gun.kind() != FirearmKind.MUSKET || !FirearmContent.isLoaded(stack)) return fov;
        return FirearmRules.zoomedFov(fov, FirearmsConfig.MUSKET_ZOOM.get());
    }
}
