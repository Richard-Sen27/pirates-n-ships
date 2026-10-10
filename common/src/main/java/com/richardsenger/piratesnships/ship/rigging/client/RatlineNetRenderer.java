package com.richardsenger.piratesnships.ship.rigging.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.richardsenger.piratesnships.sailing.client.StayClothRenderer;
import com.richardsenger.piratesnships.ship.rigging.BoardingNetRules;
import com.richardsenger.piratesnships.ship.rigging.BoardingNets;
import com.richardsenger.piratesnships.ship.rigging.NetFittingBlockEntity;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Point;
import com.richardsenger.piratesnships.ship.rigging.RatlineNets;
import com.richardsenger.piratesnships.ship.rigging.RatlineNets.Net;
import com.richardsenger.piratesnships.ship.rigging.RatlineNets.Strand;
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
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * Draws the ratline nets of a hub (RL2, RL4) from the hub's block entity (the hoop, which knows all its strands,
 * {@link RatlineNets#hubStrands}). Strands in a pair ({@link NetFittingBlockEntity#joinsNext}, {@code joined}) are
 * one sheet (RL4's fan, RL5's rectangle or trapezoid): every strand's rope from its foot to its hoop, the outer ones the
 * sides, and from each pair's owner a rung every {@link #RUNG_SPACING} blocks across to its partner
 * ({@link RatlineNetGeometry#pairRungs}), whose hoop may be another one (RL5), so the rungs run across the whole sheet. A lone strand is drawn as RL2 drew it, from its ring A to B as one continuous rope net:
 * two side ropes that leave A's ring, spread to {@link #HALF_WIDTH} either side of the line and close again at B's ring
 * ({@link RatlineNetGeometry}), and a rung across every {@link #RUNG_SPACING} blocks, all square rope beams with the
 * stay's {@code block/rope} texture tiled along their length (v runs in blocks, so the texture never stretches).
 *
 * <p>A boarding net (RAIL5, {@link BoardingNets}) is drawn upright along the rail instead ({@link BoardingNetRules#mesh}):
 * a foot rope on the rail, a top rope a block up sagging in the middle, two ropes between and uprights every
 * {@link #BOARDING_SPACING} blocks.
 *
 * <p>Drawn in the hub's block entity space with every ring relative to it; on a ship Sable renders a sub-level's block
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
    /** RAIL5: distance between the uprights of a boarding net [blocks]. */
    public static final double BOARDING_SPACING = 1.0 / 3.0;

    public RatlineNetRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(NetFittingBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null || !be.hasNet()) {
            return;
        }
        BlockPos p = be.getBlockPos();
        List<Strand> strands = RatlineNets.hubStrands(level, p);
        if (strands.isEmpty()) {
            return;
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(StayClothRenderer.ROPE_TEXTURE));
        PoseStack.Pose last = pose.last();
        // a strand in no pair keeps RL2's look; a joined one is one rope, and each pair it owns gets the rungs across to
        // its partner, whose hub may be another hoop of the sheet (RL5)
        for (Strand s : strands) {
            boolean owns = be.joinsNext(s.foot());
            if (!owns && !be.joined(s.foot())) {
                single(level, s.net(), p, vc, last, light, overlay);
                continue;
            }
            Point foot = local(s.footRing(), p), hub = local(s.hubRing(), p);
            beam(vc, last, foot, hub, SIDE_HALF, light, overlay);
            Strand partner = owns ? partner(level, be.partnerOf(s.foot())) : null;
            if (partner == null) {
                continue;
            }
            for (Point[] rung : RatlineNetGeometry.pairRungs(foot, hub, local(partner.footRing(), p),
                    local(partner.hubRing(), p), RUNG_SPACING)) {
                beam(vc, last, rung[0], rung[1], RUNG_HALF, light, overlay);
            }
        }
    }

    /** The strand whose foot is {@code foot} (a chainplate or latch holds one), or null. */
    private static @Nullable Strand partner(Level level, @Nullable BlockPos foot) {
        Net n = foot == null ? null : RatlineNets.netOf(level, foot);
        return n == null ? null : RatlineNets.strand(level, n);
    }

    /** A lone strand (RL2's look): two side ropes and rungs, the side taken from fitting A's facing for a vertical net. */
    private static void single(Level level, Net n, BlockPos p, VertexConsumer vc, PoseStack.Pose last, int light, int overlay) {
        if (BoardingNets.isBoarding(level, n)) {
            // RAIL5: a boarding net hangs upright from the rail to a block above it, its top rope sagging
            for (Point[] rope : BoardingNetRules.mesh(local(n.ringA(), p), local(n.ringB(), p), BOARDING_SPACING)) {
                beam(vc, last, rope[0], rope[1], RUNG_HALF, light, overlay);
            }
            return;
        }
        Direction facing = facing(level.getBlockState(n.a()));
        Point side = RatlineNetRules.side(n.ringA(), n.ringB(), facing.getClockWise().getStepX(), facing.getClockWise().getStepZ());
        RatlineNetGeometry net = RatlineNetGeometry.of(local(n.ringA(), p), local(n.ringB(), p), side, HALF_WIDTH, RUNG_SPACING);
        for (List<Point> rope : List.of(net.left(), net.right())) {
            for (int k = 0; k + 1 < rope.size(); k++) {
                beam(vc, last, rope.get(k), rope.get(k + 1), SIDE_HALF, light, overlay);
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
     * The nets reach to every fitting the hub holds a net to. NeoForge culls block entity renderers by this box
     * ({@code IBlockEntityRendererExtension#getRenderBoundingBox}, which this method overrides when the NeoForge module
     * compiles the common sources); plain vanilla and Fabric have no such check.
     */
    public AABB getRenderBoundingBox(NetFittingBlockEntity be) {
        BlockPos p = be.getBlockPos();
        AABB box = new AABB(p);
        for (NetFittingBlockEntity.Link l : be.links()) {
            box = box.minmax(new AABB(l.other()));
            if (l.partner() != null) {
                // RL5: the rungs reach to the partner strand, up to its own hub
                box = box.minmax(new AABB(l.partner()));
                if (be.getLevel() != null && be.getLevel().getBlockEntity(l.partner()) instanceof NetFittingBlockEntity f
                        && f.partner() != null) {
                    box = box.minmax(new AABB(f.partner()));
                }
            }
        }
        return box.inflate(HALF_WIDTH + 0.1);
    }
}
