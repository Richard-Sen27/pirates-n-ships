package com.richardsenger.piratesnships.combat.grapple.client;

import com.richardsenger.piratesnships.combat.grapple.BoardRopePayload;
import com.richardsenger.piratesnships.combat.grapple.GrappleConfig;
import com.richardsenger.piratesnships.combat.grapple.GrappleContent;
import com.richardsenger.piratesnships.combat.grapple.GrapplingHookEntity;
import com.richardsenger.piratesnships.combat.grapple.MooringRingBlock;
import com.richardsenger.piratesnships.combat.grapple.ReleaseHookPayload;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.combat.grapple.client.anim.RopeSlidePoses;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.ship.sable.ClientShipPoses;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Client setup of the grappling hook (physical client only, from {@code GrappleModule.initClient()}). */
public final class GrappleClient {

    private GrappleClient() {
    }

    public static void init() {
        ClientEvents.registerEntityRenderer(GrappleContent.HOOK, GrapplingHookRenderer::new);
        ClientEvents.registerEntityRenderer(GrappleContent.ROPE_RIDER, NoopRenderer::new);
        ClientEvents.INTERACTION_KEY.register(GrappleClient::onInteraction);
        // GR3: the crossbow's draw and shot of the hook, the musket's hook look
        ClientEvents.INTERACTION_KEY.register(GrappleLaunchClient::onInteraction);
        ClientEvents.CLIENT_SETUP.register(GrappleLaunchClient::registerItemProperties);
        ClientEvents.CLIENT_SETUP.register(RopeSlidePoses::onClientSetup);
        ClientEvents.CLIENT_TICK_END.register(RopeSlidePoses::onClientTickEnd);
    }

    /**
     * Sneak + use with an empty main hand while one of our hooks is out asks the server to release it. Vanilla's own
     * action still runs (PASS), so sneak-using a block with an empty hand works as before.
     */
    private static ClientEvents.InteractionKeyResult onInteraction(Minecraft mc, ClientEvents.InteractionInput input, InteractionHand hand) {
        if (input != ClientEvents.InteractionInput.USE || hand != InteractionHand.MAIN_HAND || mc.player == null || mc.level == null) {
            return ClientEvents.InteractionKeyResult.PASS;
        }
        if (mc.player.isShiftKeyDown()) {
            if (mc.player.getMainHandItem().isEmpty() && hasHookOut(mc) && !aimsAtRing(mc)) {
                Services.NETWORK.sendToServer(ReleaseHookPayload.INSTANCE);
            }
            return ClientEvents.InteractionKeyResult.PASS;
        }
        return grabRope(mc) ? ClientEvents.InteractionKeyResult.CANCEL : ClientEvents.InteractionKeyResult.PASS;
    }

    /**
     * Use (not sneaking) while looking at a latched rope within reach grabs it (GR2): asks the server to let the player
     * slide and cancels vanilla's use. A block or entity in front of the rope gets the use instead.
     */
    private static boolean grabRope(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player.isPassenger() || player.isSpectator() || !GrappleConfig.ENABLED.get() || !GrappleConfig.SLIDE_ENABLED.get()) {
            return false;
        }
        Vec3 eye = player.getEyePosition(1.0f);
        ClientRopes.RopeHit hit = ClientRopes.pick(mc.level, eye, player.getViewVector(1.0f));
        if (hit == null || inFront(mc, eye, hit.pick().eyeDistance())) {
            return false;
        }
        Services.NETWORK.sendToServer(new BoardRopePayload(hit.hook().getId()));
        return true;
    }

    /** The crosshair's block or entity is nearer than {@code ropeDistance} (ship blocks through their render pose). */
    private static boolean inFront(Minecraft mc, Vec3 eye, double ropeDistance) {
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return false;
        }
        Vec3 at = hit.getLocation();
        if (hit instanceof BlockHitResult) {
            Vec3 onShip = ClientShipPoses.toWorld(mc.level, at, 1.0f); // ship blocks are picked in plot coordinates
            if (onShip != null) {
                at = onShip;
            }
        }
        return eye.distanceTo(at) < ropeDistance;
    }

    /**
     * The use aims at a mooring ring: it ties the rope there instead (GR1, {@code MooringRingBlock#useWithoutItem}),
     * so no release is sent. Ship blocks come back from the pick in plot coordinates, where the client has them too.
     */
    private static boolean aimsAtRing(Minecraft mc) {
        return mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && MooringRingBlock.isRing(mc.level, hit.getBlockPos());
    }

    private static boolean hasHookOut(Minecraft mc) {
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof GrapplingHookEntity hook && hook.getOwner() == mc.player) {
                return true;
            }
        }
        return false;
    }
}
