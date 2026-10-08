package com.richardsenger.piratesnships.sailing.anchor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.anchor.AnchorEntity;
import com.richardsenger.piratesnships.sailing.anchor.AnchorTravel;
import com.richardsenger.piratesnships.sailing.anchor.ChainCurve;
import com.richardsenger.piratesnships.ship.sable.ClientShipPoses;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Renders the visible anchor (docs/design.md §5.3): a code-built anchor model about two blocks tall (ring, stock,
 * shank, crown, two arms with flukes) and, while the anchor is out, its chain from the hawse to the ring (AN2b: a
 * sagging catenary of the paid-out length while the anchor rests, {@link ChainCurve}). The hawse is computed from the
 * ship's render pose every frame, so the chain and its curve follow the moving, swinging hull without lag.
 *
 * <p>A stowed anchor lives in the ship's plot, so Sable already moves and rotates it with the ship
 * ({@code LevelRendererMixin}); an anchor that is out lives in world space and hangs straight down, turned with the
 * ship's heading.
 */
public class AnchorRenderer extends EntityRenderer<AnchorEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Constants.id("anchor"), "main");
    static final ResourceLocation TEXTURE = Constants.id("textures/entity/anchor.png");
    /** Vanilla's chain block texture: two 3-pixel strips (u 0..3 and 3..6), as in vanilla's chain model. */
    static final ResourceLocation CHAIN = ResourceLocation.withDefaultNamespace("textures/block/chain.png");
    private static final float CHAIN_WIDTH = 3f / 16f;
    /** Height above the anchor's feet (the seabed) at which the slack chain lies [blocks]. */
    private static final double FLOOR_LIFT = 0.1;

    private final ModelPart model;
    /** Reused every frame (render thread only), so drawing the chain allocates nothing. */
    private final ChainCurve curve = new ChainCurve();
    private final BlockPos.MutableBlockPos lightPos = new BlockPos.MutableBlockPos();

    public AnchorRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = ctx.bakeLayer(LAYER);
        this.shadowRadius = 0f;
    }

    /**
     * Admiralty-pattern anchor, exported from Blockbench ({@code art/models/anchor.bbmodel}, Modded Entity format,
     * Mojang mappings 1.17+); edit it there and paste the export's body here. Model in pixels, origin at the top of
     * the ring, y pointing down; arms along x, stock along z. Ring, eye, shank with a stock and ball ends across it,
     * crown, and two curved arms (two segments each) ending in spade flukes with bills. About two blocks tall (ring
     * top to crown tip 31 px), the flukes reach 13 px to each side. Texture {@code textures/entity/anchor.png},
     * 64 x 64 (see {@code tools/gen_anchor_texture.py}).
     */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(36, 36).addBox(-1.25F, 0.0F, -0.5F, 2.5F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(44, 36).addBox(-1.25F, 6.0F, -0.5F, 2.5F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(20, 27).addBox(2.0F, 2.25F, -0.5F, 1.0F, 2.5F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(24, 27).addBox(-3.0F, 2.25F, -0.5F, 1.0F, 2.5F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 21).addBox(-1.5F, 6.5F, -1.5F, 3.0F, 2.5F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0).addBox(-1.0F, 9.0F, -1.0F, 2.0F, 19.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(8, 0).addBox(-1.0F, 9.0F, -7.0F, 2.0F, 2.0F, 14.0F, new CubeDeformation(0.0F))
                .texOffs(12, 21).addBox(-1.5F, 8.5F, -1.5F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(0, 27).addBox(-1.25F, 8.75F, -8.5F, 2.5F, 2.5F, 1.5F, new CubeDeformation(0.0F))
                .texOffs(10, 27).addBox(-1.25F, 8.75F, 7.0F, 2.5F, 2.5F, 1.5F, new CubeDeformation(0.0F))
                .texOffs(24, 21).addBox(-2.5F, 27.0F, -1.5F, 5.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(28, 36).addBox(-1.0F, 30.0F, -1.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition ring_ul = body.addOrReplaceChild("ring_ul", CubeListBuilder.create().texOffs(52, 36).addBox(-1.1F, -0.5F, -0.5F, 2.2F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.85F, 1.65F, 0.0F, 0.0F, 0.0F, 0.7854F));
        PartDefinition ring_ur = body.addOrReplaceChild("ring_ur", CubeListBuilder.create().texOffs(0, 40).addBox(-1.1F, -0.5F, -0.5F, 2.2F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.85F, 1.65F, 0.0F, 0.0F, 0.0F, -0.7854F));
        PartDefinition ring_ll = body.addOrReplaceChild("ring_ll", CubeListBuilder.create().texOffs(8, 40).addBox(-1.1F, -0.5F, -0.5F, 2.2F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.85F, 5.35F, 0.0F, 0.0F, 0.0F, -0.7854F));
        PartDefinition ring_lr = body.addOrReplaceChild("ring_lr", CubeListBuilder.create().texOffs(16, 40).addBox(-1.1F, -0.5F, -0.5F, 2.2F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.85F, 5.35F, 0.0F, 0.0F, 0.0F, 0.7854F));
        PartDefinition right_arm = body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(28, 27).addBox(-6.8F, -1.0F, -1.0F, 7.3F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.0F, 28.5F, 0.0F, 0.0F, 0.0F, 0.4363F));
        PartDefinition right_arm_upper = body.addOrReplaceChild("right_arm_upper", CubeListBuilder.create().texOffs(0, 32).addBox(-6.5F, -0.9F, -0.9F, 7.1F, 1.8F, 1.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.891F, 25.753F, 0.0F, 0.0F, 0.0F, 0.9599F));
        PartDefinition right_fluke = body.addOrReplaceChild("right_fluke", CubeListBuilder.create().texOffs(40, 0).addBox(-1.25F, -2.6F, -0.6F, 3.0F, 5.2F, 1.2F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-9.9559F, 22.804F, 0.0F, 0.0F, 0.0F, 0.9599F));
        PartDefinition right_fluke_point = body.addOrReplaceChild("right_fluke_point", CubeListBuilder.create().texOffs(40, 21).addBox(-1.85F, -1.85F, -0.6F, 3.7F, 3.7F, 1.2F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-10.7589F, 21.6572F, 0.0F, 0.0F, 0.0F, 1.7453F));
        PartDefinition right_bill = body.addOrReplaceChild("right_bill", CubeListBuilder.create().texOffs(20, 32).addBox(-0.6F, -0.6F, -0.6F, 1.2F, 1.2F, 1.2F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-11.8487F, 20.1008F, 0.0F, 0.0F, 0.0F, 1.7453F));
        PartDefinition left_arm = body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(28, 32).addBox(-0.5F, -1.0F, -1.0F, 7.3F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, 28.5F, 0.0F, 0.0F, 0.0F, -0.4363F));
        PartDefinition left_arm_upper = body.addOrReplaceChild("left_arm_upper", CubeListBuilder.create().texOffs(0, 36).addBox(-0.6F, -0.9F, -0.9F, 7.1F, 1.8F, 1.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.891F, 25.753F, 0.0F, 0.0F, 0.0F, -0.9599F));
        PartDefinition left_fluke = body.addOrReplaceChild("left_fluke", CubeListBuilder.create().texOffs(50, 0).addBox(-1.25F, -2.6F, -0.6F, 3.0F, 5.2F, 1.2F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(9.9559F, 22.804F, 0.0F, 0.0F, 0.0F, -0.9599F));
        PartDefinition left_fluke_point = body.addOrReplaceChild("left_fluke_point", CubeListBuilder.create().texOffs(52, 21).addBox(-1.85F, -1.85F, -0.6F, 3.7F, 3.7F, 1.2F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(10.7589F, 21.6572F, 0.0F, 0.0F, 0.0F, -1.7453F));
        PartDefinition left_bill = body.addOrReplaceChild("left_bill", CubeListBuilder.create().texOffs(20, 36).addBox(-0.6F, -0.6F, -0.6F, 1.2F, 1.2F, 1.2F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(11.8487F, 20.1008F, 0.0F, 0.0F, 0.0F, -1.7453F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public boolean shouldRender(AnchorEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        // stowed, the entity's own box is in plot space (millions of blocks away); out, the chain reaches far
        return true;
    }

    @Override
    public void render(AnchorEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        boolean out = e.isOut();
        Vec3 hawsePlot = e.hawse();
        double armsYaw = e.armsAlongX() ? 0.0 : Math.PI / 2;
        if (out) {
            // hanging free: turn with the ship's heading (the plot's arm axis seen in world space)
            Quaterniond rot = ClientShipPoses.orientation(e.level(), hawsePlot, pt);
            if (rot != null) {
                Vector3d axis = rot.transform(e.armsAlongX() ? new Vector3d(1, 0, 0) : new Vector3d(0, 0, 1));
                armsYaw = Math.atan2(-axis.z, axis.x);
            }
        }
        pose.pushPose();
        pose.translate(0.0, AnchorTravel.HEIGHT, 0.0);
        pose.mulPose(Axis.YP.rotation((float) armsYaw));
        pose.scale(-1f, -1f, 1f);
        model.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        if (out) {
            Vec3 hawse = ClientShipPoses.toWorld(e.level(), hawsePlot, pt);
            if (hawse != null) {
                double x = Mth.lerp(pt, e.xo, e.getX()), y = Mth.lerp(pt, e.yo, e.getY()), z = Mth.lerp(pt, e.zo, e.getZ());
                chain(e, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(CHAIN)), x, y, z,
                        hawse.x - x, hawse.y - y, hawse.z - z);
            }
        }
        super.render(e, yaw, pt, pose, buffers, light);
    }

    /**
     * The chain from the ring to the hawse (AN2b), relative to the anchor's render position {@code (x, y, z)}: a
     * {@link ChainCurve} of the paid-out length, slack only while the anchor rests (then its lowest part lies on the
     * seabed); falling, hanging or heaved up, the chain runs straight from the hawse, since it pays out or is wound in
     * as the anchor moves. Lit per point from the world, since it can reach from the seabed to the deck.
     */
    private void chain(AnchorEntity e, PoseStack pose, VertexConsumer vc, double x, double y, double z, double hx, double hy, double hz) {
        double ry = AnchorTravel.HEIGHT;
        double distance = Math.sqrt(hx * hx + (hy - ry) * (hy - ry) + hz * hz);
        double length = Math.max(e.paidOut(), distance);
        boolean resting = e.isResting();
        double floor = resting ? FLOOR_LIFT : Double.NEGATIVE_INFINITY;
        int n = curve.compute(0.0, ry, 0.0, hx, hy, hz, length, e.isTaut() || !resting, floor, ChainCurve.segmentsFor(length));
        PoseStack.Pose p = pose.last();
        int lightFrom = light(e, x, y, z, 0);
        for (int i = 0; i < n; i++) {
            int lightTo = light(e, x, y, z, i + 1);
            segment(p, vc, curve.x(i), curve.y(i), curve.z(i), curve.x(i + 1), curve.y(i + 1), curve.z(i + 1), lightFrom, lightTo);
            lightFrom = lightTo;
        }
    }

    private int light(AnchorEntity e, double x, double y, double z, int i) {
        lightPos.set(Mth.floor(x + curve.x(i)), Mth.floor(y + curve.y(i)), Mth.floor(z + curve.z(i)));
        return LevelRenderer.getLightColor(e.level(), lightPos);
    }

    /**
     * One curve segment as two crossed quads (vanilla chain strips), split into pieces of at most a block so the strip
     * texture is never stretched beyond its 16 pixels.
     */
    private static void segment(PoseStack.Pose p, VertexConsumer vc, double x0, double y0, double z0, double x1, double y1, double z1,
                                int light0, int light1) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-4) {
            return;
        }
        dx /= len;
        dy /= len;
        dz /= len;
        // a: horizontal, across the chain (dir × up); b = dir × a
        double ax, ay, az;
        if (Math.abs(dy) > 0.99) {
            ax = 1.0;
            ay = 0.0;
            az = 0.0;
        } else {
            double h = Math.sqrt(dx * dx + dz * dz);
            ax = -dz / h;
            ay = 0.0;
            az = dx / h;
        }
        double bx = dy * az - dz * ay, by = dz * ax - dx * az, bz = dx * ay - dy * ax;
        int pieces = Math.max(1, (int) Math.ceil(len - 1.0e-6));
        float v = (float) (len / pieces);
        for (int k = 0; k < pieces; k++) {
            double t0 = (double) k / pieces, t1 = (double) (k + 1) / pieces;
            double sx = x0 + dx * len * t0, sy = y0 + dy * len * t0, sz = z0 + dz * len * t0;
            double ex = x0 + dx * len * t1, ey = y0 + dy * len * t1, ez = z0 + dz * len * t1;
            int l0 = pieces == 1 ? light0 : (k == 0 ? light0 : light1);
            strip(p, vc, sx, sy, sz, ex, ey, ez, ax, ay, az, 0f, bx, by, bz, l0, light1, v);
            strip(p, vc, sx, sy, sz, ex, ey, ez, bx, by, bz, 3f / 16f, ax, ay, az, l0, light1, v);
        }
    }

    private static void strip(PoseStack.Pose p, VertexConsumer vc, double sx, double sy, double sz, double ex, double ey, double ez,
                              double cx, double cy, double cz, float u0, double nx, double ny, double nz, int light0, int light1, float v) {
        float hx = (float) (cx * CHAIN_WIDTH / 2), hy = (float) (cy * CHAIN_WIDTH / 2), hz = (float) (cz * CHAIN_WIDTH / 2);
        float u1 = u0 + 3f / 16f;
        float fx = (float) nx, fy = (float) ny, fz = (float) nz;
        vertex(p, vc, (float) sx - hx, (float) sy - hy, (float) sz - hz, u0, 0f, fx, fy, fz, light0);
        vertex(p, vc, (float) sx + hx, (float) sy + hy, (float) sz + hz, u1, 0f, fx, fy, fz, light0);
        vertex(p, vc, (float) ex + hx, (float) ey + hy, (float) ez + hz, u1, v, fx, fy, fz, light1);
        vertex(p, vc, (float) ex - hx, (float) ey - hy, (float) ez - hz, u0, v, fx, fy, fz, light1);
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, int light) {
        vc.addVertex(p, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(p, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(AnchorEntity entity) {
        return TEXTURE;
    }
}
