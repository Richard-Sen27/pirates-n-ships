package com.richardsenger.piratesnships.mob.client;

import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * Model of the M3 mobs: the shared humanoid rig with the mob's texture, plus the procedural telegraph
 * ({@link DuelistArmPose}) on the sword arm after the animations ran ({@code GeoModel#handleAnimations} runs every
 * controller first, then {@link #setCustomAnimations}, so these rotations win over the animated ones). The musket pose
 * is an animation since M6 ({@code musket_aim} on the soldier's arm controller); while aiming, this model only adds
 * the look pitch and the head's turn to both arms, so the musket follows the target up and down like the player's aim
 * pose, and tips the head a little down to the sights. GeckoLib negates x and y of file rotations when it loads them,
 * so file-convention degrees become {@code -deg} here; {@link EntityModelData}'s angles are already in its sense.
 */
public class SeafarerModel<T extends SeafarerMob> extends HumanoidGeoModel<T> {

    /** Head tipped down to the sights while aiming (degrees). */
    private static final float AIM_HEAD_DOWN = 6f;

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
            EntityModelData data = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
            if (data != null) {
                float pitch = data.headPitch() * Mth.DEG_TO_RAD, yaw = data.netHeadYaw() * Mth.DEG_TO_RAD;
                for (GeoBone arm : new GeoBone[]{main, off}) {
                    if (arm == null) continue;
                    arm.setRotX(arm.getRotX() + pitch);
                    arm.setRotY(arm.getRotY() + yaw);
                }
                GeoBone head = getAnimationProcessor().getBone(HEAD);
                if (head != null) head.setRotX(head.getRotX() - AIM_HEAD_DOWN * Mth.DEG_TO_RAD);
            }
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
