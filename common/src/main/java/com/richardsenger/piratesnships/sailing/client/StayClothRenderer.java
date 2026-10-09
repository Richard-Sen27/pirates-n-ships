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
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.force.SailTypes;
import com.richardsenger.piratesnships.sailing.sail.SailShape;
import com.richardsenger.piratesnships.sailing.sail.SailVisualsConfig;
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
 * <p><b>In the wind (VIS1b).</b> With client {@code sail_visuals.enabled} the cloth moves in its own frame like a
 * square sail's ({@link SailShape#triangle}, {@link SailAirTracker}; on a ship the bow is the synced one, VIS1c, and
 * without it the bow is taken toward the tack until the ship's motion says otherwise); off: the fixed bulge of before.
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

    /** Per-sail looks and the apparent wind (VIS1b). */
    private final SailAirTracker air = new SailAirTracker();
    /** The cloth grid ({@code (i, j)} at {@code i * (n + 1) + j}), refilled per sail and frame; grown for a bigger sail. */
    private float[] px = new float[0];
    private float[] py = new float[0];
    private float[] pz = new float[0];
    private float[] nx = new float[0];
    private float[] ny = new float[0];
    private float[] nz = new float[0];

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
            SailTrim trim = state.getValue(CleatBlock.TRIM);
            float shown = animate(be, (float) TriangularSail.drawnFraction(trim), now);
            Vector3f normal = new Vector3f(g.tackZ(), 0f, -g.tackX());
            if (normal.lengthSquared() > 1.0e-6f) {
                normal.normalize();
                SailShape.Look look = null;
                if (SailVisualsConfig.ENABLED.get()) {
                    // without the ship's synced bow (VIS1c): a stay runs along the ship with its tack forward
                    look = air.look(be, be.side, 1);
                    float hx = (float) Math.hypot(g.tackX(), g.tackZ());
                    be.side = air.update(look, level, Vec3.atCenterOf(p), partialTick, now, SailTypes.FORE_AND_AFT_CURVE, trim,
                            g.tackX() / hx, g.tackZ() / hx, normal.x, normal.z, g.drop(), be.side);
                } else {
                    updateSide(be, normal, level, now, partialTick);
                }
                Vector3f gTack = new Vector3f(g.tackX(), g.tackY(), g.tackZ());
                if (shown * g.drop() > 0.05f) {
                    float bottom = shown * g.drop();
                    int n = grid(gTack, bottom, normal, g, be, look, now);
                    // each buffer is taken right before it is written: asking for another render type ends the batch
                    cloth(buffers.getBuffer(RenderType.entityCutoutNoCull(CLOTH_TEXTURE)), last, n, gTack, bottom, false, light, overlay);
                    if (SailFoot.shown(bottom)) {
                        cloth(buffers.getBuffer(RenderType.entityCutoutNoCull(FOOT_TEXTURE)), last, n, gTack, bottom, true, light, overlay);
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
     * Fills the grid of the drawn triangle head (origin) - tack - clew point {@code (0, -bottom, 0)} and its per-vertex
     * normals, and returns its size {@code n}: points {@code (i, j)} with {@code i + j <= n} at {@code tack * i/n +
     * clew * j/n}, pushed along the cloth's normal. With a {@code look} (VIS1b) by {@link SailShape#triangle} on a
     * finer grid (at least {@code sail_visuals.segments} cells along each edge and half as many per block); without one
     * by the fixed bulge of before, to the downwind side, most in the middle. The grid's rows {@code i + j = const} run
     * parallel to the tack-clew edge.
     */
    private int grid(Vector3f tack, float bottom, Vector3f normal, TriangleCloth g, CleatBlockEntity be, SailShape.Look look,
                     double now) {
        float size = Math.max(tack.length(), bottom);
        int n = Math.max(2, (int) Math.ceil(size * CELLS_PER_BLOCK));
        if (look != null) {
            int segments = SailVisualsConfig.SEGMENTS.get();
            n = Math.max(n, Math.max(segments, (int) Math.ceil(size * segments / 2f)));
        }
        int stride = n + 1;
        ensure(stride * stride);
        double area = 0.5 * Math.hypot(g.tackX(), g.tackZ()) * g.drop();
        float belly = Math.min(MAX_BELLY, BELLY_PER_SIZE * (float) Math.sqrt(area)) * (bottom / g.drop());
        float phase = SailShape.phase(be.getBlockPos().asLong());
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= n - i; j++) {
                float u = (float) i / n, v = (float) j / n;
                float off = look == null ? be.side * 27f * u * v * (1f - u - v) * belly
                        : SailShape.triangle(look, u, v, size, now, phase);
                int k = i * stride + j;
                px[k] = tack.x * u + normal.x * off;
                py[k] = tack.y * u - bottom * v + normal.y * off;
                pz[k] = tack.z * u + normal.z * off;
            }
        }
        java.util.Arrays.fill(nx, 0, stride * stride, 0f);
        java.util.Arrays.fill(ny, 0, stride * stride, 0f);
        java.util.Arrays.fill(nz, 0, stride * stride, 0f);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n - i; j++) {
                int a = i * stride + j, b = a + stride, c = a + 1;
                faceNormal(a, b, c);
                if (j < n - i - 1) {
                    faceNormal(b, b + 1, c);
                }
            }
        }
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= n - i; j++) {
                int k = i * stride + j;
                float l = (float) Math.sqrt(nx[k] * nx[k] + ny[k] * ny[k] + nz[k] * nz[k]);
                if (l > 1.0e-12f) {
                    nx[k] /= l;
                    ny[k] /= l;
                    nz[k] /= l;
                } else {
                    ny[k] = 1f;
                }
            }
        }
        return n;
    }

    /** Adds the normal {@code (b - a) x (c - a)} of one grid triangle to its three corners (area-weighted). */
    private void faceNormal(int a, int b, int c) {
        float ex = px[b] - px[a], ey = py[b] - py[a], ez = pz[b] - pz[a];
        float fx = px[c] - px[a], fy = py[c] - py[a], fz = pz[c] - pz[a];
        float cx = ey * fz - ez * fy, cy = ez * fx - ex * fz, cz = ex * fy - ey * fx;
        nx[a] += cx;
        ny[a] += cy;
        nz[a] += cz;
        nx[b] += cx;
        ny[b] += cy;
        nz[b] += cz;
        nx[c] += cx;
        ny[c] += cy;
        nz[c] += cz;
    }

    private void ensure(int size) {
        if (px.length < size) {
            px = new float[size];
            py = new float[size];
            pz = new float[size];
            nx = new float[size];
            ny = new float[size];
            nz = new float[size];
        }
    }

    /**
     * Draws the grid filled by {@link #grid}. Called twice: {@code foot} false draws the triangles on the plain tile,
     * true those on the foot tile ({@link SailFoot#stayFootTriangle}: wholly within the bottom block above the
     * tack-clew edge).
     */
    private void cloth(VertexConsumer vc, PoseStack.Pose p, int n, Vector3f tack, float bottom, boolean foot, int light, int overlay) {
        float hx = (float) Math.hypot(tack.x, tack.z);
        float ax = hx < 1.0e-6f ? 1f : tack.x / hx;
        float az = hx < 1.0e-6f ? 0f : tack.z / hx;
        int stride = n + 1;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n - i; j++) {
                // the first triangle's highest corner is (i, j), the second's lies one grid row lower
                float hA = SailFoot.heightAboveFoot((float) i / n, (float) j / n, bottom);
                float hB = SailFoot.heightAboveFoot((float) (i + 1) / n, (float) j / n, bottom);
                float hC = SailFoot.heightAboveFoot((float) i / n, (float) (j + 1) / n, bottom);
                int a = i * stride + j, b = a + stride, c = a + 1;
                if (SailFoot.stayFootTriangle(hA, bottom) == foot) {
                    tri(vc, p, a, b, c, hA, hB, hC, ax, az, bottom, light, overlay);
                }
                if (j < n - i - 1) {
                    float hD = SailFoot.heightAboveFoot((float) (i + 1) / n, (float) (j + 1) / n, bottom);
                    if (SailFoot.stayFootTriangle(hB, bottom) == foot) {
                        tri(vc, p, b, b + 1, c, hB, hD, hC, ax, az, bottom, light, overlay);
                    }
                }
            }
        }
    }

    /**
     * One triangle of grid points, as a quad with its last corner doubled. Texture u is the horizontal position along
     * the tack in blocks ({@code (ax, az)}), v counts whole tiles up from the foot ({@link SailFoot#stayV}), so the bands
     * run parallel to the foot.
     */
    private void tri(VertexConsumer vc, PoseStack.Pose p, int a, int b, int c, float ha, float hb, float hc, float ax, float az,
                     float bottom, int light, int overlay) {
        gridVertex(vc, p, a, px[a] * ax + pz[a] * az, SailFoot.stayV(ha, bottom), light, overlay);
        gridVertex(vc, p, b, px[b] * ax + pz[b] * az, SailFoot.stayV(hb, bottom), light, overlay);
        gridVertex(vc, p, c, px[c] * ax + pz[c] * az, SailFoot.stayV(hc, bottom), light, overlay);
        gridVertex(vc, p, c, px[c] * ax + pz[c] * az, SailFoot.stayV(hc, bottom), light, overlay);
    }

    private void gridVertex(VertexConsumer vc, PoseStack.Pose p, int k, float u, float v, int light, int overlay) {
        vc.addVertex(p, px[k], py[k], pz[k]).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(overlay).setLight(light)
                .setNormal(p, nx[k], ny[k], nz[k]);
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
        double m = Math.max(MAX_BELLY, YardClothRenderer.bellyReach());
        return box.minmax(new AABB(Math.min(p.getX(), t.getX()) - m, Math.min(Math.min(p.getY(), t.getY()), p.getY() - drop) - m,
                Math.min(p.getZ(), t.getZ()) - m, Math.max(p.getX(), t.getX()) + 1 + m,
                Math.max(p.getY(), t.getY()) + 1 + m, Math.max(p.getZ(), t.getZ()) + 1 + m));
    }
}
