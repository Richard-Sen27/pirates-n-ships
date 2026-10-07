package com.richardsenger.piratesnships.mob.kraken.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.mob.kraken.Kraken;
import com.richardsenger.piratesnships.mob.kraken.KrakenTentacles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model of the kraken (rig contract: {@code tools/gen_kraken.py}). After the animations ran, each tentacle's
 * first segment {@code tentacle_<i>_1} is aimed at its hit box ({@code KrakenPart} i, synced by the server) and
 * stretched along its length so the tentacle's tip reaches it; the animations bend the second and third segments.
 * A cut tentacle shrinks to a stump. GeckoLib flips the file's x axis when baking and the renderer turns the model by
 * {@code 180 - bodyYaw}, so the aim is computed in that frame. A bone pointing up (+y) turned by
 * {@code Rz(c)·Ry(0)·Rx(a)} (GeckoLib's order) points at {@code (−sin c·cos a, cos c·cos a, sin a)}.
 */
public class KrakenModel extends GeoModel<Kraken> {

    public static final ResourceLocation GEO = Constants.id("geo/kraken.geo.json");
    public static final ResourceLocation TEXTURE = Constants.id("textures/entity/kraken.png");
    public static final ResourceLocation ANIMATIONS = Constants.id("animations/kraken.animation.json");

    /** Rest length of a tentacle in px (segments 12 + 12 + 10). */
    public static final float TENTACLE_LENGTH = 34f;
    private static final float MIN_STRETCH = 0.4f;
    private static final float MAX_STRETCH = 8f;
    private static final float STUMP = 0.25f;

    @Override
    public ResourceLocation getModelResource(Kraken animatable) {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(Kraken animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(Kraken animatable) {
        return ANIMATIONS;
    }

    public static String tentacleBone(int i, int segment) {
        return "tentacle_" + i + "_" + segment;
    }

    @Override
    public void setCustomAnimations(Kraken kraken, long instanceId, AnimationState<Kraken> state) {
        float partialTick = state.getPartialTick();
        Vec3 origin = kraken.getPosition(partialTick);
        float bodyYaw = Mth.rotLerp(partialTick, kraken.yBodyRotO, kraken.yBodyRot);
        // world (relative to the entity) -> model render frame: undo the renderer's rotateY(180 - bodyYaw)
        Quaternionf toModel = new Quaternionf().rotationY(-(180f - bodyYaw) * Mth.DEG_TO_RAD);
        for (int i = 0; i < KrakenTentacles.COUNT; i++) {
            GeoBone bone = getAnimationProcessor().getBone(tentacleBone(i, 1));
            if (bone == null) continue;
            if (kraken.clientTentacleCut(i)) {
                bone.setScaleX(STUMP);
                bone.setScaleY(STUMP);
                bone.setScaleZ(STUMP);
                continue;
            }
            Vec3 tip = kraken.clientPartCentre(i, partialTick);
            if (tip == null) continue;
            Vector3f d = toModel.transform(new Vector3f((float) (tip.x - origin.x), (float) (tip.y - origin.y), (float) (tip.z - origin.z)));
            // render frame px; the baked pivot already has x flipped
            d.mul(16f).sub(bone.getPivotX(), bone.getPivotY(), bone.getPivotZ());
            float len = d.length();
            if (len < 1e-3f) continue;
            d.div(len);
            float a = (float) Math.asin(Mth.clamp(d.z, -1f, 1f));
            float c = (float) Mth.atan2(-d.x, d.y);
            bone.setRotX(a);
            bone.setRotY(0f);
            bone.setRotZ(c);
            bone.setScaleY(Mth.clamp(len / TENTACLE_LENGTH, MIN_STRETCH, MAX_STRETCH));
        }
    }
}
