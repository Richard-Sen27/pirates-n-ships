package com.richardsenger.piratesnships.combat.cannon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.richardsenger.piratesnships.combat.cannon.SwivelGunBlock;
import com.richardsenger.piratesnships.combat.cannon.SwivelGunBlockEntity;
import com.richardsenger.piratesnships.combat.cannon.SwivelPiece;
import com.richardsenger.piratesnships.combat.cannon.SwivelRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the swivel gun (docs/design.md §8.2, P2) from its block entity: the yoke turned by the yaw about the block's
 * vertical centre line, and the barrel turned by the yaw and raised by the elevation about the pivot
 * ({@link SwivelRules#PIVOT_HEIGHT}). Both are ordinary baked block models ({@code swivel_gun_yoke},
 * {@code swivel_gun_barrel[_loaded]}, placeholders until F7g), reached through the block's {@link SwivelGunBlock#PIECE}
 * states, built with the muzzle to the north (yaw 180°). While the local player aims this gun, the aim follows the
 * player's view directly (no wait for the server's update).
 *
 * <p>Works the same on land and on ships: Sable renders a sub-level's block entities through the vanilla dispatcher
 * with the ship's pose on the pose stack (see {@code sailing/client/YardClothRenderer}), and the aim is in the block
 * (plot) frame.
 */
public class SwivelGunRenderer implements BlockEntityRenderer<SwivelGunBlockEntity> {

    public SwivelGunRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SwivelGunBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof SwivelGunBlock)) {
            return;
        }
        SwivelRules.Aim aim = SwivelAimClient.localAim(be, partialTick);
        if (aim == null) {
            aim = be.aim();
        }
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        pose.pushPose();
        pose.translate(0.5f, 0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees((float) (180.0 - aim.yawDegrees())));
        pose.translate(-0.5f, 0f, -0.5f);
        draw(blocks, state.setValue(SwivelGunBlock.PIECE, SwivelPiece.YOKE), pose, buffers, light, overlay);
        float pivot = (float) SwivelRules.PIVOT_HEIGHT;
        pose.translate(0.5f, pivot, 0.5f);
        pose.mulPose(Axis.XP.rotationDegrees((float) aim.elevationDegrees()));
        pose.translate(-0.5f, -pivot, -0.5f);
        draw(blocks, state.setValue(SwivelGunBlock.PIECE, SwivelPiece.BARREL), pose, buffers, light, overlay);
        pose.popPose();
    }

    private static void draw(BlockRenderDispatcher blocks, BlockState state, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        blocks.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(ItemBlockRenderTypes.getRenderType(state, false)),
                state, blocks.getBlockModel(state), 1f, 1f, 1f, light, overlay);
    }
}
