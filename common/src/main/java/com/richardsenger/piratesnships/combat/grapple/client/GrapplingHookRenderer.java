package com.richardsenger.piratesnships.combat.grapple.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.grapple.GrapplingHookEntity;
import com.richardsenger.piratesnships.combat.grapple.HookPose;
import com.richardsenger.piratesnships.combat.grapple.RopeCurve;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Draws a grappling hook (the hook's item model turned into its {@link HookPose}, GR4: point first in flight, hanging
 * from a rail, flat on a deck, biting into a wall, point down when free) and its rope from the hook's eye to the
 * thrower's hand (docs/design.md §8.3). A
 * latched hook is drawn at its plot position through the ship's render pose ({@link ClientShipPoses}), so it sits on
 * the moving hull without lag; the rope is a thin square strip with the {@code rope.png} texture, like the stays of
 * {@code sailing.client.StayClothRenderer}, straight while taut (also while a player hauls on it, GR5) and sagging otherwise;
 * while its thrower hangs on it (GR7) it is the catenary of its length ({@link ClientRopes#handRopeLength}, {@link RopeCurve}). The near end is the thrower's
 * hand or the mooring ring or cleat it is tied to (GR1, RP1, {@link ClientRopes#fixedNearEnd}); players sliding on it
 * pull it into straight pieces through their hands.
 */
public class GrapplingHookRenderer extends EntityRenderer<GrapplingHookEntity> {

    public static final ResourceLocation ROPE_TEXTURE = Constants.id("textures/block/rope.png");

    private static final float ROPE_HALF = 0.025f;
    private static final int SEGMENTS = 16;
    /** Sag of a slack rope per block of length, and its cap [blocks]. */
    private static final float SAG_PER_BLOCK = 0.06f;
    private static final float MAX_SAG = 1.5f;

    private final ItemRenderer items;

    public GrapplingHookRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
    }

    @Override
    public boolean shouldRender(GrapplingHookEntity hook, Frustum frustum, double camX, double camY, double camZ) {
        Entity owner = hook.getOwner();
        AABB box = hook.getBoundingBox().inflate(0.5);
        Vec3 ring = ClientRopes.fixedNearEnd(hook, 1.0f);
        if (ring != null) {
            box = box.minmax(new AABB(ring, ring).inflate(0.5));
        } else if (owner != null) {
            box = box.minmax(owner.getBoundingBox());
        }
        return frustum.isVisible(box);
    }

    @Override
    public void render(GrapplingHookEntity hook, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 base = new Vec3(Mth.lerp(partialTick, hook.xOld, hook.getX()), Mth.lerp(partialTick, hook.yOld, hook.getY()),
                Mth.lerp(partialTick, hook.zOld, hook.getZ()));
        Vec3 at = ClientRopes.hookPos(hook, partialTick);
        Vec3 off = at.subtract(base);
        // GR4: the model turned into its pose (HookPose), crown at the hook's position; the rope ties on at the eye
        Quaternionf rot = ClientRopes.hookRotation(hook, partialTick);
        Vector3f eye = HookPose.eye(rot);

        pose.pushPose();
        pose.translate(off.x, off.y, off.z);
        pose.mulPose(HookPose.modelMatrix(rot));
        items.renderStatic(hook.getItem(), ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buffers, hook.level(), hook.getId());
        pose.popPose();
        off = off.add(eye.x, eye.y, eye.z);
        at = at.add(eye.x, eye.y, eye.z);

        Vec3 ring = ClientRopes.fixedNearEnd(hook, partialTick);
        Vec3 end = ring != null ? ring : hook.getOwner() instanceof Player owner ? handPos(owner, partialTick) : null;
        if (end != null) {
            Vec3 hand = end.subtract(base);
            Vector3f from = new Vector3f((float) off.x, (float) off.y, (float) off.z);
            Vector3f to = new Vector3f((float) hand.x, (float) hand.y, (float) hand.z);
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(ROPE_TEXTURE));
            List<Vec3> grips = hook.state() == GrapplingHookEntity.State.LATCHED ? ClientRopes.grips(hook, at, end, partialTick) : List.of();
            double hanging = ring == null && grips.isEmpty() ? ClientRopes.handRopeLength(hook, at, end, partialTick) : Double.NaN;
            if (!Double.isNaN(hanging)) {
                // GR7: the rope its thrower hangs on is a catenary of its length: straight at full length, sagging when slack
                curve(vc, pose.last(), base, at, end, hanging, light);
            } else if (grips.isEmpty()) {
                float len = from.distance(to);
                float sag = hook.taut() ? 0f : Math.min(MAX_SAG, SAG_PER_BLOCK * len);
                rope(vc, pose.last(), from, to, sag, light);
            } else {
                // riders pull the rope taut: straight pieces from the hook through every grip to the near end (GR2)
                Vector3f prev = from;
                float v = 0f;
                for (Vec3 g : grips) {
                    Vec3 rel = g.subtract(base);
                    Vector3f next = new Vector3f((float) rel.x, (float) rel.y, (float) rel.z);
                    float len = prev.distance(next);
                    beam(vc, pose.last(), prev, next, v, v + len, light);
                    v += len;
                    prev = next;
                }
                beam(vc, pose.last(), prev, to, v, v + prev.distance(to), light);
            }
        }
        super.render(hook, yaw, partialTick, pose, buffers, light);
    }

    /** The rope as {@link #SEGMENTS} straight pieces along a parabola that hangs {@code sag} blocks at its middle. */
    private static void rope(VertexConsumer vc, PoseStack.Pose p, Vector3f from, Vector3f to, float sag, int light) {
        Vector3f prev = new Vector3f(from);
        float v = 0f;
        for (int i = 1; i <= SEGMENTS; i++) {
            float t = (float) i / SEGMENTS;
            Vector3f next = new Vector3f(from).lerp(to, t).sub(0f, 4f * sag * t * (1f - t), 0f);
            float len = prev.distance(next);
            beam(vc, p, prev, next, v, v + len, light);
            v += len;
            prev = next;
        }
    }

    /**
     * GR7: the rope from {@code a} to {@code b} (world) as {@link #SEGMENTS} straight pieces along the catenary of
     * {@code length} ({@link RopeCurve}), drawn relative to {@code base}.
     */
    private static void curve(VertexConsumer vc, PoseStack.Pose p, Vec3 base, Vec3 a, Vec3 b, double length, int light) {
        Vector3f prev = null;
        float v = 0f;
        for (Vec3 point : RopeCurve.sample(a, b, length, SEGMENTS)) {
            Vec3 rel = point.subtract(base);
            Vector3f next = new Vector3f((float) rel.x, (float) rel.y, (float) rel.z);
            if (prev != null) {
                float len = prev.distance(next);
                beam(vc, p, prev, next, v, v + len, light);
                v += len;
            }
            prev = next;
        }
    }

    /** A square beam from {@code a} to {@code b} (four sides, open ends), texture v running along it. */
    private static void beam(VertexConsumer vc, PoseStack.Pose p, Vector3f a, Vector3f b, float v0, float v1, int light) {
        Vector3f d = new Vector3f(b).sub(a);
        if (d.lengthSquared() < 1.0e-8f) return;
        d.normalize();
        Vector3f s = Math.abs(d.y) > 0.99f ? new Vector3f(1f, 0f, 0f) : new Vector3f(d).cross(0f, 1f, 0f).normalize();
        Vector3f t = new Vector3f(d).cross(s).normalize();
        Vector3f[] c = {
                new Vector3f(s).add(t).mul(ROPE_HALF), new Vector3f(s).sub(t).mul(ROPE_HALF),
                new Vector3f(s).negate().sub(t).mul(ROPE_HALF), new Vector3f(t).sub(s).mul(ROPE_HALF)};
        for (int k = 0; k < 4; k++) {
            Vector3f c0 = c[k], c1 = c[(k + 1) % 4];
            Vector3f n = new Vector3f(c0).add(c1).normalize();
            float u0 = k * 0.25f, u1 = u0 + 0.25f;
            vertex(vc, p, new Vector3f(a).add(c0), u0, v0, n, light);
            vertex(vc, p, new Vector3f(a).add(c1), u1, v0, n, light);
            vertex(vc, p, new Vector3f(b).add(c1), u1, v1, n, light);
            vertex(vc, p, new Vector3f(b).add(c0), u0, v1, n, light);
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, Vector3f at, float u, float v, Vector3f n, int light) {
        vc.addVertex(p, at.x, at.y, at.z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(p, n.x, n.y, n.z);
    }

    /** Where the thrower holds the rope: the main hand, seen from the camera in first person, else from the body. */
    private Vec3 handPos(Player player, float partialTick) {
        int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        Minecraft mc = Minecraft.getInstance();
        if (entityRenderDispatcher.options.getCameraType().isFirstPerson() && player == mc.player) {
            double fovScale = 960.0 / entityRenderDispatcher.options.fov().get().intValue();
            Vec3 v = entityRenderDispatcher.camera.getNearPlane().getPointOnPlane(side * 0.525f, -0.1f).scale(fovScale);
            return player.getEyePosition(partialTick).add(v);
        }
        float body = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        double sin = Mth.sin(body);
        double cos = Mth.cos(body);
        double scale = player.getScale();
        double sideways = side * 0.35 * scale;
        double forward = 0.8 * scale;
        double crouch = player.isCrouching() ? -0.1875 : 0.0;
        return player.getEyePosition(partialTick).add(-cos * sideways - sin * forward, crouch - 0.45 * scale, -sin * sideways + cos * forward);
    }

    @Override
    public ResourceLocation getTextureLocation(GrapplingHookEntity hook) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
