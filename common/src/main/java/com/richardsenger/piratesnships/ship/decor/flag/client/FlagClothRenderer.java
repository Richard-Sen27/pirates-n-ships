package com.richardsenger.piratesnships.ship.decor.flag.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.ship.decor.FlagpoleBlock;
import com.richardsenger.piratesnships.ship.decor.flag.FlagClothModel;
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
 * geometry from {@link FlagClothModel}, the banner colour of a custom flag from {@link FlagpoleBlockEntity#clothTint()}.
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
    private static final double REACH = -FlagClothModel.TIP_Z + 0.1;

    public FlagClothRenderer(BlockEntityRendererProvider.Context context) {
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
        for (FlagClothModel.Face f : FlagClothModel.faces()) {
            for (int i = 0; i < 4; i++) {
                float[] c = f.corners()[i];
                vc.addVertex(p, c[0], c[1], c[2]).setColor(r, g, b, 255).setUv(f.u()[i], f.v()[i]).setOverlay(overlay)
                        .setLight(light).setNormal(p, f.nx(), f.ny(), f.nz());
            }
        }
        pose.popPose();
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
