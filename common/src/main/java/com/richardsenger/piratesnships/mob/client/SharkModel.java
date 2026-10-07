package com.richardsenger.piratesnships.mob.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.mob.entity.Shark;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * GeckoLib model of the shark (rig contract: {@code art/README.md}, "Entities", shark rig). After the animations ran,
 * the {@code root} bone pitches the whole body with the swimming direction (the entity's x rotation) and the
 * {@code head} bone follows the rest of the look direction within ±{@link #HEAD_LIMIT}°, so animations never key the
 * head's or the root's rotation. The pitch maths and its sign live in {@link SharkPose} (GL1: the root used to get
 * Minecraft's pitch unflipped and pitched the body against the swimming direction).
 */
public class SharkModel extends GeoModel<Shark> {

    public static final ResourceLocation GEO = Constants.id("geo/shark.geo.json");
    public static final ResourceLocation TEXTURE = Constants.id("textures/entity/shark.png");
    public static final ResourceLocation ANIMATIONS = Constants.id("animations/shark.animation.json");

    public static final String ROOT = "root";
    public static final String HEAD = "head";
    public static final float HEAD_LIMIT = SharkPose.HEAD_LIMIT;

    @Override
    public ResourceLocation getModelResource(Shark animatable) {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(Shark animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(Shark animatable) {
        return ANIMATIONS;
    }

    @Override
    public void setCustomAnimations(Shark animatable, long instanceId, AnimationState<Shark> animationState) {
        EntityModelData data = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        boolean inWater = animatable.isInWater();
        // Minecraft's pitch (positive = nose down); SharkPose takes it into bone space, once
        float pitch = animatable.getViewXRot(animationState.getPartialTick());
        GeoBone head = getAnimationProcessor().getBone(HEAD);
        if (head != null) {
            head.setRotX(SharkPose.headRotX(pitch, inWater));
            // EntityModelData's yaw is already in bone space (GeoEntityRenderer passes -netHeadYaw)
            if (data != null) head.setRotY(Mth.clamp(data.netHeadYaw(), -HEAD_LIMIT, HEAD_LIMIT) * Mth.DEG_TO_RAD);
        }
        GeoBone root = getAnimationProcessor().getBone(ROOT);
        if (root != null) root.setRotX(SharkPose.rootRotX(pitch, inWater));
    }
}
