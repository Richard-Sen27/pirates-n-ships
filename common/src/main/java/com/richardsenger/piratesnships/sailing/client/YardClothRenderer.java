package com.richardsenger.piratesnships.sailing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.block.YardBlock;
import com.richardsenger.piratesnships.sailing.block.YardBlockEntity;
import com.richardsenger.piratesnships.sailing.sail.ClothGeometry;
import com.richardsenger.piratesnships.sailing.sail.SquareSail;
import com.richardsenger.piratesnships.sailing.wind.ClientWind;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.ship.sable.ClientShipPoses;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Draws the cloth of a square sail (docs/design.md §5.2, rule F5a) from its head's {@link YardBlockEntity}: a
 * trapezoid between the two yards, lowered by the trim (furled: a bundle under the upper yard; half: down to half the
 * drop; full: down to the lower yard), hoisted and lowered smoothly over about a second, and bellied out to the
 * downwind side. Both faces are drawn ({@code entityCutoutNoCull}).
 *
 * <p>Works the same on land and on ships: Sable renders the block entities of a sub-level through the vanilla
 * {@code BlockEntityRenderDispatcher} with the ship's pose on the pose stack
 * ({@code sublevel/render/dispatcher/VanillaSubLevelRenderDispatcher#renderBlockEntities} l.219-252,
 * {@code mixinhelpers/sublevel_render/vanilla/VanillaSubLevelBlockEntityRenderer#renderSingleBE}). The cloth is built
 * in the head's local block space; only the downwind side needs the ship's orientation ({@link ClientShipPoses}).
 */
public class YardClothRenderer implements BlockEntityRenderer<YardBlockEntity> {

    public static final ResourceLocation TEXTURE = Constants.id("textures/block/sail_cloth.png");

    /** Hoisting speed: fraction of the drop per second. */
    private static final float HOIST_PER_SECOND = 1.0f;
    /** Half the yard beam's thickness (6 px). */
    private static final float BEAM_HALF = 3f / 16f;
    /** Cells per block of cloth (texture: one copy per block). */
    private static final int CELLS_PER_BLOCK = 2;
    /** |cos| of the wind against the across-yard axis below which the cloth keeps its side (hysteresis). */
    private static final double SIDE_SWITCH = 0.15;

    public YardClothRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(YardBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ClothGeometry g = be.geometry();
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (g == null || level == null || !(state.getBlock() instanceof YardBlock) || g.drop() <= 0) {
            return;
        }
        double now = level.getGameTime() + (double) partialTick;
        float shown = animate(be, (float) SquareSail.drawnFraction(state.getValue(YardBlock.TRIM)), now);
        updateSide(be, g, level, now, partialTick);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f);
        PoseStack.Pose p = pose.last();
        float bottom = shown * g.drop();
        if (bottom > 0.05f) {
            cloth(vc, p, g, be.side, bottom, light, overlay);
        }
        if (shown < 0.999f) {
            bundle(vc, p, g, be.side, 1f - shown, light, overlay);
        }
        pose.popPose();
    }

    /** Moves the shown fraction toward {@code target} at {@link #HOIST_PER_SECOND}. */
    private static float animate(YardBlockEntity be, float target, double now) {
        if (Float.isNaN(be.shownFraction) || Double.isNaN(be.shownTime)) {
            be.shownFraction = target;
        } else {
            float step = (float) (Math.min(Math.max(now - be.shownTime, 0.0), 20.0) / 20.0) * HOIST_PER_SECOND;
            float d = target - be.shownFraction;
            be.shownFraction = Math.abs(d) <= step ? target : be.shownFraction + Math.signum(d) * step;
        }
        be.shownTime = now;
        return be.shownFraction;
    }

    /** The cloth bellies toward the side the wind blows to (in the ship's frame when on a ship). */
    private static void updateSide(YardBlockEntity be, ClothGeometry g, Level level, double now, float partialTick) {
        if (!ClientWind.hasData()) {
            return;
        }
        WindSample w = ClientWind.sample(now);
        if (w.strength() <= 0.01) {
            return;
        }
        Vector3d across = g.alongX() ? new Vector3d(0, 0, 1) : new Vector3d(1, 0, 0);
        Quaterniond q = ClientShipPoses.orientation(level, Vec3.atCenterOf(be.getBlockPos()), partialTick);
        if (q != null) {
            q.transform(across);
        }
        double h = Math.hypot(across.x, across.z);
        if (h < 1.0e-6) {
            return;
        }
        double dot = (across.x * w.dirX() + across.z * w.dirZ()) / h;
        if (dot > SIDE_SWITCH) {
            be.side = 1;
        } else if (dot < -SIDE_SWITCH) {
            be.side = -1;
        }
    }

    /** The drawn cloth from the upper yard down to {@code bottom}, as a grid that follows the standoff profile. */
    private static void cloth(VertexConsumer vc, PoseStack.Pose p, ClothGeometry g, int side, float bottom, int light, int overlay) {
        float width = Math.max(g.upperNeg() + g.upperPos(), g.lowerNeg() + g.lowerPos());
        int cols = Math.max(2, (int) Math.ceil(width * CELLS_PER_BLOCK));
        int rows = Math.max(1, (int) Math.ceil(bottom * CELLS_PER_BLOCK));
        Vector3f[][] pts = new Vector3f[rows + 1][cols + 1];
        for (int i = 0; i <= rows; i++) {
            float v = bottom * i / rows;
            float neg = g.negativeEdge(v);
            float pos = g.positiveEdge(v);
            for (int j = 0; j <= cols; j++) {
                float t = (float) j / cols;
                pts[i][j] = local(g, neg + (pos - neg) * t, -v, side * g.standoff(v, t, bottom));
            }
        }
        Vector3f n = new Vector3f();
        for (int i = 0; i < rows; i++) {
            float v0 = (i % CELLS_PER_BLOCK) / (float) CELLS_PER_BLOCK;
            float v1 = v0 + 1f / CELLS_PER_BLOCK;
            for (int j = 0; j < cols; j++) {
                float u0 = (j % CELLS_PER_BLOCK) / (float) CELLS_PER_BLOCK;
                float u1 = u0 + 1f / CELLS_PER_BLOCK;
                Vector3f a = pts[i][j], b = pts[i][j + 1], c = pts[i + 1][j + 1], d = pts[i + 1][j];
                new Vector3f(b).sub(a).cross(new Vector3f(d).sub(a), n);
                if (n.lengthSquared() < 1.0e-12f) continue;
                n.normalize();
                vertex(vc, p, a, u0, v0, n, light, overlay);
                vertex(vc, p, b, u1, v0, n, light, overlay);
                vertex(vc, p, c, u1, v1, n, light, overlay);
                vertex(vc, p, d, u0, v1, n, light, overlay);
            }
        }
    }

    /** The furled part of the cloth: a flattened roll under the upper yard, thicker the more cloth is gathered. */
    private static void bundle(VertexConsumer vc, PoseStack.Pose p, ClothGeometry g, int side, float gathered, int light, int overlay) {
        float r = (0.05f + 0.1f * gathered * Math.min(1f, g.drop() / 4f)) * Math.min(1f, (g.upperNeg() + g.upperPos()) / 3f + 0.4f);
        float a0 = -g.upperNeg() + 0.15f;
        float a1 = g.upperPos() - 0.15f;
        if (a1 <= a0) return;
        float y1 = -BEAM_HALF + 0.03f;
        float y0 = y1 - 1.6f * r;
        float n0 = side * 0.04f - r;
        float n1 = side * 0.04f + r;
        box(vc, p, g, a0, a1, y0, y1, n0, n1, light, overlay);
    }

    private static void box(VertexConsumer vc, PoseStack.Pose p, ClothGeometry g, float a0, float a1, float y0, float y1,
                            float n0, float n1, int light, int overlay) {
        float len = a1 - a0;
        // bottom, two long sides, top (hidden by the yard mostly), two ends
        quad(vc, p, g, new float[][] {{a0, y0, n0}, {a1, y0, n0}, {a1, y0, n1}, {a0, y0, n1}}, len, light, overlay);
        quad(vc, p, g, new float[][] {{a0, y1, n0}, {a1, y1, n0}, {a1, y0, n0}, {a0, y0, n0}}, len, light, overlay);
        quad(vc, p, g, new float[][] {{a0, y0, n1}, {a1, y0, n1}, {a1, y1, n1}, {a0, y1, n1}}, len, light, overlay);
        quad(vc, p, g, new float[][] {{a0, y1, n1}, {a1, y1, n1}, {a1, y1, n0}, {a0, y1, n0}}, len, light, overlay);
        quad(vc, p, g, new float[][] {{a0, y0, n0}, {a0, y0, n1}, {a0, y1, n1}, {a0, y1, n0}}, 0.25f, light, overlay);
        quad(vc, p, g, new float[][] {{a1, y0, n1}, {a1, y0, n0}, {a1, y1, n0}, {a1, y1, n1}}, 0.25f, light, overlay);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose p, ClothGeometry g, float[][] c, float uLength, int light, int overlay) {
        Vector3f a = local(g, c[0][0], c[0][1], c[0][2]);
        Vector3f b = local(g, c[1][0], c[1][1], c[1][2]);
        Vector3f cc = local(g, c[2][0], c[2][1], c[2][2]);
        Vector3f d = local(g, c[3][0], c[3][1], c[3][2]);
        Vector3f n = new Vector3f(b).sub(a).cross(new Vector3f(d).sub(a));
        if (n.lengthSquared() < 1.0e-12f) return;
        n.normalize();
        float u1 = Math.min(1f, Math.max(0.1f, uLength));
        vertex(vc, p, a, 0f, 0f, n, light, overlay);
        vertex(vc, p, b, u1, 0f, n, light, overlay);
        vertex(vc, p, cc, u1, 0.25f, n, light, overlay);
        vertex(vc, p, d, 0f, 0.25f, n, light, overlay);
    }

    /** Cloth coordinates (along the yard, up, across the yard) to block-local coordinates around the head's center. */
    private static Vector3f local(ClothGeometry g, float along, float up, float across) {
        return g.alongX() ? new Vector3f(along, up, across) : new Vector3f(across, up, along);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, Vector3f at, float u, float v, Vector3f n, int light, int overlay) {
        vc.addVertex(p, at.x, at.y, at.z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(overlay).setLight(light)
                .setNormal(p, n.x, n.y, n.z);
    }

    /** Sails are big: draw them from further away than the default 64 blocks. */
    @Override
    public int getViewDistance() {
        return 192;
    }

    /**
     * The cloth reaches far beyond the head block. NeoForge culls block entity renderers by this box
     * ({@code IBlockEntityRendererExtension#getRenderBoundingBox}, which this method overrides when the NeoForge module
     * compiles the common sources; without it the cloth vanishes whenever the head block leaves the view). Plain
     * vanilla (and later Fabric) has no such check, so on those it is just an unused method.
     */
    public AABB getRenderBoundingBox(YardBlockEntity be) {
        BlockPos p = be.getBlockPos();
        ClothGeometry g = be.geometry();
        if (g == null) {
            return new AABB(p);
        }
        double along = g.maxExtent() + 0.5;
        double across = ClothGeometry.STANDOFF + ClothGeometry.MAX_BELLY + 0.5;
        double ax = g.alongX() ? along : across;
        double az = g.alongX() ? across : along;
        return new AABB(p.getX() + 0.5 - ax, p.getY() - g.drop(), p.getZ() + 0.5 - az,
                p.getX() + 0.5 + ax, p.getY() + 1.0, p.getZ() + 0.5 + az);
    }
}
