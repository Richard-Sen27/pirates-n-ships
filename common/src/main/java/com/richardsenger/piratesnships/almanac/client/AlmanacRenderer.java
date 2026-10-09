package com.richardsenger.piratesnships.almanac.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.richardsenger.piratesnships.almanac.AlmanacAnimation;
import com.richardsenger.piratesnships.almanac.AlmanacBlock;
import com.richardsenger.piratesnships.almanac.AlmanacBlockEntity;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the placed Captain's Almanac (ALM1, docs/design.md §3.4): vanilla's book model with the enchanting table's
 * book texture as the placeholder (ART14 brings the Blockbench tome), lying on the block as {@link AlmanacBookPose}
 * places it, opened and its pages turned from the block entity's {@link AlmanacAnimation} at the partial tick. The
 * block model draws nothing ({@code ENTITYBLOCK_ANIMATED}). On a ship Sable draws the block entity with the ship's pose
 * like any other (docs/sable-notes.md §9.0g); the book stays inside its block, so the default culling box fits.
 */
public class AlmanacRenderer implements BlockEntityRenderer<AlmanacBlockEntity> {

    private final BookModel book;

    public AlmanacRenderer(BlockEntityRendererProvider.Context context) {
        this.book = new BookModel(context.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public void render(AlmanacBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof AlmanacBlock)) return;
        AlmanacAnimation animation = be.animation();
        float open = animation.open(partialTick);
        float flip = animation.flip(partialTick);
        pose.pushPose();
        pose.mulPose(AlmanacBookPose.pose(state.getValue(AlmanacBlock.FACING), open));
        // time 0: no floating "breath"; the lid angle is LID_ANGLE * open (AlmanacBookPose)
        book.setupAnim(0.0f, AlmanacAnimation.pageTurn(flip, 0.25f), AlmanacAnimation.pageTurn(flip, 0.75f), open);
        book.render(pose, EnchantTableRenderer.BOOK_LOCATION.buffer(buffers, RenderType::entitySolid), light, overlay, -1);
        pose.popPose();
    }
}
