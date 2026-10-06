package com.richardsenger.piratesnships.sailing.anchor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.anchor.AnchorEntity;
import com.richardsenger.piratesnships.sailing.anchor.AnchorTravel;
import com.richardsenger.piratesnships.ship.sable.ClientShipPoses;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Renders the visible anchor (docs/design.md §5.3): a code-built anchor model about two blocks tall (ring, stock,
 * shank, crown, two arms with flukes) and, while the anchor is out, its chain from the hawse to the ring. The hawse
 * is computed from the ship's render pose every frame, so the chain stays attached to the moving hull.
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

    private final ModelPart model;

    public AnchorRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = ctx.bakeLayer(LAYER);
        this.shadowRadius = 0f;
    }

    /** Model in pixels, origin at the top of the ring, y pointing down; arms along x, stock along z. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("ring", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3f, 0f, -0.5f, 6, 1, 1).addBox(-3f, 1f, -0.5f, 1, 4, 1).addBox(2f, 1f, -0.5f, 1, 4, 1), PartPose.ZERO);
        root.addOrReplaceChild("stock", CubeListBuilder.create().texOffs(0, 8).addBox(-1f, 5f, -6f, 2, 2, 12), PartPose.ZERO);
        root.addOrReplaceChild("shank", CubeListBuilder.create().texOffs(40, 0).addBox(-1f, 5f, -1f, 2, 24, 2), PartPose.ZERO);
        root.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(0, 24).addBox(-2.5f, 28f, -1.5f, 5, 3, 3), PartPose.ZERO);
        PartDefinition left = root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(0, 32).addBox(-10f, -1f, -1f, 10, 2, 2),
                PartPose.offsetAndRotation(-1.5f, 29.5f, 0f, 0f, 0f, 0.7f));
        left.addOrReplaceChild("left_fluke", CubeListBuilder.create().texOffs(0, 40).addBox(-4f, -3f, -1.5f, 4, 4, 3),
                PartPose.offsetAndRotation(-9f, 0f, 0f, 0f, 0f, 0.35f));
        PartDefinition right = root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 48).addBox(0f, -1f, -1f, 10, 2, 2),
                PartPose.offsetAndRotation(1.5f, 29.5f, 0f, 0f, 0f, -0.7f));
        right.addOrReplaceChild("right_fluke", CubeListBuilder.create().texOffs(16, 40).addBox(0f, -3f, -1.5f, 4, 4, 3),
                PartPose.offsetAndRotation(9f, 0f, 0f, 0f, 0f, -0.35f));
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
                Vec3 ring = new Vec3(0.0, AnchorTravel.HEIGHT, 0.0);
                chain(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(CHAIN)), ring,
                        new Vec3(hawse.x - x, hawse.y - y, hawse.z - z), light);
            }
        }
        super.render(e, yaw, pt, pose, buffers, light);
    }

    /** The chain as one-block segments of two crossed quads (vanilla chain strips) from {@code from} to {@code to}. */
    static void chain(PoseStack pose, VertexConsumer vc, Vec3 from, Vec3 to, int light) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.05) {
            return;
        }
        Vec3 dir = d.scale(1.0 / len);
        Vec3 a = Math.abs(dir.y) > 0.99 ? new Vec3(1, 0, 0) : dir.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 b = dir.cross(a).normalize();
        PoseStack.Pose p = pose.last();
        for (double s = 0.0; s < len; s += 1.0) {
            double segment = Math.min(1.0, len - s);
            Vec3 p0 = from.add(dir.scale(s));
            Vec3 p1 = from.add(dir.scale(s + segment));
            strip(p, vc, p0, p1, a, 0f, b, light, (float) segment);
            strip(p, vc, p0, p1, b, 3f / 16f, a, light, (float) segment);
        }
    }

    private static void strip(PoseStack.Pose p, VertexConsumer vc, Vec3 p0, Vec3 p1, Vec3 side, float u0, Vec3 normal, int light, float v) {
        Vec3 h = side.scale(CHAIN_WIDTH / 2);
        float u1 = u0 + 3f / 16f;
        vertex(p, vc, p0.subtract(h), u0, 0f, normal, light);
        vertex(p, vc, p0.add(h), u1, 0f, normal, light);
        vertex(p, vc, p1.add(h), u1, v, normal, light);
        vertex(p, vc, p1.subtract(h), u0, v, normal, light);
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer vc, Vec3 at, float u, float v, Vec3 n, int light) {
        vc.addVertex(p, (float) at.x, (float) at.y, (float) at.z).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, (float) n.x, (float) n.y, (float) n.z);
    }

    @Override
    public ResourceLocation getTextureLocation(AnchorEntity entity) {
        return TEXTURE;
    }
}
