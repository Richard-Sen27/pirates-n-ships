package com.richardsenger.piratesnships.combat.grapple.client;

import com.richardsenger.piratesnships.sailing.client.RopeLineRenderer;
import com.richardsenger.piratesnships.combat.grapple.GrappleService;
import com.richardsenger.piratesnships.combat.grapple.BoardRopePayload;
import com.richardsenger.piratesnships.combat.grapple.GrappleConfig;
import com.richardsenger.piratesnships.combat.grapple.GrappleContent;
import com.richardsenger.piratesnships.combat.grapple.GrappleLaunch;
import com.richardsenger.piratesnships.combat.grapple.GrapplingHookItem;
import com.richardsenger.piratesnships.combat.grapple.RopeSlide;
import com.richardsenger.piratesnships.combat.grapple.RopeSwingService;
import com.richardsenger.piratesnships.combat.grapple.GrapplingHookEntity;
import com.richardsenger.piratesnships.combat.grapple.ReleaseHookPayload;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.combat.grapple.client.anim.RopeSlidePoses;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;
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
        // the rope lines (RP1) whose drawing end is a mooring ring
        ClientEvents.registerBlockEntityRenderer(GrappleContent.MOORING_RING_BLOCK_ENTITY, RopeLineRenderer::new);
        ClientEvents.INTERACTION_KEY.register(GrappleClient::onInteraction);
        // GR3: the musket's hook look
        ClientEvents.CLIENT_SETUP.register(GrappleLaunchClient::registerItemProperties);
        ClientEvents.CLIENT_SETUP.register(RopeSlidePoses::onClientSetup);
        ClientEvents.CLIENT_TICK_END.register(RopeSlidePoses::onClientTickEnd);
        // GR7: the local player hangs and swings on their own hand-held rope (the client moves its own player)
        CommonEvents.PLAYER_TICK_END.register(ClientRopeSwing::onPlayerTick);
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
        ClientEvents.InteractionKeyResult own = pullOwnRope(mc);
        if (own != null) {
            return own;
        }
        return grabRope(mc) ? ClientEvents.InteractionKeyResult.CANCEL : ClientEvents.InteractionKeyResult.PASS;
    }

    /**
     * GR7: use (not sneaking) with the own latched rope in hand (an empty main hand or a hook in it): one pull, sent to
     * the server ({@link BoardRopePayload}, which pulls without a pick, {@code RopeSwingService#pull}) and predicted at
     * once ({@link ClientRopeSwing#predictPull}), when the player hangs on the rope, looks at it, or looks at nothing
     * with the hook high enough above to climb to. During the hook item's cooldown ({@code pull_cooldown_ticks}) the use
     * is swallowed, so a click or a held button cannot pull faster nor throw another hook. A use aimed at a block or an
     * entity on the ground is left to vanilla. Null when the player has no such rope (the tied-rope grab follows).
     */
    private static ClientEvents.@Nullable InteractionKeyResult pullOwnRope(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player.isPassenger() || player.isSpectator() || !GrappleConfig.ENABLED.get() || !GrappleConfig.SWING.get()) {
            return null;
        }
        GrapplingHookEntity hook = ClientRopeSwing.ownHook(player);
        GrappleLaunch.Held main = GrapplingHookItem.heldOf(player.getMainHandItem());
        if (hook == null || RopeSlide.grab(main, hook.tickCount, GrappleConfig.GRAB_COOLDOWN_TICKS.get()) != RopeSlide.Grab.OK) {
            return null;
        }
        Item item = CombatContent.GRAPPLING_HOOK.get();
        if (player.getCooldowns().isOnCooldown(item)) {
            return ClientEvents.InteractionKeyResult.CANCEL;
        }
        Vec3 eye = player.getEyePosition(1.0f);
        ClientRopes.RopeHit hit = ClientRopes.pick(mc.level, eye, player.getViewVector(1.0f), player);
        boolean looksAtRope = hit != null && hit.hook() == hook && !inFront(mc, eye, hit.pick().eyeDistance());
        double hookY = ClientRopes.hookPos(hook, 1.0f).y;
        boolean hanging = ClientRopeSwing.hanging(hook) && hookY > RopeSwingService.hand(player).y;
        // GR6's climb rule, as the server checks it: the hook high enough above the feet, or above a hanging player
        boolean climb = hanging || hookY - player.getY() >= GrappleConfig.CLIMB_MIN_RISE.get();
        boolean intoTheAir = (mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS) && climb;
        if (!hanging && !looksAtRope && !intoTheAir) {
            return null;
        }
        Services.NETWORK.sendToServer(new BoardRopePayload(hook.getId())); // no climb: the server shows the tie-off hint
        if (climb) {
            ClientRopeSwing.predictPull(hook, player);
            int cooldown = GrappleConfig.PULL_COOLDOWN_TICKS.get();
            if (cooldown > 0) {
                player.getCooldowns().addCooldown(item, cooldown); // the server's own cooldown follows by packet
            }
        }
        return ClientEvents.InteractionKeyResult.CANCEL;
    }

    /**
     * Use (not sneaking) while looking at a latched rope within reach grabs it (GR2): asks the server to let the player
     * slide and cancels vanilla's use. A block or entity in front of the rope gets the use instead. GR4: only with an
     * empty main hand or a grappling hook in it, and not within {@code grab_cooldown_ticks} of the hook leaving
     * ({@link RopeSlide#grab}, the server checks the same): with the musket in hand the use always goes to the musket,
     * so re-aiming right after a shot (the rope runs from the hand along the look ray) no longer grabs the rope.
     */
    private static boolean grabRope(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player.isPassenger() || player.isSpectator() || !GrappleConfig.ENABLED.get() || !GrappleConfig.SLIDE_ENABLED.get()) {
            return false;
        }
        GrappleLaunch.Held main = GrapplingHookItem.heldOf(player.getMainHandItem());
        int cooldown = GrappleConfig.GRAB_COOLDOWN_TICKS.get();
        if (RopeSlide.grab(main, Integer.MAX_VALUE, cooldown) != RopeSlide.Grab.OK) {
            return false; // the item in hand keeps its own use
        }
        Vec3 eye = player.getEyePosition(1.0f);
        ClientRopes.RopeHit hit = ClientRopes.pick(mc.level, eye, player.getViewVector(1.0f), player);
        if (hit == null || RopeSlide.grab(main, hit.hook().tickCount, cooldown) != RopeSlide.Grab.OK
                || inFront(mc, eye, hit.pick().eyeDistance())) {
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
     * The use aims at a mooring ring or a cleat: it ties the rope there instead (GR1, RP1, {@code MooringRingBlock#useWithoutItem}),
     * so no release is sent. Ship blocks come back from the pick in plot coordinates, where the client has them too.
     */
    private static boolean aimsAtRing(Minecraft mc) {
        return mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && GrappleService.isGrappleAnchor(mc.level, hit.getBlockPos());
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
