package com.richardsenger.piratesnships.mob.client;

import com.richardsenger.piratesnships.mob.MeleePosed;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * The procedural melee pose on the humanoid rig, shared by the M3 mobs' {@link SeafarerModel} and the crew's model
 * (MEL3): the synced {@link MeleePosed#meleePose()} through {@link DuelistArmPose#of} on the sword arm and the waist,
 * else, with {@code punches}, vanilla's arm swing ({@link DuelistArmPose#punch}). A model calls it from
 * {@code setCustomAnimations}, after GeckoLib ran every controller, so these rotations win over the animated ones (the
 * melee pose wins over the station pose and idle). Angles from {@link DuelistArmPose} are in the animation-file
 * convention; GeckoLib's bone space negates x and y of those (art/README.md, "GeckoLib bone rotations in code"), and a
 * left-handed duelist mirrors y and z.
 */
public final class MeleeTelegraph {

    private MeleeTelegraph() {
    }

    /**
     * Poses the main arm and waist of {@code entity}; returns whether a pose was applied ({@code false} out of a fight
     * and between swings, when the animations play unchanged).
     */
    public static <T extends LivingEntity & GeoAnimatable & MeleePosed> boolean apply(AnimationProcessor<?> bones, T entity,
                                                                                      float partialTick, boolean punches) {
        DuelistArmPose.Angles a = DuelistArmPose.of(entity.meleePose(), entity.meleePoseProgress(partialTick));
        if (a == null && punches) a = DuelistArmPose.punch(entity.getAttackAnim(partialTick));
        if (a == null) return false;
        boolean rightMain = entity.getMainArm() == HumanoidArm.RIGHT;
        pose(bones.getBone(rightMain ? HumanoidGeoModel.RIGHT_ARM : HumanoidGeoModel.LEFT_ARM),
                bones.getBone(rightMain ? HumanoidGeoModel.RIGHT_HAND : HumanoidGeoModel.LEFT_HAND),
                bones.getBone(HumanoidGeoModel.WAIST), a, rightMain);
        return true;
    }

    /**
     * Sets {@code arm} (the main arm), {@code hand} (its hand locator, which carries the held item) and {@code waist} to
     * {@code a}; {@code rightMain} false mirrors y and z.
     */
    public static void pose(@Nullable GeoBone arm, @Nullable GeoBone hand, @Nullable GeoBone waist, DuelistArmPose.Angles a,
                            boolean rightMain) {
        float mirror = rightMain ? 1f : -1f;
        if (arm != null) {
            arm.setRotX(-a.armX() * Mth.DEG_TO_RAD);
            arm.setRotY(-a.armY() * mirror * Mth.DEG_TO_RAD);
            arm.setRotZ(a.armZ() * mirror * Mth.DEG_TO_RAD);
        }
        if (hand != null) hand.setRotX(-a.handX() * Mth.DEG_TO_RAD);
        if (waist != null) waist.setRotX(-a.waistX() * Mth.DEG_TO_RAD);
    }
}
