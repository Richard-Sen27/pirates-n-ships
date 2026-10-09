package com.richardsenger.piratesnships.sailing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.block.YardBlock;
import com.richardsenger.piratesnships.sailing.block.YardBlockEntity;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.force.SailTypes;
import com.richardsenger.piratesnships.sailing.sail.ClothGeometry;
import com.richardsenger.piratesnships.sailing.sail.SailBanner;
import com.richardsenger.piratesnships.sailing.sail.SailShape;
import com.richardsenger.piratesnships.sailing.sail.SailVisualsConfig;
import com.richardsenger.piratesnships.sailing.sail.SquareSail;
import com.richardsenger.piratesnships.sailing.ship.BowFrame;
import com.richardsenger.piratesnships.sailing.ship.ClientShipBows;
import com.richardsenger.piratesnships.sailing.wind.ClientWind;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.ship.sable.ClientShipPoses;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;

/**
 * Draws the cloth of a square sail (docs/design.md §5.2, rule F5a) from its head's {@link YardBlockEntity}: a
 * trapezoid between the two yards, lowered by the trim (furled: a bundle under the upper yard; half: down to half the
 * drop; full: down to the lower yard), hoisted and lowered smoothly over about a second, and bellied out to the
 * downwind side. Both faces are drawn ({@code entityCutoutNoCull}). The bottom block of a cloth at least two blocks
 * tall shows the frayed foot tile ({@link SailFoot}, ART5); the bundle keeps the plain tile.
 *
 * <p><b>In the wind (VIS1b).</b> With client {@code sail_visuals.enabled} the cloth moves: it bellies to leeward while
 * it draws, flutters while it luffs, sags in a calm ({@link SailShape}), from the apparent wind on the sail
 * ({@link SailAirTracker}); the grid is finer and its normals follow the surface. Off: the fixed belly of before.
 *
 * <p>Works the same on land and on ships: Sable renders the block entities of a sub-level through the vanilla
 * {@code BlockEntityRenderDispatcher} with the ship's pose on the pose stack
 * ({@code sublevel/render/dispatcher/VanillaSubLevelRenderDispatcher#renderBlockEntities} l.219-252,
 * {@code mixinhelpers/sublevel_render/vanilla/VanillaSubLevelBlockEntityRenderer#renderSingleBE}). The cloth is built
 * in the head's local block space; only the downwind side needs the ship's orientation ({@link ClientShipPoses}).
 *
 * <p><b>Dye and banner (SAIL2).</b> The cloth, hanging or furled, is drawn with the head's synced tint
 * ({@link YardBlockEntity#clothTint}) as vertex colour over the canvas weave. A banner shown on the sail
 * ({@link YardBlockEntity#shownLayers}) has its pattern layers drawn over the cloth's own grid, as FLG2 draws them on
 * the flag ({@code FlagClothRenderer}): each layer's sprite from vanilla's banner atlas ({@link Sheets#getBannerMaterial})
 * tinted with its dye, in vanilla's banner pattern render type ({@link Sheets#bannerSheet()}: translucent, no culling,
 * no depth write), mapped upright and scaled to the cloth by {@link SailBanner}, lifted a hair off both faces along the
 * cloth's normals ({@link #LAYER_LIFT}, a little more per layer), so it ripples and bellies with the cloth and the back
 * shows it mirrored. Client {@code sail_visuals.banner_layers} off: the base colour only.
 */
public class YardClothRenderer implements BlockEntityRenderer<YardBlockEntity> {

    public static final ResourceLocation TEXTURE = Constants.id("textures/block/sail_cloth.png");
    /** The bottom block of a hanging cloth (ART5): the plain tile with a frayed foot in its bottom rows. */
    public static final ResourceLocation FOOT_TEXTURE = Constants.id("textures/block/sail_cloth_foot.png");

    /** Hoisting speed: fraction of the drop per second. */
    private static final float HOIST_PER_SECOND = 1.0f;
    /** Half the yard beam's thickness (6 px). */
    private static final float BEAM_HALF = 3f / 16f;
    /** Cells per block of cloth (texture: one copy per block) without VIS1b; the moving cloth refines each cell. */
    private static final int CELLS_PER_BLOCK = 2;
    /** How far the first pattern layer floats off each face of the cloth, and how much more each further layer [blocks]. */
    static final float LAYER_LIFT = 0.01f;
    static final float LAYER_STEP = 0.002f;

    /** Per-sail looks and the apparent wind (VIS1b). */
    private final SailAirTracker air = new SailAirTracker();
    /** The cloth grid, refilled per sail and frame (no allocation while drawing; grown for a bigger sail). */
    private float[] px = new float[0];
    private float[] py = new float[0];
    private float[] pz = new float[0];
    private float[] nx = new float[0];
    private float[] ny = new float[0];
    private float[] nz = new float[0];
    /** The cloth's tint for this frame's sail (SAIL2), as 0..255 channels. */
    private int tintR = 255, tintG = 255, tintB = 255;
    /** The size of the grid {@link #cloth} filled last. */
    private int gridRows, gridCols;

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
        SailTrim trim = state.getValue(YardBlock.TRIM);
        float shown = animate(be, (float) SquareSail.drawnFraction(trim), now);
        SailShape.Look look = null;
        if (SailVisualsConfig.ENABLED.get()) {
            look = air.look(be, be.side, 0);
            Vector3d out = ClothSide.squareSailOut(g.alongX());
            // without the ship's synced bow (VIS1c): the yards run across the ship, so the bow lies along the out axis
            be.side = air.update(look, level, Vec3.atCenterOf(be.getBlockPos()), partialTick, now, SailTypes.SQUARE_CURVE, trim,
                    out.x, out.z, out.x, out.z, g.drop(), be.side);
        } else {
            updateSide(be, g, level, now, partialTick);
        }
        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f);
        PoseStack.Pose p = pose.last();
        float bottom = shown * g.drop();
        int tint = be.clothTint();
        tintR = (tint >> 16) & 0xFF;
        tintG = (tint >> 8) & 0xFF;
        tintB = tint & 0xFF;
        if (bottom > 0.05f) {
            cloth(buffers, p, g, be, look, bottom, now, light, overlay);
            if (SailVisualsConfig.BANNER_LAYERS.get()) {
                design(buffers, p, g, be, bottom, light, overlay);
            }
        }
        if (shown < 0.999f) {
            bundle(buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), p, g, be.side, 1f - shown, tint, light, overlay);
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

    /**
     * The cloth bellies toward the side the wind blows to (in the ship's frame when on a ship), see {@link ClothSide};
     * used with {@code sail_visuals.enabled} off (on, {@link SailAirTracker} does it with the apparent wind).
     */
    private static void updateSide(YardBlockEntity be, ClothGeometry g, Level level, double now, float partialTick) {
        if (!ClientWind.hasData()) {
            return;
        }
        WindSample w = ClientWind.sample(now);
        if (w.strength() <= 0.01) {
            return;
        }
        Quaterniond q = ClientShipPoses.orientation(level, Vec3.atCenterOf(be.getBlockPos()), partialTick);
        be.side = ClothSide.side(be.side, ClothSide.squareSailOut(g.alongX()), q, w.dirX(), w.dirZ());
    }

    /**
     * The drawn cloth from the upper yard down to {@code bottom}, as a grid that follows the standoff profile. The
     * texture's tiles are counted up from the bottom edge, and the last block of rows takes the foot tile
     * ({@link SailFoot}). Each buffer is taken right before its rows are written: a buffer source ends the previous
     * shared batch when another render type is asked for.
     *
     * <p>With a {@code look} (VIS1b) every cell of the plain grid is split further (at least
     * {@code sail_visuals.segments} columns across, half as many rows per block down) and every point stands off by
     * the clearance from the yards ({@link ClothGeometry#clearance}) plus {@link SailShape#square}; without one, the
     * fixed profile of {@link ClothGeometry#standoff} on the plain grid. Texture tiles stay one per block both ways.
     * Normals are per vertex, summed over the cells around it, so the belly shades smoothly.
     */
    private void cloth(MultiBufferSource buffers, PoseStack.Pose p, ClothGeometry g, YardBlockEntity be, SailShape.Look look,
                       float bottom, double now, int light, int overlay) {
        float width = Math.max(g.upperNeg() + g.upperPos(), g.lowerNeg() + g.lowerPos());
        int cols0 = Math.max(2, (int) Math.ceil(width * CELLS_PER_BLOCK));
        int rows0 = Math.max(1, (int) Math.ceil(bottom * CELLS_PER_BLOCK));
        int segments = look == null ? 0 : SailVisualsConfig.SEGMENTS.get();
        int kc = Math.max(1, (segments + cols0 - 1) / cols0);
        int kr = Math.max(1, segments / (2 * CELLS_PER_BLOCK));
        int cols = cols0 * kc;
        int rows = rows0 * kr;
        int rowsPerBlock = CELLS_PER_BLOCK * kr;
        int stride = cols + 1;
        ensure((rows + 1) * stride);
        boolean footFree = !g.hangsToLowerYard(bottom);
        float phase = SailShape.phase(be.getBlockPos().asLong());
        for (int i = 0; i <= rows; i++) {
            float s = (float) i / rows;
            float v = bottom * s;
            float neg = g.negativeEdge(v);
            float pos = g.positiveEdge(v);
            float clearance = g.clearance(v, bottom);
            for (int j = 0; j <= cols; j++) {
                float t = (float) j / cols;
                float off = look == null ? be.side * g.standoff(v, t, bottom)
                        : look.side * clearance + SailShape.square(look, t, s, footFree, bottom, now, phase);
                float along = neg + (pos - neg) * t;
                int k = i * stride + j;
                px[k] = g.alongX() ? along : off;
                py[k] = -v;
                pz[k] = g.alongX() ? off : along;
            }
        }
        normals(rows, cols);
        gridRows = rows;
        gridCols = cols;
        int footFrom = rows;
        while (footFrom > 0 && SailFoot.yardFootRow(footFrom - 1, rows, rowsPerBlock, bottom)) {
            footFrom--;
        }
        int colsPerTile = CELLS_PER_BLOCK * kc;
        rows(buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), p, 0, footFrom, rows, cols, rowsPerBlock, colsPerTile,
                light, overlay);
        if (footFrom < rows) {
            rows(buffers.getBuffer(RenderType.entityCutoutNoCull(FOOT_TEXTURE)), p, footFrom, rows, rows, cols, rowsPerBlock,
                    colsPerTile, light, overlay);
        }
    }

    private void ensure(int n) {
        if (px.length < n) {
            px = new float[n];
            py = new float[n];
            pz = new float[n];
            nx = new float[n];
            ny = new float[n];
            nz = new float[n];
        }
    }

    /** Per-vertex normals of the grid: the sum of the normals of the cells around each vertex, normalised. */
    private void normals(int rows, int cols) {
        int stride = cols + 1;
        int n = (rows + 1) * stride;
        java.util.Arrays.fill(nx, 0, n, 0f);
        java.util.Arrays.fill(ny, 0, n, 0f);
        java.util.Arrays.fill(nz, 0, n, 0f);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                int a = i * stride + j, b = a + 1, d = a + stride, c = d + 1;
                // (c - a) x (d - b): the cell's normal, the same way round as the flat (b - a) x (d - a) of before
                float ex = px[c] - px[a], ey = py[c] - py[a], ez = pz[c] - pz[a];
                float fx = px[d] - px[b], fy = py[d] - py[b], fz = pz[d] - pz[b];
                float cx = ey * fz - ez * fy, cy = ez * fx - ex * fz, cz = ex * fy - ey * fx;
                add(a, cx, cy, cz);
                add(b, cx, cy, cz);
                add(c, cx, cy, cz);
                add(d, cx, cy, cz);
            }
        }
        for (int k = 0; k < n; k++) {
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

    private void add(int k, float x, float y, float z) {
        nx[k] += x;
        ny[k] += y;
        nz[k] += z;
    }

    /**
     * Grid rows {@code from} (inclusive) to {@code to} (exclusive) of {@code rows}: one texture tile per block, i.e.
     * per {@code rowsPerBlock} rows down and {@code colsPerTile} columns across.
     */
    private void rows(VertexConsumer vc, PoseStack.Pose p, int from, int to, int rows, int cols, int rowsPerBlock, int colsPerTile,
                      int light, int overlay) {
        int stride = cols + 1;
        for (int i = from; i < to; i++) {
            float v0 = SailFoot.yardV0(i, rows, rowsPerBlock);
            float v1 = v0 + 1f / rowsPerBlock;
            for (int j = 0; j < cols; j++) {
                float u0 = (j % colsPerTile) / (float) colsPerTile;
                float u1 = u0 + 1f / colsPerTile;
                int a = i * stride + j, b = a + 1, d = a + stride, c = d + 1;
                vertex(vc, p, a, u0, v0, light, overlay);
                vertex(vc, p, b, u1, v0, light, overlay);
                vertex(vc, p, c, u1, v1, light, overlay);
                vertex(vc, p, d, u0, v1, light, overlay);
            }
        }
    }

    private void vertex(VertexConsumer vc, PoseStack.Pose p, int k, float u, float v, int light, int overlay) {
        vc.addVertex(p, px[k], py[k], pz[k]).setColor(tintR, tintG, tintB, 255).setUv(u, v).setOverlay(overlay).setLight(light)
                .setNormal(p, nx[k], ny[k], nz[k]);
    }

    /**
     * The shown banner's pattern layers over the grid {@link #cloth} just filled (SAIL2, see the class comment): every
     * cell of the cloth down to {@link SailBanner#clipDepth} (the last row cut there), on both faces, the same texels at
     * the same places. The design reads right from the face toward the ship's bow ({@link SailBanner#flipAcross}).
     */
    private void design(MultiBufferSource buffers, PoseStack.Pose p, ClothGeometry g, YardBlockEntity be, float bottom,
                        int light, int overlay) {
        List<BannerPatternLayers.Layer> layers = be.shownLayers();
        float clip = SailBanner.clipDepth(bottom, SailFoot.shown(bottom));
        if (layers.isEmpty() || clip <= 0.01f) {
            return;
        }
        float designDepth = SailBanner.designDepth(g.drop(), SailFoot.shown(g.drop()));
        BowFrame bow = be.getLevel() == null ? null
                : ClientShipBows.INSTANCE.bow(ClientShipPoses.shipId(be.getLevel(), Vec3.atCenterOf(be.getBlockPos())));
        boolean flip = SailBanner.flipAcross(g.alongX(), bow == null ? 0 : bow.dx(), bow == null ? 0 : bow.dz());
        int rows = gridRows, cols = gridCols, stride = cols + 1;
        VertexConsumer vc = buffers.getBuffer(Sheets.bannerSheet());
        for (int l = 0; l < layers.size(); l++) {
            BannerPatternLayers.Layer layer = layers.get(l);
            TextureAtlasSprite sprite = Sheets.getBannerMaterial(layer.pattern()).sprite();
            int c = layer.color().getTextureDiffuseColor();
            int r = (c >> 16) & 0xFF, gr = (c >> 8) & 0xFF, b = c & 0xFF;
            float lift = LAYER_LIFT + LAYER_STEP * l;
            for (int i = 0; i < rows; i++) {
                float d0 = bottom * i / rows, d1 = bottom * (i + 1) / rows;
                if (d0 >= clip) {
                    break;
                }
                float w = d1 > clip ? (clip - d0) / (d1 - d0) : 1f;
                float v0 = sprite.getV(SailBanner.patternV(d0, designDepth));
                float v1 = sprite.getV(SailBanner.patternV(d0 + (d1 - d0) * w, designDepth));
                for (int j = 0; j < cols; j++) {
                    float u0 = sprite.getU(SailBanner.patternU((float) j / cols, flip));
                    float u1 = sprite.getU(SailBanner.patternU((float) (j + 1) / cols, flip));
                    int a = i * stride + j, bb = a + 1, d = a + stride, cc = d + 1;
                    // front: the grid's own winding; back: the same corners the other way round
                    designVertex(vc, p, a, a, 0f, lift, u0, v0, r, gr, b, light, overlay);
                    designVertex(vc, p, bb, bb, 0f, lift, u1, v0, r, gr, b, light, overlay);
                    designVertex(vc, p, bb, cc, w, lift, u1, v1, r, gr, b, light, overlay);
                    designVertex(vc, p, a, d, w, lift, u0, v1, r, gr, b, light, overlay);
                    designVertex(vc, p, a, d, w, -lift, u0, v1, r, gr, b, light, overlay);
                    designVertex(vc, p, bb, cc, w, -lift, u1, v1, r, gr, b, light, overlay);
                    designVertex(vc, p, bb, bb, 0f, -lift, u1, v0, r, gr, b, light, overlay);
                    designVertex(vc, p, a, a, 0f, -lift, u0, v0, r, gr, b, light, overlay);
                }
            }
        }
    }

    /**
     * A design vertex between grid points {@code k0} and {@code k1} at weight {@code w}, moved {@code off} along the
     * cloth's normal there (the normal turned to that face).
     */
    private void designVertex(VertexConsumer vc, PoseStack.Pose p, int k0, int k1, float w, float off, float u, float v,
                              int r, int g, int b, int light, int overlay) {
        float x = px[k0] + (px[k1] - px[k0]) * w, y = py[k0] + (py[k1] - py[k0]) * w, z = pz[k0] + (pz[k1] - pz[k0]) * w;
        float mx = nx[k0] + (nx[k1] - nx[k0]) * w, my = ny[k0] + (ny[k1] - ny[k0]) * w, mz = nz[k0] + (nz[k1] - nz[k0]) * w;
        float len = (float) Math.sqrt(mx * mx + my * my + mz * mz);
        if (len > 1.0e-6f) {
            mx /= len;
            my /= len;
            mz /= len;
        }
        float s = Math.signum(off);
        vc.addVertex(p, x + mx * off, y + my * off, z + mz * off).setColor(r, g, b, 255).setUv(u, v).setOverlay(overlay)
                .setLight(light).setNormal(p, mx * s, my * s, mz * s);
    }

    /** The furled part of the cloth: a flattened roll under the upper yard, thicker the more cloth is gathered. */
    private static void bundle(VertexConsumer vc, PoseStack.Pose p, ClothGeometry g, int side, float gathered, int tint, int light,
                               int overlay) {
        float r = (0.05f + 0.1f * gathered * Math.min(1f, g.drop() / 4f)) * Math.min(1f, (g.upperNeg() + g.upperPos()) / 3f + 0.4f);
        float a0 = -g.upperNeg() + 0.15f;
        float a1 = g.upperPos() - 0.15f;
        if (a1 <= a0) return;
        float y1 = -BEAM_HALF + 0.03f;
        float y0 = y1 - 1.6f * r;
        float n0 = side * 0.04f - r;
        float n1 = side * 0.04f + r;
        box(vc, p, g, a0, a1, y0, y1, n0, n1, tint, light, overlay);
    }

    private static void box(VertexConsumer vc, PoseStack.Pose p, ClothGeometry g, float a0, float a1, float y0, float y1,
                            float n0, float n1, int tint, int light, int overlay) {
        float len = a1 - a0;
        // bottom, two long sides, top (hidden by the yard mostly), two ends
        quad(vc, p, g, new float[][] {{a0, y0, n0}, {a1, y0, n0}, {a1, y0, n1}, {a0, y0, n1}}, len, tint, light, overlay);
        quad(vc, p, g, new float[][] {{a0, y1, n0}, {a1, y1, n0}, {a1, y0, n0}, {a0, y0, n0}}, len, tint, light, overlay);
        quad(vc, p, g, new float[][] {{a0, y0, n1}, {a1, y0, n1}, {a1, y1, n1}, {a0, y1, n1}}, len, tint, light, overlay);
        quad(vc, p, g, new float[][] {{a0, y1, n1}, {a1, y1, n1}, {a1, y1, n0}, {a0, y1, n0}}, len, tint, light, overlay);
        quad(vc, p, g, new float[][] {{a0, y0, n0}, {a0, y0, n1}, {a0, y1, n1}, {a0, y1, n0}}, 0.25f, tint, light, overlay);
        quad(vc, p, g, new float[][] {{a1, y0, n1}, {a1, y0, n0}, {a1, y1, n0}, {a1, y1, n1}}, 0.25f, tint, light, overlay);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose p, ClothGeometry g, float[][] c, float uLength, int tint, int light,
                             int overlay) {
        Vector3f a = local(g, c[0][0], c[0][1], c[0][2]);
        Vector3f b = local(g, c[1][0], c[1][1], c[1][2]);
        Vector3f cc = local(g, c[2][0], c[2][1], c[2][2]);
        Vector3f d = local(g, c[3][0], c[3][1], c[3][2]);
        Vector3f n = new Vector3f(b).sub(a).cross(new Vector3f(d).sub(a));
        if (n.lengthSquared() < 1.0e-12f) return;
        n.normalize();
        float u1 = Math.min(1f, Math.max(0.1f, uLength));
        vertex(vc, p, a, 0f, 0f, n, tint, light, overlay);
        vertex(vc, p, b, u1, 0f, n, tint, light, overlay);
        vertex(vc, p, cc, u1, 0.25f, n, tint, light, overlay);
        vertex(vc, p, d, 0f, 0.25f, n, tint, light, overlay);
    }

    /** Cloth coordinates (along the yard, up, across the yard) to block-local coordinates around the head's center. */
    private static Vector3f local(ClothGeometry g, float along, float up, float across) {
        return g.alongX() ? new Vector3f(along, up, across) : new Vector3f(across, up, along);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, Vector3f at, float u, float v, Vector3f n, int tint, int light,
                               int overlay) {
        vc.addVertex(p, at.x, at.y, at.z).setColor((tint >> 16) & 0xFF, (tint >> 8) & 0xFF, tint & 0xFF, 255).setUv(u, v).setOverlay(overlay).setLight(light)
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
        double across = ClothGeometry.STANDOFF + bellyReach() + 0.5;
        double ax = g.alongX() ? along : across;
        double az = g.alongX() ? across : along;
        return new AABB(p.getX() + 0.5 - ax, p.getY() - g.drop(), p.getZ() + 0.5 - az,
                p.getX() + 0.5 + ax, p.getY() + 1.0, p.getZ() + 0.5 + az);
    }

    /** How far the belly can reach off the cloth's rest line [blocks], for the culling boxes of both sail renderers. */
    static double bellyReach() {
        return SailVisualsConfig.ENABLED.get()
                ? Math.max(ClothGeometry.MAX_BELLY, SailShape.maxReach(SailVisualsConfig.MAX_BELLY.get().floatValue(),
                        SailVisualsConfig.FLUTTER_AMPLITUDE.get().floatValue()))
                : ClothGeometry.MAX_BELLY;
    }
}
