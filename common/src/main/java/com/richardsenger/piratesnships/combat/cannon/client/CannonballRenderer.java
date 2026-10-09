package com.richardsenger.piratesnships.combat.cannon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.cannon.CannonContent;
import com.richardsenger.piratesnships.combat.cannon.CannonballEntity;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Draws a {@link CannonballEntity} by what it carries (ART15, docs/design.md §8.2). A cannonball (and the swivel gun's
 * shot) stays vanilla's thrown item at {@code ballScale}. Chain shot is its 3D item model ({@code item/chain_shot},
 * chain along the model's x axis) laid along the flight and tumbling end over end about the axis across it
 * ({@link ShotTumble}). A grapeshot pellet is the small hand-made ball {@code block/grapeshot_pellet} at
 * {@link #PELLET_SCALE}, not the cannonball's size. The kind is read from the synced item ({@link CannonballEntity#setKind}
 * puts the chain shot item or an iron nugget into it), so nothing new is synced.
 */
public class CannonballRenderer extends ThrownItemRenderer<CannonballEntity> {

    /** The pellet model, a 3 px iron ball (art/models/grapeshot_pellet.bbmodel); registered as a stand-alone model. */
    static final ResourceLocation PELLET_MODEL = Constants.id("block/grapeshot_pellet");
    /** Chain shot at model size: balls 4.6 px (0.29 blocks) across, about 1.05 blocks from ball to ball. */
    static final float CHAIN_SCALE = 1.0f;
    /** A pellet 3 px * 0.8 = 2.4 px (0.15 blocks) across; the cannonball is about 0.38 blocks. */
    static final float PELLET_SCALE = 0.8f;
    /** Vanilla's {@code ThrownItemRenderer}: hide a fresh shot right in front of the camera. */
    private static final double NEAR_CAMERA_SQ = 12.25;

    private final ItemRenderer items;

    public CannonballRenderer(EntityRendererProvider.Context context, float ballScale) {
        super(context, ballScale, false);
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(CannonballEntity shot, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        ItemStack stack = shot.getItem();
        boolean chain = stack.is(CannonContent.CHAIN_SHOT.get());
        boolean pellet = stack.is(Items.IRON_NUGGET);
        if (!chain && !pellet) {
            super.render(shot, entityYaw, partialTick, pose, buffers, light);
            return;
        }
        if (shot.tickCount < 2 && entityRenderDispatcher.camera.getEntity().distanceToSqr(shot) < NEAR_CAMERA_SQ) return;
        pose.pushPose();
        BakedModel model;
        if (chain) {
            // the model's x axis along the flight (the arrow's convention), then end over end about z, across it
            pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, shot.yRotO, shot.getYRot()) - 90.0f));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, shot.xRotO, shot.getXRot())));
            pose.mulPose(Axis.ZP.rotationDegrees(ShotTumble.angle(shot.tickCount, partialTick)));
            pose.scale(CHAIN_SCALE, CHAIN_SCALE, CHAIN_SCALE);
            model = items.getModel(stack, shot.level(), null, shot.getId());
        } else {
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            pose.scale(PELLET_SCALE, PELLET_SCALE, PELLET_SCALE);
            model = ClientEvents.additionalModel(PELLET_MODEL);
        }
        items.render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, OverlayTexture.NO_OVERLAY, model);
        pose.popPose();
    }
}
