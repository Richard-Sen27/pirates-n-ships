package com.richardsenger.piratesnships.station.capstan.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.sailing.block.CapstanBlock;
import com.richardsenger.piratesnships.station.capstan.CapstanBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.phys.AABB;

/**
 * Draws the capstan's bars and drumhead (CAP2, docs/design.md §6 "Capstan in motion"): the hand-made stand-alone
 * {@code block/capstan_bars} model (the {@code bars} group of {@code art/models/capstan.bbmodel}) turned about the drum's
 * vertical axis through the block centre by {@link CapstanBlockEntity#barAngle}, which follows the synced turning at the
 * synced rate with the partial tick and settles on a quarter turn when the capstan stops; the block model
 * {@code block/capstan} is only the base and the drum. With {@code crew_stations.capstan.animation} off the server never
 * turns it, so the bars stand still. Lit by the block's light, drawn untinted on the block atlas.
 *
 * <p>Works on ships like {@code PumpHandleRenderer}: Sable draws a sub-level's block entities with the ship's pose.
 */
public class CapstanBarsRenderer implements BlockEntityRenderer<CapstanBlockEntity> {

    public CapstanBarsRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CapstanBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(be.getBlockState().getBlock() instanceof CapstanBlock) || be.getLevel() == null) {
            return;
        }
        double angle = be.barAngle(be.getLevel().getGameTime() + partialTick);
        pose.pushPose();
        pose.translate(0.5f, 0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees((float) (angle % 360.0)));
        pose.translate(-0.5f, 0f, -0.5f);
        BakedModel bars = ClientEvents.additionalModel(CapstanClient.BARS_MODEL);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(),
                buffers.getBuffer(Sheets.solidBlockSheet()), null, bars, 1f, 1f, 1f, light, overlay);
        pose.popPose();
    }

    /**
     * NeoForge's culling box ({@code IBlockEntityRendererExtension#getRenderBoundingBox}, overridden by this method in
     * the NeoForge module): the bar tips reach 3 px past the block, about 0.3 blocks when turned 45 degrees.
     */
    public AABB getRenderBoundingBox(CapstanBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(0.5);
    }
}
