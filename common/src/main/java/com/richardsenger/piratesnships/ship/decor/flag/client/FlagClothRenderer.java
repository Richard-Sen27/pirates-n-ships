package com.richardsenger.piratesnships.ship.decor.flag.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.ship.decor.FlagpoleBlock;
import com.richardsenger.piratesnships.sailing.wind.ClientWind;
import com.richardsenger.piratesnships.ship.decor.flag.FlagClothModel;
import com.richardsenger.piratesnships.ship.decor.flag.FlagRipple;
import com.richardsenger.piratesnships.ship.decor.flag.FlagTint;
import com.richardsenger.piratesnships.ship.decor.flag.FlagWind;
import com.richardsenger.piratesnships.ship.decor.flag.FlagYaw;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleBlockEntity;
import com.richardsenger.piratesnships.ship.sable.ClientShipPoses;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

/**
 * Draws a flying flag's cloth (docs/design.md §4.7, FL1) at its exact downwind yaw; the pole stays the block model.
 * The kind comes from the block state's {@link FlagpoleBlock#FLAG} (none while struck: nothing is drawn), the
 * geometry from {@link FlagClothModel}, cut into strips that ripple slowly ({@link FlagRipple}, ART3; render only, the
 * swing follows the synced wind's strength), the banner colour of a custom flag from {@link FlagpoleBlockEntity#clothTint()}.
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
        FlagKind kind = state.getValue(FlagpoleBlock.FLAG);
        if (kind == FlagKind.NONE) {
            be.shownYaw = Float.NaN; // a raised flag starts at the current angle, not where it was struck
            return;
        }
        double now = level.getGameTime() + (double) partialTick;
        float target = targetYaw(be, level, partialTick);
        float shown = Double.isNaN(be.shownTime) ? target : FlagYaw.approach(be.shownYaw, target, now - be.shownTime, FlagYaw.SMOOTHING_TICKS);
        be.shownYaw = shown;
        be.shownTime = now;

        int tint = FlagClothModel.tinted(kind) ? be.clothTint() : FlagTint.NONE;
        int r = (tint >> 16) & 0xFF, g = (tint >> 8) & 0xFF, b = tint & 0xFF;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(FlagClothModel.textureFile(kind)));
        pose.pushPose();
        pose.translate(0.5f, 0f, 0.5f);
        // Yaw 0 points north (-Z); a compass bearing turns clockwise seen from above, i.e. negative about +Y.
        pose.mulPose(Axis.YP.rotationDegrees(-shown));
        PoseStack.Pose p = pose.last();
        FlagRipple.fill(now, FlagRipple.phase(be.getBlockPos().asLong()), amplitude(level), x, nx, nz);
        drawCloth(vc, p, r, g, b, light, overlay);
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
     */
    public AABB getRenderBoundingBox(FlagpoleBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() + 0.5 - REACH, p.getY(), p.getZ() + 0.5 - REACH,
                p.getX() + 0.5 + REACH, p.getY() + 1.0, p.getZ() + 0.5 + REACH);
    }
}
