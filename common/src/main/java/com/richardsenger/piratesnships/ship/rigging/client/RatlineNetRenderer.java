package com.richardsenger.piratesnships.ship.rigging.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.richardsenger.piratesnships.sailing.client.StayClothRenderer;
import com.richardsenger.piratesnships.ship.rigging.NetFittingBlockEntity;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Point;
import com.richardsenger.piratesnships.ship.rigging.RatlineNets;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import java.util.List;

/**
 * Draws a ratline net (RL2) from its fitting A ({@link NetFittingBlockEntity#isA}) to B as one continuous rope net:
 * two side ropes that leave A's ring, spread to {@link #HALF_WIDTH} either side of the line and close again at B's ring
 * ({@link RatlineNetGeometry}), and a rung across every {@link #RUNG_SPACING} blocks, all square rope beams with the
 * stay's {@code block/rope} texture tiled along their length (v runs in blocks, so the texture never stretches).
 *
 * <p>Drawn in A's block entity space with B's ring relative to A; on a ship Sable renders a sub-level's block
 * entities with the ship's pose on the pose stack, so the net moves with the plot. NeoForge culls block entity
 * renderers by {@link #getRenderBoundingBox}; like the stay and the flag it does not set {@code shouldRenderOffScreen}
 * (Sable's sub-level path renders only per-section block entities).
 */
public class RatlineNetRenderer implements BlockEntityRenderer<NetFittingBlockEntity> {

    /** Half the width of the net between its side ropes [blocks]. */
    public static final double HALF_WIDTH = 0.4;
    /** Distance between two rungs along the net [blocks]. */
    public static final double RUNG_SPACING = 0.5;
    /** Half the thickness of a side rope and of a rung [blocks]. */
    private static final float SIDE_HALF = 0.035f;
    private static final float RUNG_HALF = 0.025f;

    public RatlineNetRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(NetFittingBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null || !be.isA()) {
            return;
        }
        BlockPos p = be.getBlockPos();
        BlockPos other = be.partner();
        Point a = RatlineNets.ring(level, p);
        Point b = other == null ? null : RatlineNets.ring(level, other);
        if (a == null || b == null) {
            return;
        }
        Direction facing = facing(be.getBlockState());
        Point side = RatlineNetRules.side(a, b, facing.getClockWise().getStepX(), facing.getClockWise().getStepZ());
        RatlineNetGeometry net = RatlineNetGeometry.of(local(a, p), local(b, p), side, HALF_WIDTH, RUNG_SPACING);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(StayClothRenderer.ROPE_TEXTURE));
        PoseStack.Pose last = pose.last();
        for (List<Point> rope : List.of(net.left(), net.right())) {
            for (int i = 0; i + 1 < rope.size(); i++) {
                beam(vc, last, rope.get(i), rope.get(i + 1), SIDE_HALF, light, overlay);
            }
        }
        for (Point[] rung : net.rungs()) {
            beam(vc, last, rung[0], rung[1], RUNG_HALF, light, overlay);
        }
    }

    private static Point local(Point v, BlockPos p) {
        return new Point(v.x() - p.getX(), v.y() - p.getY(), v.z() - p.getZ());
    }

    private static Direction facing(BlockState state) {
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
    }

    /** A square rope beam from {@code from} to {@code to}; u round the rope, v along it in blocks (tiled). */
    private static void beam(VertexConsumer vc, PoseStack.Pose p, Point from, Point to, float half, int light, int overlay) {
        Vector3f f = new Vector3f((float) from.x(), (float) from.y(), (float) from.z());
        Vector3f t = new Vector3f((float) to.x(), (float) to.y(), (float) to.z());
        Vector3f d = new Vector3f(t).sub(f);
        float len = d.length();
        if (len < 1.0e-4f) {
            return;
        }
        d.div(len);
        Vector3f a = Math.abs(d.y) > 0.99f ? new Vector3f(1f, 0f, 0f) : new Vector3f(d).cross(0f, 1f, 0f).normalize();
        Vector3f b = new Vector3f(d).cross(a).normalize();
        Vector3f[] corner = {
                new Vector3f(a).add(b).mul(half), new Vector3f(a).sub(b).mul(half),
                new Vector3f(a).negate().sub(b).mul(half), new Vector3f(b).sub(a).mul(half)};
        for (int k = 0; k < 4; k++) {
            Vector3f c0 = corner[k], c1 = corner[(k + 1) % 4];
            Vector3f n = new Vector3f(c0).add(c1).normalize();
            float u0 = k * 0.25f, u1 = u0 + 0.25f;
            vertex(vc, p, new Vector3f(f).add(c0), u0, 0f, n, light, overlay);
            vertex(vc, p, new Vector3f(f).add(c1), u1, 0f, n, light, overlay);
            vertex(vc, p, new Vector3f(t).add(c1), u1, len, n, light, overlay);
            vertex(vc, p, new Vector3f(t).add(c0), u0, len, n, light, overlay);
        }
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
     * The net reaches to fitting B. NeoForge culls block entity renderers by this box
     * ({@code IBlockEntityRendererExtension#getRenderBoundingBox}, which this method overrides when the NeoForge module
     * compiles the common sources); plain vanilla and Fabric have no such check.
     */
    public AABB getRenderBoundingBox(NetFittingBlockEntity be) {
        BlockPos p = be.getBlockPos();
        BlockPos other = be.partner();
        AABB box = new AABB(p);
        return (other == null ? box : box.minmax(new AABB(other))).inflate(HALF_WIDTH + 0.1);
    }
}
