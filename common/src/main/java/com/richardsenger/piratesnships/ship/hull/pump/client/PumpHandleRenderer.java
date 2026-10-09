package com.richardsenger.piratesnships.ship.hull.pump.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.ship.hull.pump.BilgePumpBlock;
import com.richardsenger.piratesnships.ship.hull.pump.BilgePumpBlockEntity;
import com.richardsenger.piratesnships.ship.hull.pump.PumpHandlePose;
import com.richardsenger.piratesnships.ship.hull.pump.PumpVisualsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the bilge pump's brake handle and piston rod (PMP1, docs/design.md §4.8 "Visual backlog 2", item 3); the block
 * model is the pump's body only ({@code bilge_pump}). The handle ({@code bilge_pump_handle}, built at the top of its
 * stroke) turns about its pin (x through {@link PumpHandlePose#PIVOT_Y}, {@link PumpHandlePose#PIVOT_Z} of the
 * {@code facing=north} model) down by the swing of {@link BilgePumpBlockEntity#swing()}: while the synced pumping flag
 * is on it rocks by {@code pump_visuals.stroke_degrees} once every {@code stroke_ticks}, and it eases to rest when the
 * flag goes off. The rod ({@code bilge_pump_rod}) slides up and down under the handle ({@link PumpHandlePose#rodLift}).
 * Both are lit by the block's light, drawn untinted on the block atlas and turned to the block's facing like the block
 * state.
 *
 * <p>Works on ships like {@code CannonBarrelRenderer}: Sable draws a sub-level's block entities with the ship's pose.
 * The parts stay inside the block's own box (the raised grip reaches 4.6 px above it, as the full block model did).
 */
public class PumpHandleRenderer implements BlockEntityRenderer<BilgePumpBlockEntity> {

    public PumpHandleRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BilgePumpBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof BilgePumpBlock)) {
            return;
        }
        double swing = 0.0;
        if (PumpVisualsConfig.ENABLED.get() && be.getLevel() != null) {
            double now = be.getLevel().getGameTime() + partialTick;
            double degrees = Math.min(PumpVisualsConfig.STROKE_DEGREES.get(), PumpHandlePose.MAX_SWING);
            swing = be.swing().swing(be.pumping(), now, PumpVisualsConfig.STROKE_TICKS.get(), degrees);
        }
        Direction facing = state.getValue(BilgePumpBlock.FACING);
        ModelBlockRenderer models = Minecraft.getInstance().getBlockRenderer().getModelRenderer();

        pose.pushPose();
        // the block state's y rotation: north is the model as built, each quarter turn clockwise seen from above
        pose.translate(0.5f, 0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        pose.translate(-0.5f, 0f, -0.5f);

        pose.pushPose();
        pose.translate(0f, (float) (PumpHandlePose.rodLift(swing) / 16.0), 0f);
        draw(models, pose, buffers, PumpClient.ROD_MODEL, light, overlay);
        pose.popPose();

        float pivotY = (float) (PumpHandlePose.PIVOT_Y / 16.0);
        float pivotZ = (float) (PumpHandlePose.PIVOT_Z / 16.0);
        pose.translate(0f, pivotY, pivotZ);
        // Axis.XP with a positive angle lifts the north (grip) end, as the model's own +22.5; the swing lowers it
        pose.mulPose(Axis.XP.rotationDegrees((float) -swing));
        pose.translate(0f, -pivotY, -pivotZ);
        draw(models, pose, buffers, PumpClient.HANDLE_MODEL, light, overlay);
        pose.popPose();
    }

    private static void draw(ModelBlockRenderer models, PoseStack pose, MultiBufferSource buffers, ResourceLocation id,
                             int light, int overlay) {
        BakedModel model = ClientEvents.additionalModel(id);
        models.renderModel(pose.last(), buffers.getBuffer(Sheets.solidBlockSheet()), null, model, 1f, 1f, 1f, light, overlay);
    }
}
