package com.richardsenger.piratesnships.combat.boarding.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.richardsenger.piratesnships.combat.boarding.BoardingPlankBlock;
import com.richardsenger.piratesnships.combat.boarding.BoardingPlankBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Draws a gangway's straight board (BRD4) from its segment 0 ({@link BoardingPlankBlockEntity#isBase()}): one board from
 * the near end to the far end at its single pitch ({@link BoardingPlankBlockEntity#boardStart()},
 * {@link BoardingPlankBlockEntity#boardEnd()}), 14 px wide and 2 px thick (its underside on the line the cells'
 * collision steps follow), the vanilla spruce planks texture tiled along its length (u in blocks, so the grain runs
 * along the board and never stretches), a short flat lip where an end meets an edge (the deck or rail it starts from,
 * the top edge it climbs to), and on a slope steeper than {@link #CLEAT_PITCH} a stripped-spruce cleat across the board
 * every block, which is what a walker's feet find on a steep gangway.
 *
 * <p>A cell that knows no run (a Ponder scene sets bare plank blocks; a cell of an old save before its run is
 * recomputed) draws its own flat or sloped piece from its near to its far edge, so a run is never invisible. The other
 * cells of a known run draw nothing; their block model is particles only.
 *
 * <p>Drawn in segment 0's block entity space; on a ship Sable renders a sub-level's block entities with the ship's pose
 * on the pose stack, so the board moves with the plot. NeoForge culls block entity renderers by
 * {@link #getRenderBoundingBox}, the whole board (the ratline net's pattern).
 */
public class BoardingPlankRenderer implements BlockEntityRenderer<BoardingPlankBlockEntity> {

    public static final ResourceLocation PLANKS = ResourceLocation.withDefaultNamespace("textures/block/spruce_planks.png");
    public static final ResourceLocation CLEAT = ResourceLocation.withDefaultNamespace("textures/block/stripped_spruce_log.png");
    /** Above this pitch (degrees) the board carries cleats. */
    public static final double CLEAT_PITCH = 15.0;
    /** Half the board's width [blocks]: the cells' collision is inset {@link BoardingPlankBlock#INSET} px each side. */
    private static final float HALF = (8 - BoardingPlankBlock.INSET) / 16f;
    /** The board's thickness, vertical [blocks]. */
    private static final float THICK = BoardingPlankBlock.THICKNESS / 16f;
    /** How far a lip reaches past an end [blocks]. */
    private static final float LIP = 3 / 16f;
    /** A cleat: half its width across, its length along the board, its height [blocks]. */
    private static final float CLEAT_HALF = 6 / 16f;
    private static final float CLEAT_LENGTH = 1.5f / 16f;
    private static final float CLEAT_HEIGHT = 1 / 16f;

    public BoardingPlankRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BoardingPlankBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction f = be.facing();
        Vec3 start;
        Vec3 end;
        boolean lipNear;
        boolean lipFar;
        int columns;
        if (be.isBase()) {
            start = be.boardStart();
            end = be.boardEnd();
            lipNear = be.lipNear();
            lipFar = be.lipFar();
            columns = be.columns();
        } else if (!be.linked()) {
            start = new Vec3(0.5 - 0.5 * f.getStepX(), be.near(), 0.5 - 0.5 * f.getStepZ());
            end = start.add(f.getStepX(), be.far() - be.near(), f.getStepZ());
            lipNear = false;
            lipFar = false;
            columns = 1;
        } else {
            return;
        }
        PoseStack.Pose last = pose.last();
        Vector3f across = new Vector3f(f.getClockWise().getStepX(), 0, f.getClockWise().getStepZ());
        Vector3f a = vec(start);
        Vector3f b = vec(end);
        VertexConsumer planks = buffers.getBuffer(RenderType.entityCutoutNoCull(PLANKS));
        slab(planks, last, a, b, across, HALF, THICK, light, overlay);
        Vector3f out = new Vector3f(f.getStepX(), 0, f.getStepZ()).mul(LIP);
        if (lipNear) {
            slab(planks, last, new Vector3f(a).sub(out), a, across, HALF, THICK, light, overlay);
        }
        if (lipFar) {
            slab(planks, last, b, new Vector3f(b).add(out), across, HALF, THICK, light, overlay);
        }
        double run = Math.hypot(end.x - start.x, end.z - start.z);
        double pitch = Math.toDegrees(Math.atan2(Math.abs(end.y - start.y), run));
        if (pitch > CLEAT_PITCH) {
            VertexConsumer cleats = buffers.getBuffer(RenderType.entityCutoutNoCull(CLEAT));
            Vector3f d = new Vector3f(b).sub(a);
            Vector3f half = new Vector3f(d).normalize().mul(CLEAT_LENGTH / 2f);
            for (int k = 0; k < columns; k++) {
                Vector3f c = new Vector3f(d).mul((k + 0.5f) / columns).add(a).add(0, THICK, 0);
                slab(cleats, last, new Vector3f(c).sub(half), new Vector3f(c).add(half), across, CLEAT_HALF, CLEAT_HEIGHT,
                        light, overlay);
            }
        }
    }

    private static Vector3f vec(Vec3 v) {
        return new Vector3f((float) v.x, (float) v.y, (float) v.z);
    }

    /**
     * A board whose underside's middle runs from {@code a} to {@code b}, {@code half} to either side along
     * {@code across} (horizontal, unit) and {@code thick} high (vertical, so its top follows the cells' collision). The
     * top and bottom carry the texture with u along the board in blocks (tiled) and v across it; the sides and ends a
     * strip of it.
     */
    private static void slab(VertexConsumer vc, PoseStack.Pose p, Vector3f a, Vector3f b, Vector3f across, float half,
                             float thick, int light, int overlay) {
        Vector3f d = new Vector3f(b).sub(a);
        float len = d.length();
        if (len < 1.0e-4f) {
            return;
        }
        Vector3f s = new Vector3f(across).mul(half);
        Vector3f up = new Vector3f(0, thick, 0);
        Vector3f a0 = new Vector3f(a).sub(s), a1 = new Vector3f(a).add(s);
        Vector3f b0 = new Vector3f(b).sub(s), b1 = new Vector3f(b).add(s);
        Vector3f a0t = new Vector3f(a0).add(up), a1t = new Vector3f(a1).add(up);
        Vector3f b0t = new Vector3f(b0).add(up), b1t = new Vector3f(b1).add(up);
        Vector3f dir = new Vector3f(d).div(len);
        Vector3f top = new Vector3f(across).cross(dir).normalize();
        if (top.y < 0) top.negate();
        Vector3f bottom = new Vector3f(top).negate();
        Vector3f side = new Vector3f(across);
        Vector3f other = new Vector3f(across).negate();
        Vector3f fore = new Vector3f(dir.x, 0, dir.z).normalize();
        Vector3f aft = new Vector3f(fore).negate();
        float v0 = 0.5f - half, v1 = 0.5f + half, t = thick;
        quad(vc, p, a0t, a1t, b1t, b0t, 0, v0, 0, v1, len, v1, len, v0, top, light, overlay);
        quad(vc, p, a0, b0, b1, a1, 0, v0, len, v0, len, v1, 0, v1, bottom, light, overlay);
        quad(vc, p, a1, b1, b1t, a1t, 0, t, len, t, len, 0, 0, 0, side, light, overlay);
        quad(vc, p, a0, a0t, b0t, b0, 0, t, 0, 0, len, 0, len, t, other, light, overlay);
        quad(vc, p, a0, a1, a1t, a0t, v0, t, v1, t, v1, 0, v0, 0, aft, light, overlay);
        quad(vc, p, b0, b0t, b1t, b1, v0, t, v0, 0, v1, 0, v1, t, fore, light, overlay);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose p, Vector3f c0, Vector3f c1, Vector3f c2, Vector3f c3,
                             float u0, float w0, float u1, float w1, float u2, float w2, float u3, float w3, Vector3f n,
                             int light, int overlay) {
        vertex(vc, p, c0, u0, w0, n, light, overlay);
        vertex(vc, p, c1, u1, w1, n, light, overlay);
        vertex(vc, p, c2, u2, w2, n, light, overlay);
        vertex(vc, p, c3, u3, w3, n, light, overlay);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, Vector3f at, float u, float v, Vector3f n, int light, int overlay) {
        vc.addVertex(p, at.x, at.y, at.z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(overlay).setLight(light)
                .setNormal(p, n.x, n.y, n.z);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    /**
     * The whole board, from segment 0 to the far end with its lips. NeoForge culls block entity renderers by this box
     * ({@code IBlockEntityRendererExtension#getRenderBoundingBox}, which this method overrides when the NeoForge module
     * compiles the common sources); plain vanilla and Fabric have no such check.
     */
    public AABB getRenderBoundingBox(BoardingPlankBlockEntity be) {
        BlockPos p = be.getBlockPos();
        if (!be.isBase()) {
            return new AABB(p).inflate(0.5);
        }
        Vec3 o = Vec3.atLowerCornerOf(p);
        return new AABB(o.add(be.boardStart()), o.add(be.boardEnd())).inflate(0.75);
    }
}
