package com.richardsenger.piratesnships.sailing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.richardsenger.piratesnships.sailing.block.BoomBlock;
import com.richardsenger.piratesnships.sailing.block.BoomBlockEntity;
import com.richardsenger.piratesnships.sailing.block.BoomData;
import com.richardsenger.piratesnships.sailing.sail.BoomRules;
import com.richardsenger.piratesnships.ship.decor.wood.client.WoodModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Draws a boom's arm (SAIL5) from its block entity: the stand-alone part {@code block/<wood>_boom_arm} (one block of
 * spar along −z from the pivot, {@code art/models/boom.bbmodel} group {@code arm}) repeated from the pivot (the
 * block's centre) out to the sail's foot, the last piece shortened, turned about the vertical to the boom's direction:
 * the arm toward the tack ({@link BoomBlockEntity#arm}) turned by the drawn sheet ({@link BoomBlockEntity#shownSheet},
 * eased, the same angle the cloth renderer draws). A boom without a sail draws {@link #SHORT_ARM} blocks along its
 * facing. The block model is only the gooseneck.
 *
 * <p>The turn is {@link #armYaw}: {@code Axis.YP} turns counter-clockwise seen from above, so the model's −z goes to
 * {@code (−sin a, −cos a)}; {@code BoomArmRigTest} composes these transforms and checks every pivot and sign.
 */
public class BoomRenderer implements BlockEntityRenderer<BoomBlockEntity> {

    /** The arm of a boom that carries no sail [blocks]. */
    public static final float SHORT_ARM = 1.5f;

    public BoomRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BoomBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !(state.getBlock() instanceof BoomBlock boom)) {
            return;
        }
        double now = level.getGameTime() + (double) partialTick;
        float sheet = be.shownSheet(now);
        double[] dir = direction(be.arm(), state.getValue(BoomBlock.FACING), sheet);
        float length = (float) Math.hypot(dir[0], dir[1]);
        if (length < 1.0e-4f) {
            return;
        }
        BakedModel arm = WoodModels.get(boom, BoomData.ARM_MODEL);
        pose.pushPose();
        placeArm(pose, dir[0], dir[1]);
        int whole = (int) Math.floor(length);
        for (int i = 0; i <= whole; i++) {
            float piece = Math.min(1f, length - i);
            if (piece <= 1.0e-3f) {
                break;
            }
            pose.pushPose();
            placePiece(pose, i, piece);
            Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(),
                    buffers.getBuffer(Sheets.solidBlockSheet()), null, arm, 1f, 1f, 1f, light, overlay);
            pose.popPose();
        }
        pose.popPose();
    }

    /** From the block's corner to the pivot (the block's centre), turned so −z runs along the boom {@code (x, z)}. */
    static void placeArm(PoseStack pose, double x, double z) {
        pose.translate(0.5f, 0.5f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(armYaw(x, z)));
    }

    /**
     * Piece {@code i} of the arm, {@code piece} blocks long (≤ 1): the model's block (its spar along z from 1 to 0, the
     * centre at x = y = 0.5) moved to run from {@code −i} to {@code −i − piece} along the arm's −z.
     */
    static void placePiece(PoseStack pose, int i, float piece) {
        pose.translate(-0.5f, -0.5f, -i);
        pose.scale(1f, 1f, piece);
        pose.translate(0f, 0f, -1f);
    }

    /**
     * The arm from the pivot to the boom's end {@code {x, z}} [blocks]: the arm to the tack ({@code arm}, null without a
     * sail: {@link #SHORT_ARM} along {@code facing}) turned clockwise seen from above by {@code sheetDegrees}.
     */
    public static double[] direction(int[] arm, Direction facing, double sheetDegrees) {
        double x = arm == null ? facing.getStepX() * SHORT_ARM : arm[0];
        double z = arm == null ? facing.getStepZ() * SHORT_ARM : arm[1];
        return BoomRules.turn(x, z, sheetDegrees);
    }

    /** The {@code Axis.YP} angle [degrees] that turns the model's −z toward {@code (x, z)}. */
    public static float armYaw(double x, double z) {
        return (float) Math.toDegrees(Math.atan2(-x, -z));
    }

    @Override
    public int getViewDistance() {
        return 192;
    }

    /** NeoForge's culling box ({@code IBlockEntityRendererExtension#getRenderBoundingBox}): the arm's reach around the pivot. */
    public AABB getRenderBoundingBox(BoomBlockEntity be) {
        double reach = Math.max(SHORT_ARM, be.armLength()) + 0.5;
        return new AABB(be.getBlockPos()).inflate(reach, 0.5, reach);
    }
}
