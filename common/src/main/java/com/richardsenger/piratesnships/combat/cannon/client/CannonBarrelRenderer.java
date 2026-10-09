package com.richardsenger.piratesnships.combat.cannon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.richardsenger.piratesnships.combat.cannon.CannonBarrelPose;
import com.richardsenger.piratesnships.combat.cannon.CannonBlock;
import com.richardsenger.piratesnships.combat.cannon.CannonBlockEntity;
import com.richardsenger.piratesnships.combat.cannon.CannonConfig;
import com.richardsenger.piratesnships.combat.cannon.CannonLoad;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Draws the cannon's barrel and quoin (CAN2, docs/design.md §4.8 "Cannon barrel tilt") from the master block entity;
 * the block model is only the carriage ({@code cannon_carriage}, with the rammer {@code cannon_carriage_rammer}). The
 * barrel is the hand-made stand-alone model of the load state ({@code cannon_barrel}, {@code _powder} with the priming
 * quill, {@code _loaded} with the ball), turned about the trunnion axis (x through 14 px up, z 8 px of the
 * {@code facing=north} model) by the elevation step's angle ({@link CannonConfig#elevationDegrees}, clamped to
 * {@link CannonBarrelPose#drawnDegrees}) and eased between steps over {@code cannon_visuals.tilt_ticks}; the quoin
 * ({@code cannon_quoin}) shrinks and draws back under a dropping breech ({@link CannonBarrelPose#quoin}). Both are lit
 * by the block's light and drawn untinted on the block atlas, turned to the block's facing like the block state.
 *
 * <p>Works on ships like {@code HelmWheelRenderer}: Sable draws a sub-level's block entities with the ship's pose
 * (docs/sable-notes.md §9.0g).
 */
public class CannonBarrelRenderer implements BlockEntityRenderer<CannonBlockEntity> {

    public CannonBarrelRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CannonBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof CannonBlock) || !state.getValue(CannonBlock.PART).isMaster()) {
            return;
        }
        double angle = 0.0;
        if (CannonConfig.TILT_ENABLED.get()) {
            double target = CannonBarrelPose.drawnDegrees(CannonConfig.elevationDegrees(be.elevationStep()));
            double now = be.getLevel() == null ? 0.0 : be.getLevel().getGameTime() + partialTick;
            angle = be.tilt().angle(target, now, CannonConfig.TILT_TICKS.get());
        }
        CannonBarrelPose.Quoin quoin = CannonBarrelPose.quoin(angle);
        Direction facing = state.getValue(CannonBlock.FACING);
        ModelBlockRenderer models = Minecraft.getInstance().getBlockRenderer().getModelRenderer();

        pose.pushPose();
        // the block state's y rotation: north is the model as built, each quarter turn clockwise seen from above
        pose.translate(0.5f, 0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        pose.translate(-0.5f, 0f, -0.5f);

        pose.pushPose();
        float stool = (float) (CannonBarrelPose.STOOL_TOP / 16.0);
        pose.translate(0f, stool, (float) (quoin.slide() / 16.0));
        pose.scale(1f, (float) quoin.scale(), 1f);
        pose.translate(0f, -stool, 0f);
        draw(models, pose, buffers, CannonClient.QUOIN_MODEL, light, overlay);
        pose.popPose();

        float pivotY = (float) (CannonBarrelPose.PIVOT_Y / 16.0);
        float pivotZ = (float) (CannonBarrelPose.PIVOT_Z / 16.0);
        pose.translate(0f, pivotY, pivotZ);
        // Axis.XP with a positive angle lifts the north (muzzle) end, as CannonRules.muzzleDirection
        pose.mulPose(Axis.XP.rotationDegrees((float) angle));
        pose.translate(0f, -pivotY, -pivotZ);
        draw(models, pose, buffers, barrelModel(state.getValue(CannonBlock.LOAD)), light, overlay);
        pose.popPose();
    }

    static ResourceLocation barrelModel(CannonLoad load) {
        return switch (load) {
            case EMPTY -> CannonClient.BARREL_MODEL;
            case POWDER -> CannonClient.BARREL_POWDER_MODEL;
            case LOADED -> CannonClient.BARREL_LOADED_MODEL;
        };
    }

    private static void draw(ModelBlockRenderer models, PoseStack pose, MultiBufferSource buffers, ResourceLocation id,
                             int light, int overlay) {
        BakedModel model = ClientEvents.additionalModel(id);
        models.renderModel(pose.last(), buffers.getBuffer(Sheets.solidBlockSheet()), null, model, 1f, 1f, 1f, light, overlay);
    }

    /**
     * NeoForge's culling box ({@code IBlockEntityRendererExtension#getRenderBoundingBox}, overridden by this method in
     * the NeoForge module): the barrel reaches one block past the master in front, into the rear block behind and,
     * raised, up to 1.6 blocks high; one block all round and two up covers every facing.
     */
    public AABB getRenderBoundingBox(CannonBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 1, p.getY(), p.getZ() - 1, p.getX() + 2, p.getY() + 2, p.getZ() + 2);
    }
}
