package com.richardsenger.piratesnships.seachest.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.richardsenger.piratesnships.seachest.SeaChestConfig;
import com.richardsenger.piratesnships.seachest.SeaChestContent;
import com.richardsenger.piratesnships.seachest.SeaChestEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Draws the floating sea chest as its block model (facing north, turned to the entity's yaw so the front faces the
 * player who launched it), scaled to the entity's width, bobbing and rocking on the water by
 * {@code sea_chest_visuals.bob_amplitude}.
 */
public class SeaChestRenderer extends EntityRenderer<SeaChestEntity> {

    private final BlockRenderDispatcher blocks;

    public SeaChestRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.blocks = ctx.getBlockRenderDispatcher();
        this.shadowRadius = 0.4f;
    }

    @Override
    public void render(SeaChestEntity entity, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        if (entity.isInWater()) {
            float t = entity.tickCount + partialTick;
            float amplitude = SeaChestConfig.BOB_AMPLITUDE.get().floatValue();
            pose.translate(0.0, amplitude * Mth.sin(t * 0.12f), 0.0);
            pose.mulPose(Axis.XP.rotationDegrees(amplitude * 40f * Mth.sin(t * 0.09f + 1.3f)));
            pose.mulPose(Axis.ZP.rotationDegrees(amplitude * 30f * Mth.sin(t * 0.07f)));
        }
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        float scale = entity.getBbWidth();
        pose.scale(scale, scale, scale);
        pose.translate(-0.5, 0.0, -0.5);
        blocks.renderSingleBlock(SeaChestContent.BLOCK.get().defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SeaChestEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
