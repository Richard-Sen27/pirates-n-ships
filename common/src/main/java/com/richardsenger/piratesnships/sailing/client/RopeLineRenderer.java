package com.richardsenger.piratesnships.sailing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.rope.RopeAnchor;
import com.richardsenger.piratesnships.sailing.rope.RopeAnchorBlockEntity;
import com.richardsenger.piratesnships.sailing.rope.RopeLine;
import com.richardsenger.piratesnships.sailing.rope.RopeLines;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Draws decorative rope lines (RP1, docs/design.md §5.2) from a rope anchor's {@link RopeAnchorBlockEntity}: every rope
 * this end draws ({@link RopeLines#drawsFrom}; the other end must store the rope too) as a sagging catenary of short
 * straight pieces ({@link RopeLine#points}, sag {@code sailing.sails.rope_sag}) between the two anchors' tie points,
 * with the stay's rope texture and thickness. Nothing is drawn while {@code sailing.sails.rope_lines} is off.
 *
 * <p>Registered for the mooring ring's block entity (by the grapple's client class); the cleat's
 * {@link StayClothRenderer} calls {@link #drawLines} for the ropes that are not its sail's stay. On a ship the pose
 * stack carries the ship's pose, so the rope sags toward the ship's own "down".
 */
public class RopeLineRenderer implements BlockEntityRenderer<RopeAnchorBlockEntity> {

    /** Half the thickness of the rope [blocks], as the stay's. */
    private static final float ROPE_HALF = 0.03f;

    public RopeLineRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RopeAnchorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        drawLines(be, List.of(), pose, buffers, light, overlay);
    }

    /**
     * Draws the rope lines of {@code be} except those to {@code skip} (stays drawn straight by the caller). The pose is
     * at the block's minimum corner.
     */
    public static void drawLines(RopeAnchorBlockEntity be, Collection<BlockPos> skip, PoseStack pose, MultiBufferSource buffers,
                                 int light, int overlay) {
        Level level = be.getLevel();
        if (level == null || !be.hasRopes() || !SailingConfig.ROPE_LINES.get()) {
            return;
        }
        BlockPos p = be.getBlockPos();
        Vec3 from = RopeAnchor.point(level, p).subtract(p.getX(), p.getY(), p.getZ());
        double sag = SailingConfig.ROPE_SAG.get();
        VertexConsumer vc = null;
        for (BlockPos t : be.ropeTargets()) {
            if (skip.contains(t) || !RopeLines.drawsFrom(p, t)
                    || !(level.getBlockEntity(t) instanceof RopeAnchorBlockEntity other) || !other.hasRopeTo(p)) {
                continue;
            }
            Vec3 to = RopeAnchor.point(level, t).subtract(p.getX(), p.getY(), p.getZ());
            double[][] pts = RopeLine.points(from.x, from.y, from.z, to.x, to.y, to.z, sag);
            if (vc == null) {
                vc = buffers.getBuffer(RenderType.entityCutoutNoCull(StayClothRenderer.ROPE_TEXTURE));
            }
            PoseStack.Pose last = pose.last();
            for (int i = 0; i + 1 < pts.length; i++) {
                StayClothRenderer.beam(vc, last, new Vector3f((float) pts[i][0], (float) pts[i][1], (float) pts[i][2]),
                        new Vector3f((float) pts[i + 1][0], (float) pts[i + 1][1], (float) pts[i + 1][2]), ROPE_HALF, light, overlay);
            }
        }
    }

    /** The block of {@code be} and every rope line it holds, with the sag below (block coordinates). */
    public static AABB linesBox(RopeAnchorBlockEntity be) {
        BlockPos p = be.getBlockPos();
        AABB box = new AABB(p);
        double sag = SailingConfig.ROPE_SAG.get();
        for (BlockPos t : be.ropeTargets()) {
            double down = RopeLine.sagDepth(t.getX() - p.getX(), t.getZ() - p.getZ(), sag);
            box = box.minmax(new AABB(t)).minmax(new AABB(p).minmax(new AABB(t)).move(0, -down, 0));
        }
        return box.inflate(0.1);
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    /**
     * Rope lines reach beyond the anchor block. NeoForge culls block entity renderers by this box
     * ({@code IBlockEntityRendererExtension#getRenderBoundingBox}, which this method overrides when the NeoForge module
     * compiles the common sources); see {@link YardClothRenderer#getRenderBoundingBox}.
     */
    public AABB getRenderBoundingBox(RopeAnchorBlockEntity be) {
        return linesBox(be);
    }
}
