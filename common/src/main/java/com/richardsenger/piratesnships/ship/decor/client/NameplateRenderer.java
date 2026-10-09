package com.richardsenger.piratesnships.ship.decor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.richardsenger.piratesnships.ship.decor.NameBoardBlock;
import com.richardsenger.piratesnships.ship.decor.NameBoardRow;
import com.richardsenger.piratesnships.ship.decor.NameplateBlockEntity;
import com.richardsenger.piratesnships.ship.decor.NameplateText;
import com.richardsenger.piratesnships.ship.decor.ShipIdentityConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws the ship's name once across a row of name boards (NAME2, docs/design.md §4.8 "Ship name boards"): only the
 * row's master (its leftmost board, {@link NameplateBlockEntity#isMaster()}) draws, centred on the whole row of
 * {@link NameplateBlockEntity#rowLength()} boards, in dark carved letters on the plank panel with the master's light,
 * scaled by {@link NameplateText#fitScale} (fills the row inside its frame, never taller than the board); a name too
 * long even at the smallest scale is cut short with "...".
 *
 * <p>Orientation: the board lies against the back of its block and looks along {@code facing}; the text runs to the
 * viewer's right ({@link NameBoardRow#right}, {@code facing} turned counter-clockwise, as a map tile on a wall), so on
 * both sides of a hull it reads left to right from outside. Drawn up to {@code ship_identity.name_board_distance}
 * blocks away ({@link #getViewDistance()}); its size does not grow with distance (a row's text is already larger than a
 * sign's, see {@link NameplateText#readableDistance}). On ships Sable draws block entities through the vanilla
 * dispatcher with the ship's pose on the stack and the camera moved into the plot for the distance check
 * ({@code BlockEntityRenderDispatcherMixin#sable$moveCameraPosForCheck}), and transforms the culling box below into the
 * world on NeoForge ({@code neoforge/.../block_entity_visible/LevelRendererMixin}).
 */
public class NameplateRenderer implements BlockEntityRenderer<NameplateBlockEntity> {

    /** Dark brown, like letters carved and burnt into the planks. */
    private static final int COLOR = 0xFF2A1A0E;
    private static final String ELLIPSIS = "...";

    private final Font font;

    public NameplateRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(NameplateBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (be.text().isEmpty() || !be.isMaster() || !(state.getBlock() instanceof NameBoardBlock)) return;
        String text = be.display().getString(); // "Wreck of <name>" on a wreck piece (RS1), translated here
        int boards = Math.max(1, be.rowLength());

        int width = font.width(text);
        float scale = NameplateText.fitScale(boards, width);
        int maxWidth = NameplateText.maxWidthAt(boards, scale);
        if (width > maxWidth) {
            text = font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ELLIPSIS))).stripTrailing() + ELLIPSIS;
            width = font.width(text);
        }

        Direction facing = state.getValue(NameBoardBlock.FACING);
        Vector3f right = vec(NameBoardRow.right(facing));
        Vector3f normal = vec(facing);
        Vector3f up = new Vector3f(0, 1, 0);
        // the middle of the row on the panel's front: from the master's centre half the row to the right, and from the
        // block's middle back to the panel (the north-facing model has its panel front at z TEXT_Z)
        Vector3f at = new Vector3f(0.5f, NameplateText.CENTER_Y / 16f, 0.5f)
                .add(new Vector3f(right).mul((boards - 1) / 2f))
                .add(new Vector3f(normal).mul((8f - NameplateText.TEXT_Z) / 16f));
        float s = scale / 16f;
        pose.pushPose();
        // font space: x along the row to the right, y down the board, z out of it
        Matrix4f basis = new Matrix4f(
                right.x * s, right.y * s, right.z * s, 0,
                -up.x * s, -up.y * s, -up.z * s, 0,
                normal.x * s, normal.y * s, normal.z * s, 0,
                at.x, at.y, at.z, 1);
        pose.last().pose().mul(basis);
        font.drawInBatch(text, -width / 2f, -NameplateText.GLYPH_HEIGHT / 2f, COLOR, false, pose.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, light);
        pose.popPose();
    }

    private static Vector3f vec(Direction d) {
        return new Vector3f(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    /** {@code ship_identity.name_board_distance} (64 blocks by default; vanilla draws block entities to 64). */
    @Override
    public int getViewDistance() {
        return ShipIdentityConfig.NAME_BOARD_DISTANCE.get();
    }

    /**
     * The whole row: the name reaches over every board of it. NeoForge culls block entity renderers by this box
     * ({@code IBlockEntityRendererExtension#getRenderBoundingBox}, which this method overrides when the NeoForge module
     * compiles the common sources); vanilla and Fabric have no such check, so on those it is just an unused method.
     */
    public AABB getRenderBoundingBox(NameplateBlockEntity be) {
        BlockPos p = be.getBlockPos();
        BlockState state = be.getBlockState();
        if (!state.hasProperty(NameBoardBlock.FACING) || be.rowLength() <= 1) return new AABB(p);
        BlockPos end = p.relative(NameBoardRow.right(state.getValue(NameBoardBlock.FACING)), be.rowLength() - 1);
        return new AABB(p).minmax(new AABB(end));
    }
}
