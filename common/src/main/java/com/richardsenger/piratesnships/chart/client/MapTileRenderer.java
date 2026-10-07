package com.richardsenger.piratesnships.chart.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.richardsenger.piratesnships.chart.data.MapTileDrawing;
import com.richardsenger.piratesnships.chart.data.TileMarker;
import com.richardsenger.piratesnships.chart.tile.MapTileBlock;
import com.richardsenger.piratesnships.chart.tile.MapTileBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws a map tile's drawing (client only, work package MAP2) as one textured quad just above the tile's parchment
 * face ({@link MapTileTextures}: the raster, worn edge, ink border, compass rose and marker icons in one picture),
 * lit by the block's light like a map in an item frame. Close up (within {@link #NAME_DISTANCE} blocks of the camera)
 * the markers' names are written under their icons in small ink letters.
 *
 * <p>Orientation: on the floor the drawing's north edge points along {@code facing} (away from whoever placed it, so
 * they read it upright); on a wall it is up. Seen from the front, "right" is {@code facing} turned clockwise on the
 * floor and counter-clockwise on a wall. A blank tile draws nothing (the block model's parchment shows). On ships
 * Sable draws block entities through the vanilla dispatcher with the ship's pose on the stack, so the camera distance
 * comes from the pose, not from block positions.
 */
public class MapTileRenderer implements BlockEntityRenderer<MapTileBlockEntity> {

    /** Height of the face above the tile's back, in blocks: the model is one pixel thick, plus a hair against z-fighting. */
    private static final float FACE = 1f / 16f + 0.0025f;
    private static final float NAME_DISTANCE = 5f;
    private static final int NAME_COLOR = 0xFF2C2018;

    private final Font font;

    public MapTileRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(MapTileBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        MapTileDrawing d = be.drawing();
        BlockState state = be.getBlockState();
        if (d == null || !(state.getBlock() instanceof MapTileBlock)) return;
        ResourceLocation texture = MapTileTextures.texture(d);
        if (texture == null) return;

        Direction facing = state.getValue(MapTileBlock.FACING);
        boolean wall = state.getValue(MapTileBlock.FACE) == AttachFace.WALL;
        Vector3f up;
        Vector3f right;
        Vector3f normal;
        Vector3f centre;
        if (wall) {
            up = new Vector3f(0, 1, 0);
            right = vec(facing.getCounterClockWise());
            normal = vec(facing);
            // the tile hangs against the block behind it (opposite to facing)
            centre = new Vector3f(0.5f, 0.5f, 0.5f).sub(new Vector3f(normal).mul(0.5f - FACE));
        } else {
            up = vec(facing);
            right = vec(facing.getClockWise());
            normal = new Vector3f(0, 1, 0);
            centre = new Vector3f(0.5f, FACE, 0.5f);
        }

        Matrix4f m = pose.last().pose();
        Vector3f tl = corner(centre, right, up, -0.5f, 0.5f);
        Vector3f bl = corner(centre, right, up, -0.5f, -0.5f);
        Vector3f br = corner(centre, right, up, 0.5f, -0.5f);
        Vector3f tr = corner(centre, right, up, 0.5f, 0.5f);
        VertexConsumer vc = buffers.getBuffer(RenderType.text(texture));
        // counter-clockwise seen from the front (right x up = normal)
        vertex(vc, m, tl, 0, 0, light);
        vertex(vc, m, bl, 0, 1, light);
        vertex(vc, m, br, 1, 1, light);
        vertex(vc, m, tr, 1, 0, light);

        if (!d.markers().isEmpty() && m.getTranslation(new Vector3f()).add(centre.x, centre.y, centre.z).length() < NAME_DISTANCE) {
            renderNames(d, pose, buffers, light, centre, right, up, normal);
        }
    }

    private void renderNames(MapTileDrawing d, PoseStack pose, MultiBufferSource buffers, int light, Vector3f centre, Vector3f right, Vector3f up, Vector3f normal) {
        float pixel = 1f / d.size();
        // glyphs 7 font units tall become about 4 tile pixels
        float s = pixel * 4f / 7f;
        for (TileMarker marker : d.markers()) {
            if (marker.name().isEmpty()) continue;
            float u = (marker.px() + 0.5f) * pixel - 0.5f;
            float v = 0.5f - (marker.py() + 0.5f) * pixel;
            Vector3f at = corner(centre, right, up, u, v - 5 * pixel).add(new Vector3f(normal).mul(0.002f));
            pose.pushPose();
            // font space: x along "right", y down the face, z out of it
            Matrix4f basis = new Matrix4f(
                    right.x * s, right.y * s, right.z * s, 0,
                    -up.x * s, -up.y * s, -up.z * s, 0,
                    normal.x * s, normal.y * s, normal.z * s, 0,
                    at.x, at.y, at.z, 1);
            pose.last().pose().mul(basis);
            int w = font.width(marker.name());
            font.drawInBatch(marker.name(), -w / 2f, 0, NAME_COLOR, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
            pose.popPose();
        }
    }

    private static Vector3f vec(Direction d) {
        return new Vector3f(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    /** {@code centre + right * u + up * v}. */
    private static Vector3f corner(Vector3f centre, Vector3f right, Vector3f up, float u, float v) {
        return new Vector3f(centre).add(new Vector3f(right).mul(u)).add(new Vector3f(up).mul(v));
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vector3f p, float u, float v, int light) {
        vc.addVertex(m, p.x, p.y, p.z).setColor(255, 255, 255, 255).setUv(u, v).setLight(light);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
