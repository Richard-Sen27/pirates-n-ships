package com.richardsenger.piratesnships.mob.client;

import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Model of the M3 mobs: the shared humanoid rig with the mob's texture, plus the procedural telegraph
 * ({@link DuelistArmPose}) on the sword arm and the musket aim (both arms forward) after the body animations ran.
 * GeckoLib negates x and y of file rotations when it loads them, so file-convention degrees become {@code -deg} here.
 */
public class SeafarerModel<T extends SeafarerMob> extends HumanoidGeoModel<T> {

    /** Musket held at the shoulder: both arms forward, in file-convention degrees. */
    private static final float AIM_RIGHT_X = -88f, AIM_RIGHT_Y = -10f, AIM_LEFT_X = -85f, AIM_LEFT_Y = 35f;

    public SeafarerModel(String textureName) {
        super(entityTexture(textureName));
    }

    @Override
    public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        boolean rightMain = animatable.getMainArm() == HumanoidArm.RIGHT;
        GeoBone main = getAnimationProcessor().getBone(rightMain ? RIGHT_ARM : LEFT_ARM);
        GeoBone off = getAnimationProcessor().getBone(rightMain ? LEFT_ARM : RIGHT_ARM);
        float mirror = rightMain ? 1f : -1f;
        if (animatable.isAiming()) {
            set(main, AIM_RIGHT_X, AIM_RIGHT_Y * mirror, 0);
            set(off, AIM_LEFT_X, AIM_LEFT_Y * mirror, 0);
            return;
        }
        DuelistArmPose.Angles a = DuelistArmPose.of(animatable.meleePose(), animatable.meleePoseProgress(animationState.getPartialTick()));
        if (a == null) return;
        set(main, a.armX(), a.armY() * mirror, a.armZ() * mirror);
        GeoBone waist = getAnimationProcessor().getBone(WAIST);
        if (waist != null) waist.setRotX(-a.waistX() * Mth.DEG_TO_RAD);
    }

    private static void set(GeoBone bone, float x, float y, float z) {
        if (bone == null) return;
        bone.setRotX(-x * Mth.DEG_TO_RAD);
        bone.setRotY(-y * Mth.DEG_TO_RAD);
        bone.setRotZ(z * Mth.DEG_TO_RAD);
    }
}
