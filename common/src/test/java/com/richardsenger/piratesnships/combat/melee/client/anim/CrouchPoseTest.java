package com.richardsenger.piratesnships.combat.melee.client.anim;

import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MEL1 sneak case: vanilla's crouch stays under a sword animation. The offsets match {@code HumanoidModel}'s crouch
 * branch in PAL's bone units, and the subtract-animate-add scheme the melee layer uses leaves channels the animation
 * does not key untouched while keyed ones end up crouched.
 */
class CrouchPoseTest {

    @Test
    void offsetsAreVanillasCrouchInBoneUnits() {
        // HumanoidModel#setupAnim (1.21.1): body xRot 0.5 / y 3.2, head y 4.2, arms xRot += 0.4 / y 5.2 (initial 2);
        // PAL's bone y is minus the ModelPart's y offset (RenderUtil.copyVanillaPart)
        assertEquals(new CrouchPose.Offset(0.5f, -3.2f), CrouchPose.forBone("torso"));
        assertEquals(new CrouchPose.Offset(0f, -4.2f), CrouchPose.forBone("head"));
        assertEquals(0.4f, CrouchPose.forBone("right_arm").rotX(), 1e-6);
        assertEquals(-(5.2f - 2f), CrouchPose.forBone("right_arm").posY(), 1e-5);
        assertEquals(CrouchPose.forBone("right_arm"), CrouchPose.forBone("left_arm"));
        assertNull(CrouchPose.forBone("right_leg"), "legs stay vanilla: no melee animation keys them");
        assertNull(CrouchPose.forBone("right_item"));
    }

    @Test
    void offsetsCoverExactlyTheBonesTheMeleeAnimationsMove() {
        for (String bone : MeleeFades.FADED_CHANNELS.keySet()) {
            boolean moved = MeleeFades.FADED_CHANNELS.get(bone).contains("position");
            assertEquals(moved, CrouchPose.forBone(bone) != null, bone);
        }
    }

    @Test
    void thirdPersonOnlyAndToggleable() {
        assertTrue(CrouchPose.applies(true, true, false));
        assertFalse(CrouchPose.applies(true, true, true), "vanilla's first-person hand never crouches");
        assertFalse(CrouchPose.applies(true, false, false));
        assertFalse(CrouchPose.applies(false, true, false));
    }

    /** The scheme of {@code PalMeleeAnimations.MeleeLayer.PoseModifier}, with a stand-in for the animation. */
    private static PlayerAnimBone throughLayer(PlayerAnimBone vanilla, boolean keyed, float animRotX, float animPosY) {
        CrouchPose.Offset c = CrouchPose.forBone(vanilla.getName());
        vanilla.rotX -= c.rotX();
        vanilla.positionY -= c.posY();
        if (keyed) {
            vanilla.rotX = animRotX;
            vanilla.positionY = animPosY;
        }
        vanilla.rotX += c.rotX();
        vanilla.positionY += c.posY();
        return vanilla;
    }

    @Test
    void unkeyedChannelsPassThroughAndKeyedOnesCrouch() {
        // the vanilla crouched torso, as PAL copies it from the model
        PlayerAnimBone torso = new PlayerAnimBone("torso");
        torso.rotX = 0.5f;
        torso.positionY = -3.2f;
        PlayerAnimBone untouched = throughLayer(torso, false, 0f, 0f);
        assertEquals(0.5f, untouched.rotX, 1e-6);
        assertEquals(-3.2f, untouched.positionY, 1e-6);

        PlayerAnimBone lunge = new PlayerAnimBone("torso");
        lunge.rotX = 0.5f;
        lunge.positionY = -3.2f;
        // a thrust lean of 0.1 rad with the hip compensation lowering the torso by 0.07 px, authored standing
        PlayerAnimBone crouched = throughLayer(lunge, true, 0.1f, -0.07f);
        assertEquals(0.6f, crouched.rotX, 1e-6, "lean on top of the crouch");
        assertEquals(-3.27f, crouched.positionY, 1e-5, "stays at crouch height instead of popping up to -0.07");
    }

}
