package com.richardsenger.piratesnships.sailing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.sail.StayLinker;
import com.richardsenger.piratesnships.sailing.sail.StayRules;
import com.richardsenger.piratesnships.sailing.sail.TriangularSails;
import java.util.ArrayList;
import java.util.List;
import com.richardsenger.piratesnships.sailing.block.CleatBlock;
import com.richardsenger.piratesnships.sailing.block.CleatBlockEntity;
import com.richardsenger.piratesnships.sailing.sail.TriangleCloth;
import com.richardsenger.piratesnships.sailing.sail.TriangularSail;
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
 * Draws a stay and the cloth of a triangular sail (docs/design.md §5.2, rule F5b) from the head cleat's
 * {@link CleatBlockEntity}: the stay as a thin rope from the head to the tack, and the cloth as the triangle head -
 * tack - clew point, where the clew point is lowered from the head toward the clew cleat by the trim (furled: a bundle
 * along the stay; half: halfway down; full: down to the clew). Trim changes are hoisted and lowered over about a
 * second, the cloth bellies out to the downwind side (with hysteresis), and both faces are drawn
 * ({@code entityCutoutNoCull}). The textures repeat once per block; the cloth's tiles are counted up from its foot (the
 * tack-clew edge) and run parallel to it, and the bottom block shows the frayed foot tile once the cloth hangs at least
 * two blocks ({@link SailFoot}, ART5). Every other rope of the cleat (RP1: lines to cleats
 * or mooring rings, and a stay without a sail) is drawn with sag by {@link RopeLineRenderer#drawLines}.
 *
 * <p>Like {@link YardClothRenderer} it works on land and on ships (Sable renders a sub-level's block entities with the
 * ship's pose on the pose stack), and it does not set {@code shouldRenderOffScreen} (see the note there); the
 * NeoForge culling box comes from {@link #getRenderBoundingBox}.
 */
public class StayClothRenderer implements BlockEntityRenderer<CleatBlockEntity> {

    public static final ResourceLocation CLOTH_TEXTURE = YardClothRenderer.TEXTURE;
    public static final ResourceLocation FOOT_TEXTURE = YardClothRenderer.FOOT_TEXTURE;
    public static final ResourceLocation ROPE_TEXTURE = Constants.id("textures/block/rope.png");

    /** Hoisting speed: fraction of the drop per second. */
    private static final float HOIST_PER_SECOND = 1.0f;
    /** Cells per block of cloth along each edge. */
    private static final int CELLS_PER_BLOCK = 2;
    /** Half the thickness of the rope [blocks]. */
    private static final float ROPE_HALF = 0.03f;
    /** Bulge at the middle of a full sail, per block of the square root of its area (capped). */
    private static final float BELLY_PER_SIZE = 0.08f;
    private static final float MAX_BELLY = 0.5f;

    public StayClothRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CleatBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !(state.getBlock() instanceof CleatBlock)) {
            return;
        }
        BlockPos p = be.getBlockPos();
        List<BlockPos> stays = straightStays(be);
        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f);
        PoseStack.Pose last = pose.last();
        for (BlockPos target : stays) { // only the higher end of a stay draws it
            Vector3f tack = new Vector3f(target.getX() - p.getX(), target.getY() - p.getY(), target.getZ() - p.getZ());
            beam(buffers.getBuffer(RenderType.entityCutoutNoCull(ROPE_TEXTURE)), last, new Vector3f(), tack, ROPE_HALF, light, overlay);
        }
        TriangleCloth g = be.cloth();
        if (g != null && g.drop() > 0) {
            double now = level.getGameTime() + (double) partialTick;
            float shown = animate(be, (float) TriangularSail.drawnFraction(state.getValue(CleatBlock.TRIM)), now);
            Vector3f normal = new Vector3f(g.tackZ(), 0f, -g.tackX());
            if (normal.lengthSquared() > 1.0e-6f) {
                normal.normalize();
                updateSide(be, normal, level, now, partialTick);
                Vector3f gTack = new Vector3f(g.tackX(), g.tackY(), g.tackZ());
                if (shown * g.drop() > 0.05f) {
                    Vector3f out = normal.mul(be.side, new Vector3f());
                    float bottom = shown * g.drop();
                    // each buffer is taken right before it is written: asking for another render type ends the batch
                    cloth(buffers.getBuffer(RenderType.entityCutoutNoCull(CLOTH_TEXTURE)), last, gTack, bottom, out, g, false, light, overlay);
                    if (SailFoot.shown(bottom)) {
                        cloth(buffers.getBuffer(RenderType.entityCutoutNoCull(FOOT_TEXTURE)), last, gTack, bottom, out, g, true, light, overlay);
                    }
                }
                if (shown < 0.999f) {
                    float r = 0.04f + 0.1f * (1f - shown) * Math.min(1f, g.drop() / 4f);
                    Vector3f off = normal.mul(be.side * 0.05f, new Vector3f());
                    beam(buffers.getBuffer(RenderType.entityCutoutNoCull(CLOTH_TEXTURE)), last, new Vector3f(off),
                            new Vector3f(gTack).add(off), r, light, overlay);
                }
            }
        }
        pose.popPose();
        RopeLineRenderer.drawLines(be, stays, pose, buffers, light, overlay); // every other rope is a line (RP1)
    }

    /**
     * The ropes this cleat draws straight, as stays from the head: with rope lines on only the stay of the sail it
     * heads (from the cloth's tack); with them off every rope down to a cleat that passes the stay rule, as before RP1.
     */
    static List<BlockPos> straightStays(CleatBlockEntity be) {
        BlockPos p = be.getBlockPos();
        List<BlockPos> out = new ArrayList<>(1);
        if (SailingConfig.ROPE_LINES.get()) {
            BlockPos tack = be.clothTack();
            if (tack != null && be.hasRopeTo(tack)) {
                out.add(tack);
            }
            return out;
        }
        Level level = be.getLevel();
        StayRules rules = SailingConfig.stayRules();
        for (BlockPos t : be.ropeTargets()) {
            if (t.getY() < p.getY() && level != null && level.getBlockState(t).getBlock() instanceof CleatBlock
                    && StayLinker.check(TriangularSails.point(p), TriangularSails.point(t), rules) == StayLinker.Check.OK) {
                out.add(t);
            }
        }
        return out;
    }

    /** Moves the shown fraction toward {@code target} at {@link #HOIST_PER_SECOND}. */
    private static float animate(CleatBlockEntity be, float target, double now) {
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

    /** The cloth bellies toward the side the wind blows to (in the ship's frame when on a ship), see {@link ClothSide}. */
    private static void updateSide(CleatBlockEntity be, Vector3f normal, Level level, double now, float partialTick) {
        if (!ClientWind.hasData()) {
            return;
        }
        WindSample w = ClientWind.sample(now);
        if (w.strength() <= 0.01) {
            return;
        }
        Quaterniond q = ClientShipPoses.orientation(level, Vec3.atCenterOf(be.getBlockPos()), partialTick);
        be.side = ClothSide.side(be.side, new Vector3d(normal.x, normal.y, normal.z), q, w.dirX(), w.dirZ());
    }

    /**
     * The drawn triangle head (origin) - tack - clew point {@code (0, -bottom, 0)}, as a grid of small triangles that
     * bulges along {@code out} (the downwind normal), most in the middle. Called twice: {@code foot} false draws the
     * triangles on the plain tile, true those on the foot tile ({@link SailFoot#stayFootTriangle}: wholly within the
     * bottom block above the tack-clew edge). The grid's rows {@code i + j = const} run parallel to that edge.
     */
    private static void cloth(VertexConsumer vc, PoseStack.Pose p, Vector3f tack, float bottom, Vector3f out, TriangleCloth g,
                              boolean foot, int light, int overlay) {
        Vector3f clew = new Vector3f(0f, -bottom, 0f);
        float size = Math.max(tack.length(), bottom);
        int n = Math.max(2, (int) Math.ceil(size * CELLS_PER_BLOCK));
        double area = 0.5 * Math.hypot(g.tackX(), g.tackZ()) * g.drop();
        float belly = Math.min(MAX_BELLY, BELLY_PER_SIZE * (float) Math.sqrt(area)) * (bottom / g.drop());
        float hx = (float) Math.hypot(tack.x, tack.z);
        Vector3f along = hx < 1.0e-6f ? new Vector3f(1f, 0f, 0f) : new Vector3f(tack.x / hx, 0f, tack.z / hx);
        Vector3f[][] pts = new Vector3f[n + 1][];
        for (int i = 0; i <= n; i++) {
            pts[i] = new Vector3f[n - i + 1];
            for (int j = 0; j <= n - i; j++) {
                float u = (float) i / n, v = (float) j / n;
                float bulge = 27f * u * v * (1f - u - v) * belly;
                pts[i][j] = new Vector3f(tack).mul(u).add(new Vector3f(clew).mul(v)).add(new Vector3f(out).mul(bulge));
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n - i; j++) {
                // the first triangle's highest corner is (i, j), the second's lies one grid row lower
                float hA = SailFoot.heightAboveFoot((float) i / n, (float) j / n, bottom);
                float hB = SailFoot.heightAboveFoot((float) (i + 1) / n, (float) j / n, bottom);
                float hC = SailFoot.heightAboveFoot((float) i / n, (float) (j + 1) / n, bottom);
                if (SailFoot.stayFootTriangle(hA, bottom) == foot) {
                    tri(vc, p, pts[i][j], pts[i + 1][j], pts[i][j + 1], hA, hB, hC, along, bottom, light, overlay);
                }
                if (j < n - i - 1) {
                    float hD = SailFoot.heightAboveFoot((float) (i + 1) / n, (float) (j + 1) / n, bottom);
                    if (SailFoot.stayFootTriangle(hB, bottom) == foot) {
                        tri(vc, p, pts[i + 1][j], pts[i + 1][j + 1], pts[i][j + 1], hB, hD, hC, along, bottom, light, overlay);
                    }
                }
            }
        }
    }

    /**
     * One triangle, as a quad with its last corner doubled. Texture u is the horizontal position along the tack in
     * blocks, v counts whole tiles up from the foot ({@link SailFoot#stayV}), so the bands run parallel to the foot.
     */
    private static void tri(VertexConsumer vc, PoseStack.Pose p, Vector3f a, Vector3f b, Vector3f c, float ha, float hb, float hc,
                            Vector3f along, float bottom, int light, int overlay) {
        Vector3f n = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
        if (n.lengthSquared() < 1.0e-12f) return;
        n.normalize();
        vertex(vc, p, a, a.dot(along), SailFoot.stayV(ha, bottom), n, light, overlay);
        vertex(vc, p, b, b.dot(along), SailFoot.stayV(hb, bottom), n, light, overlay);
        vertex(vc, p, c, c.dot(along), SailFoot.stayV(hc, bottom), n, light, overlay);
        vertex(vc, p, c, c.dot(along), SailFoot.stayV(hc, bottom), n, light, overlay);
    }

    /** A square beam of half width {@code half} from {@code from} to {@code to} (four sides, open ends). */
    static void beam(VertexConsumer vc, PoseStack.Pose p, Vector3f from, Vector3f to, float half, int light, int overlay) {
        Vector3f d = new Vector3f(to).sub(from);
        float len = d.length();
        if (len < 1.0e-4f) return;
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
            vertex(vc, p, new Vector3f(from).add(c0), u0, 0f, n, light, overlay);
            vertex(vc, p, new Vector3f(from).add(c1), u1, 0f, n, light, overlay);
            vertex(vc, p, new Vector3f(to).add(c1), u1, len, n, light, overlay);
            vertex(vc, p, new Vector3f(to).add(c0), u0, len, n, light, overlay);
        }
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
     * The stay and the cloth reach far beyond the head cleat. NeoForge culls block entity renderers by this box
     * ({@code IBlockEntityRendererExtension#getRenderBoundingBox}, which this method overrides when the NeoForge module
     * compiles the common sources); see {@link YardClothRenderer#getRenderBoundingBox}.
     */
    public AABB getRenderBoundingBox(CleatBlockEntity be) {
        BlockPos p = be.getBlockPos();
        AABB box = RopeLineRenderer.linesBox(be);
        TriangleCloth g = be.cloth();
        if (g == null) {
            return box;
        }
        BlockPos t = p.offset(g.tackX(), g.tackY(), g.tackZ());
        int drop = g.drop();
        return box.minmax(new AABB(Math.min(p.getX(), t.getX()) - MAX_BELLY, Math.min(Math.min(p.getY(), t.getY()), p.getY() - drop) - MAX_BELLY,
                Math.min(p.getZ(), t.getZ()) - MAX_BELLY, Math.max(p.getX(), t.getX()) + 1 + MAX_BELLY,
                Math.max(p.getY(), t.getY()) + 1 + MAX_BELLY, Math.max(p.getZ(), t.getZ()) + 1 + MAX_BELLY));
    }
}
