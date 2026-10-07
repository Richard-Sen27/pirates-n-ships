package com.richardsenger.piratesnships.sailing.helm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.sailing.helm.HelmBlockEntity;
import com.richardsenger.piratesnships.ship.assembly.HelmBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Draws the helm's wheel (HELM1) from its block entity: the hand-made stand-alone {@code block/helm_wheel} model (spokes, rim,
 * handles and hub of {@code art/models/helm.bbmodel}'s {@code wheel} group), turned by the wheel angle about its axle,
 * which runs along z through (8, 13) px of the model built for {@code facing=north}; the block model {@code block/helm}
 * is only the pedestal. While the local player holds this wheel the locally predicted angle is drawn (no wait for the
 * server), otherwise the synced angle, eased over one tick. Positive angles turn the wheel clockwise as the helmsman
 * (standing on the {@code FACING} side, looking the other way) sees it.
 *
 * <p>Works on ships like {@code SwivelGunRenderer}: Sable draws a sub-level's block entities with the ship's pose
 * (docs/sable-notes.md §9.0g).
 */
public class HelmWheelRenderer implements BlockEntityRenderer<HelmBlockEntity> {

    static final float AXLE_X = 8f / 16f;
    static final float AXLE_Y = 13f / 16f;
    static final float AXLE_Z = 3.75f / 16f;

    public HelmWheelRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(HelmBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof HelmBlock)) {
            return;
        }
        Float local = HelmSteeringClient.predictedWheel(be.getBlockPos(), partialTick);
        float angle = local != null ? local : be.displayedWheel(System.nanoTime(), HelmBlockEntity.EASE_NANOS);
        Direction facing = state.getValue(HelmBlock.FACING);
        pose.pushPose();
        // the block state's y rotation: north is the model as built, each quarter turn clockwise seen from above
        pose.translate(0.5f, 0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        pose.translate(-0.5f, 0f, -0.5f);
        pose.translate(AXLE_X, AXLE_Y, AXLE_Z);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        pose.translate(-AXLE_X, -AXLE_Y, -AXLE_Z);
        BakedModel wheel = ClientEvents.additionalModel(HelmClient.WHEEL_MODEL);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(),
                buffers.getBuffer(Sheets.solidBlockSheet()), null, wheel, 1f, 1f, 1f, light, overlay);
        pose.popPose();
    }

    /**
     * NeoForge's culling box ({@code IBlockEntityRendererExtension#getRenderBoundingBox}, overridden by this method in
     * the NeoForge module): the wheel's handles reach about 0.4 blocks past the block.
     */
    public AABB getRenderBoundingBox(HelmBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(0.5);
    }
}
