package com.richardsenger.piratesnships.ship.decor.flag.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.ship.decor.FlagpoleBlock;
import com.richardsenger.piratesnships.sailing.wind.ClientWind;
import com.richardsenger.piratesnships.ship.decor.flag.FlagBanner;
import com.richardsenger.piratesnships.ship.decor.flag.FlagClothModel;
import com.richardsenger.piratesnships.ship.decor.flag.FlagHoist;
import com.richardsenger.piratesnships.ship.decor.flag.FlagRipple;
import com.richardsenger.piratesnships.ship.decor.flag.FlagVisualsConfig;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleRun;
import com.richardsenger.piratesnships.ship.decor.flag.FlagTint;
import com.richardsenger.piratesnships.ship.decor.flag.FlagWind;
import com.richardsenger.piratesnships.ship.decor.flag.FlagYaw;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleBlockEntity;
import com.richardsenger.piratesnships.ship.sable.ClientShipPoses;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

import java.util.List;

/**
 * Draws a flying flag's cloth (docs/design.md §4.7, FL1) at its exact downwind yaw; the pole stays the block model.
 * The kind and the place on the pole come from the synced {@link FlagpoleBlockEntity#state()} through
 * {@link FlagHoist} (VIS1a: while an action is pending the cloth runs up or down the pole, the flag being hoisted is
 * drawn during a hoist; struck: nothing is drawn; client {@code flag_visuals.hoist_animation} off: what the pole shows,
 * at the head), the geometry from {@link FlagClothModel}, cut into strips that ripple slowly ({@link FlagRipple}, ART3;
 * render only, the swing follows the synced wind's strength), the banner colour of a custom flag from the flag item.
 *
 * <p><b>Banner designs (FLG2).</b> A custom flag's cloth is drawn tinted with the banner's base colour, then each of the
 * banner's pattern layers ({@link FlagBanner#layers}) over the same rippled strips, with the pattern's sprite from
 * vanilla's banner atlas ({@link Sheets#getBannerMaterial}, looked up once per layer per flag and frame) tinted with
 * the layer's dye, in vanilla's banner pattern render type ({@link Sheets#bannerSheet()}, translucent, no depth
 * write), as {@code BannerRenderer} does. {@link FlagBanner} maps the banner's flag region onto the cloth between the
 * heading tape and the frayed fly (sideways by default, client {@code flag_visuals.banner_upright} for upright), the
 * back face mirrored like the base cloth. Each layer floats a hair ({@link #LAYER_LIFT}) off the cloth on each face,
 * a little more per layer, so it never fights the cloth's depth and the translucent sort keeps the layers in order.
 * The weave shows through wherever no pattern covers it.
 *
 * <p><b>Tall poles (VIS1a).</b> The head of a pole ({@link FlagpoleRun}) owns the flag. The cloth is drawn by the
 * block of the pole that holds the cloth's middle ({@link FlagHoist#drawerBelowHead}): the head while it flies, a
 * shaft while a hoist or strike runs it past, so it is lit and culled where it is. The angle smoothing and the ripple
 * phase always belong to the head.
 *
 * <p><b>The angle.</b> On land the server's {@link FlagpoleBlockEntity#yaw()}. On a ship, while the flag follows the
 * wind, the synced world wind bearing ({@link FlagpoleBlockEntity#windBearing()}) turned into the ship's plot frame
 * with the ship's render orientation ({@link ClientShipPoses}) every frame, so the cloth stays downwind while the ship
 * turns between the server's re-checks; this mirrors the sails ({@code sailing/client/YardClothRenderer}), which also
 * combine the synced wind with the client's ship orientation. The drawn yaw approaches the target smoothly and the
 * short way round ({@link FlagYaw#approach}), so wind changes and gusts never snap the cloth.
 *
 * <p>Works the same on ships: Sable renders a sub-level's block entities through the vanilla dispatcher with the
 * ship's pose on the pose stack ({@code sublevel/render/dispatcher/VanillaSubLevelRenderDispatcher#renderBlockEntities},
 * as noted on {@code YardClothRenderer}), so the cloth is built in the pole's local block space.
 */
public class FlagClothRenderer implements BlockEntityRenderer<FlagpoleBlockEntity> {

    /** How far the cloth reaches from the pole's axis (blocks), for the culling box. */
    private static final double REACH = -FlagClothModel.TIP_Z + FlagRipple.MAX_AMPLITUDE + 0.1;

    private static final float H = FlagClothModel.HALF_THICKNESS;
    private static final float BOTTOM = FlagClothModel.BOTTOM;
    private static final float TOP = FlagClothModel.TOP;
    private static final float SWATCH_U0 = FlagClothModel.CLOTH_U1;
    private static final float SWATCH_U1 = FlagClothModel.CLOTH_U1 + FlagClothModel.COLUMN_U;
    private static final float ROW_V = FlagClothModel.ROW_V;

    /** Ripple state, refilled per flag and frame on the render thread (no allocation while drawing). */
    private final float[] x = new float[FlagRipple.STRIPS + 1];
    private final float[] nx = new float[FlagRipple.STRIPS + 1];
    private final float[] nz = new float[FlagRipple.STRIPS + 1];

    /** How far the first pattern layer floats off the cloth face, and how much more each further layer [blocks]. */
    static final float LAYER_LIFT = 0.004f;
    static final float LAYER_STEP = 0.0005f;

    /** The banner's pattern sprites and dye colours, refilled per flag and frame (no allocation while drawing). */
    private final TextureAtlasSprite[] sprites = new TextureAtlasSprite[FlagBanner.MAX_LAYERS];
    private final int[] layerColors = new int[FlagBanner.MAX_LAYERS];

    /** The swing from the wind's strength, sampled once per game tick for all flags. */
    private float amplitude = FlagRipple.MIN_AMPLITUDE;
    private long amplitudeTick = Long.MIN_VALUE;

    public FlagClothRenderer(BlockEntityRendererProvider.Context context) {
    }

    private float amplitude(Level level) {
        long tick = level.getGameTime();
        if (tick != amplitudeTick) {
            amplitudeTick = tick;
            amplitude = FlagRipple.amplitude(ClientWind.hasData() ? ClientWind.sample(tick).strength() : 0.0);
        }
        return amplitude;
    }

    @Override
    public void render(FlagpoleBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !(state.getBlock() instanceof FlagpoleBlock)) return;
        BlockPos pos = be.getBlockPos();
        // VIS1a: the head owns the flag; a shaft draws only while the head's cloth runs past it
        FlagpoleBlockEntity head = be;
        BlockPos headPos = pos;
        if (FlagpoleRun.stacked() && FlagpoleRun.isPole(level, pos.above())) {
            headPos = FlagpoleRun.head(level, pos);
            if (!(level.getBlockEntity(headPos) instanceof FlagpoleBlockEntity h) || h.state().pending().isEmpty()) return;
            head = h;
        }
        double now = level.getGameTime() + (double) partialTick;
        boolean animate = FlagVisualsConfig.HOIST_ANIMATION.get();
        int height = animate && head.state().pending().isPresent() ? FlagpoleRun.height(level, headPos) : 1;
        FlagHoist.Frame frame = FlagHoist.frame(head.state(), now, height, animate);
        FlagKind kind = frame.kind();
        if (kind == FlagKind.NONE) {
            if (head == be) be.shownYaw = Float.NaN; // a raised flag starts at the current angle, not where it was struck
            return;
        }
        if (headPos.getY() - FlagHoist.drawerBelowHead(frame.drop(), height) != pos.getY()) return;
        float target = targetYaw(head, level, partialTick);
        float shown = Double.isNaN(head.shownTime) || Float.isNaN(head.shownYaw) ? target
                : FlagYaw.approach(head.shownYaw, target, now - head.shownTime, FlagYaw.SMOOTHING_TICKS);
        head.shownYaw = shown;
        head.shownTime = now;

        int tint = FlagClothModel.tinted(kind) ? frame.tint() : FlagTint.NONE;
        int r = (tint >> 16) & 0xFF, g = (tint >> 8) & 0xFF, b = tint & 0xFF;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(FlagClothModel.textureFile(kind)));
        pose.pushPose();
        pose.translate(0.5f, (float) (headPos.getY() - frame.drop() - pos.getY()), 0.5f);
        // Yaw 0 points north (-Z); a compass bearing turns clockwise seen from above, i.e. negative about +Y.
        pose.mulPose(Axis.YP.rotationDegrees(-shown));
        PoseStack.Pose p = pose.last();
        FlagRipple.fill(now, FlagRipple.phase(headPos.asLong()), amplitude(level), x, nx, nz);
        drawCloth(vc, p, r, g, b, light, overlay);
        int layers = sampleLayers(kind, frame);
        if (layers > 0) {
            VertexConsumer design = buffers.getBuffer(Sheets.bannerSheet());
            boolean upright = FlagVisualsConfig.BANNER_UPRIGHT.get();
            for (int i = 0; i < layers; i++) {
                int c = layerColors[i];
                drawDesign(design, p, sprites[i], LAYER_LIFT + LAYER_STEP * i, upright,
                        (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, light, overlay);
            }
        }
        pose.popPose();
    }

    /**
     * The cloth of {@link FlagClothModel} (1 px thick, one block high, hoist on the pole's axis, tip 1.5 blocks north)
     * cut into {@link FlagRipple#STRIPS} vertical strips that follow the ripple in {@link #x}: front (+x) and back
     * faces with the texture columns of each strip (the back mirrored like a real flag), the top and bottom edges on
     * the swatch, and the tip column. Normals follow the wave, so the folds catch the light.
     */
    private void drawCloth(VertexConsumer vc, PoseStack.Pose p, int r, int g, int b, int light, int overlay) {
        for (int k = 0; k < FlagRipple.STRIPS; k++) {
            int k1 = k + 1;
            float z0 = FlagRipple.z(k), z1 = FlagRipple.z(k1), u0 = FlagRipple.u(k), u1 = FlagRipple.u(k1);
            float x0 = x[k], x1 = x[k1];
            // front: seen from +X the tip is on the right
            v(vc, p, x0 + H, BOTTOM, z0, u0, 1f, nx[k], 0f, nz[k], r, g, b, light, overlay);
            v(vc, p, x1 + H, BOTTOM, z1, u1, 1f, nx[k1], 0f, nz[k1], r, g, b, light, overlay);
            v(vc, p, x1 + H, TOP, z1, u1, 0f, nx[k1], 0f, nz[k1], r, g, b, light, overlay);
            v(vc, p, x0 + H, TOP, z0, u0, 0f, nx[k], 0f, nz[k], r, g, b, light, overlay);
            // back: the same columns seen from -X
            v(vc, p, x1 - H, BOTTOM, z1, u1, 1f, -nx[k1], 0f, -nz[k1], r, g, b, light, overlay);
            v(vc, p, x0 - H, BOTTOM, z0, u0, 1f, -nx[k], 0f, -nz[k], r, g, b, light, overlay);
            v(vc, p, x0 - H, TOP, z0, u0, 0f, -nx[k], 0f, -nz[k], r, g, b, light, overlay);
            v(vc, p, x1 - H, TOP, z1, u1, 0f, -nx[k1], 0f, -nz[k1], r, g, b, light, overlay);
            // top and bottom edges: the base-colour swatch
            v(vc, p, x0 - H, TOP, z0, SWATCH_U0, 0f, 0f, 1f, 0f, r, g, b, light, overlay);
            v(vc, p, x0 + H, TOP, z0, SWATCH_U1, 0f, 0f, 1f, 0f, r, g, b, light, overlay);
            v(vc, p, x1 + H, TOP, z1, SWATCH_U1, ROW_V, 0f, 1f, 0f, r, g, b, light, overlay);
            v(vc, p, x1 - H, TOP, z1, SWATCH_U0, ROW_V, 0f, 1f, 0f, r, g, b, light, overlay);
            v(vc, p, x1 - H, BOTTOM, z1, SWATCH_U0, 1f - ROW_V, 0f, -1f, 0f, r, g, b, light, overlay);
            v(vc, p, x1 + H, BOTTOM, z1, SWATCH_U1, 1f - ROW_V, 0f, -1f, 0f, r, g, b, light, overlay);
            v(vc, p, x0 + H, BOTTOM, z0, SWATCH_U1, 1f, 0f, -1f, 0f, r, g, b, light, overlay);
            v(vc, p, x0 - H, BOTTOM, z0, SWATCH_U0, 1f, 0f, -1f, 0f, r, g, b, light, overlay);
        }
        // the tip: the last cloth column, facing along the cloth
        int t = FlagRipple.STRIPS;
        float zt = FlagRipple.z(t), xt = x[t], tu1 = FlagClothModel.CLOTH_U1, tu0 = tu1 - FlagClothModel.COLUMN_U;
        float tx = nz[t], tz = -nx[t];
        v(vc, p, xt + H, BOTTOM, zt, tu0, 1f, tx, 0f, tz, r, g, b, light, overlay);
        v(vc, p, xt - H, BOTTOM, zt, tu1, 1f, tx, 0f, tz, r, g, b, light, overlay);
        v(vc, p, xt - H, TOP, zt, tu1, 0f, tx, 0f, tz, r, g, b, light, overlay);
        v(vc, p, xt + H, TOP, zt, tu0, 0f, tx, 0f, tz, r, g, b, light, overlay);
    }

    /**
     * Looks up the sprite and dye colour of every pattern layer of the drawn flag ({@link FlagBanner#layers}) into
     * {@link #sprites} and {@link #layerColors}: once per flag and frame. Returns how many layers to draw.
     */
    private int sampleLayers(FlagKind kind, FlagHoist.Frame frame) {
        List<BannerPatternLayers.Layer> list = FlagBanner.layers(kind, frame.item());
        int n = list.size();
        for (int i = 0; i < n; i++) {
            BannerPatternLayers.Layer layer = list.get(i);
            sprites[i] = Sheets.getBannerMaterial(layer.pattern()).sprite();
            layerColors[i] = layer.color().getTextureDiffuseColor();
        }
        return n;
    }

    /**
     * One pattern layer over the cloth's front and back faces: per ripple strip the part between
     * {@link FlagBanner#stripStart} and {@link FlagBanner#stripEnd}, its corners interpolated on the strip's quad (so it
     * lies on the cloth and ripples with it), lifted by {@code lift} off each face, textured from {@code sprite} by
     * {@link FlagBanner#patternU}/{@link FlagBanner#patternV} at the same cloth places on both faces (the back mirrored).
     */
    private void drawDesign(VertexConsumer vc, PoseStack.Pose p, TextureAtlasSprite sprite, float lift, boolean upright,
                            int r, int g, int b, int light, int overlay) {
        float front = H + lift;
        for (int k = 0; k < FlagRipple.STRIPS; k++) {
            float a0 = FlagBanner.stripStart(k), a1 = FlagBanner.stripEnd(k);
            if (a1 <= a0) continue;
            int k1 = k + 1;
            float w0 = FlagBanner.weight(k, a0), w1 = FlagBanner.weight(k, a1);
            float x0 = lerp(x[k], x[k1], w0), x1 = lerp(x[k], x[k1], w1);
            float nx0 = lerp(nx[k], nx[k1], w0), nx1 = lerp(nx[k], nx[k1], w1);
            float nz0 = lerp(nz[k], nz[k1], w0), nz1 = lerp(nz[k], nz[k1], w1);
            float z0 = FlagClothModel.TIP_Z * a0, z1 = FlagClothModel.TIP_Z * a1;
            float u0b = sprite.getU(FlagBanner.patternU(a0, 0f, upright)), v0b = sprite.getV(FlagBanner.patternV(a0, 0f, upright));
            float u1b = sprite.getU(FlagBanner.patternU(a1, 0f, upright)), v1b = sprite.getV(FlagBanner.patternV(a1, 0f, upright));
            float u0t = sprite.getU(FlagBanner.patternU(a0, 1f, upright)), v0t = sprite.getV(FlagBanner.patternV(a0, 1f, upright));
            float u1t = sprite.getU(FlagBanner.patternU(a1, 1f, upright)), v1t = sprite.getV(FlagBanner.patternV(a1, 1f, upright));
            // front, as the cloth's front face
            v(vc, p, x0 + front, BOTTOM, z0, u0b, v0b, nx0, 0f, nz0, r, g, b, light, overlay);
            v(vc, p, x1 + front, BOTTOM, z1, u1b, v1b, nx1, 0f, nz1, r, g, b, light, overlay);
            v(vc, p, x1 + front, TOP, z1, u1t, v1t, nx1, 0f, nz1, r, g, b, light, overlay);
            v(vc, p, x0 + front, TOP, z0, u0t, v0t, nx0, 0f, nz0, r, g, b, light, overlay);
            // back: the same places, so the same texels, seen from -X
            v(vc, p, x1 - front, BOTTOM, z1, u1b, v1b, -nx1, 0f, -nz1, r, g, b, light, overlay);
            v(vc, p, x0 - front, BOTTOM, z0, u0b, v0b, -nx0, 0f, -nz0, r, g, b, light, overlay);
            v(vc, p, x0 - front, TOP, z0, u0t, v0t, -nx0, 0f, -nz0, r, g, b, light, overlay);
            v(vc, p, x1 - front, TOP, z1, u1t, v1t, -nx1, 0f, -nz1, r, g, b, light, overlay);
        }
    }

    private static float lerp(float a, float b, float w) {
        return a + (b - a) * w;
    }

    private static void v(VertexConsumer vc, PoseStack.Pose p, float px, float py, float pz, float u, float v,
                          float nx, float ny, float nz, int r, int g, int b, int light, int overlay) {
        vc.addVertex(p, px, py, pz).setColor(r, g, b, 255).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz);
    }

    /** The yaw the cloth should point to in its drawing frame (see the class comment). */
    private static float targetYaw(FlagpoleBlockEntity be, Level level, float partialTick) {
        float fallback = be.yaw();
        float wind = be.windBearing();
        if (Float.isNaN(wind)) return fallback;
        Quaterniond q = ClientShipPoses.orientation(level, Vec3.atCenterOf(be.getBlockPos()), partialTick);
        if (q == null) return fallback;
        double[] v = FlagWind.toward(wind);
        return FlagWind.downwindAngle(v[0], v[1], q, Float.isNaN(be.shownYaw) ? fallback : be.shownYaw);
    }

    /**
     * The cloth reaches 1.5 blocks beyond the pole in any direction. NeoForge culls block entity renderers by this box
     * ({@code IBlockEntityRendererExtension#getRenderBoundingBox}, which this method overrides when the NeoForge module
     * compiles the common sources; without it the cloth would vanish whenever the pole block leaves the view). Plain
     * vanilla (and later Fabric) has no such check, so there it is an unused method.
     * <p>VIS1a: while the cloth runs along a tall pole it is drawn by the block holding its middle
     * ({@link FlagHoist#drawerBelowHead}), at most half a block above or below that block, so the box of every block
     * covers half a block more up and down. That is also why this renderer does not set {@code shouldRenderOffScreen}
     * to reach a low cloth from the head: Sable's sub-level path only renders the per-section block entities
     * (docs/sable-notes.md, "Block entity renderers run in client sub-levels").
     */
    public AABB getRenderBoundingBox(FlagpoleBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() + 0.5 - REACH, p.getY() - 0.5, p.getZ() + 0.5 - REACH,
                p.getX() + 0.5 + REACH, p.getY() + 1.5, p.getZ() + 0.5 + REACH);
    }
}
