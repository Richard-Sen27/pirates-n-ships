package com.richardsenger.piratesnships.ship.decor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.richardsenger.piratesnships.ship.decor.NameplateBlock;
import com.richardsenger.piratesnships.ship.decor.NameplateBlockEntity;
import com.richardsenger.piratesnships.ship.decor.NameplateText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the ship's name ({@link NameplateBlockEntity#text()}) centred on the nameplate's board, like a sign draws its
 * text: dark letters with the block's light, as large as the board allows and shrunk to its width
 * ({@link NameplateText}); a name too long even at the smallest scale is cut short with "...". The board's position
 * comes from the Blockbench model (see {@link NameplateText}); the model faces north and the block state turns it like a
 * ladder, so the text is turned the same way. On ships Sable draws block entities through the vanilla dispatcher with
 * the ship's pose on the stack (see {@code sailing.client.YardClothRenderer}).
 */
public class NameplateRenderer implements BlockEntityRenderer<NameplateBlockEntity> {

    /** Dark brown, like burnt-in letters on the spruce panel. */
    private static final int COLOR = 0xFF2A1A0E;
    private static final String ELLIPSIS = "...";

    private final Font font;

    public NameplateRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(NameplateBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (be.text().isEmpty() || !(state.getBlock() instanceof NameplateBlock)) return;
        String text = be.display().getString(); // "Wreck of <name>" on a wreck piece (RS1), translated here

        Direction facing = state.getValue(LadderBlock.FACING);
        int width = font.width(text);
        float scale = NameplateText.fitScale(width);
        int maxWidth = NameplateText.maxWidthAt(scale);
        if (width > maxWidth) {
            text = font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ELLIPSIS))).stripTrailing() + ELLIPSIS;
            width = font.width(text);
        }
        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f);
        // The block state's y rotation of the north-facing model (vanilla turns clockwise seen from above)
        pose.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        pose.translate((NameplateText.CENTER_X - 8f) / 16f, (NameplateText.CENTER_Y - 8f) / 16f, (NameplateText.TEXT_Z - 8f) / 16f);
        // Text faces north (toward -z), read from the north
        pose.mulPose(Axis.YP.rotationDegrees(180f));
        float s = scale / 16f;
        pose.scale(s, -s, s);
        font.drawInBatch(text, -width / 2f, -NameplateText.GLYPH_HEIGHT / 2f, COLOR, false, pose.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, light);
        pose.popPose();
    }
}
