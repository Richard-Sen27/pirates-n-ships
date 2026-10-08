package com.richardsenger.piratesnships.combat.melee.client.anim;

import org.jetbrains.annotations.Nullable;

/**
 * Vanilla's crouch, as offsets a sword animation keeps (MEL1, "the sneak case"; pure, no library).
 *
 * <p>PAL replaces every channel an animation keys with the file's value, which is authored on a standing rig. The
 * melee animations key the torso's rotation and position, the head's position and both arms' rotation and position,
 * so a crouching player's upper body popped back to standing height on crouched legs (F9's known issue). The melee
 * layer therefore treats vanilla's crouch as additive on exactly those channels: before the animation it takes the
 * crouch out of the incoming vanilla pose, after the animation it adds it back. Channels the animation does not key
 * come out unchanged; keyed ones become "the animation, crouched".
 *
 * <p>The numbers are {@code HumanoidModel#setupAnim}'s crouch branch (1.21.1) against its standing pose, in PAL's
 * bone units ({@code RenderUtil.copyVanillaPart}: rotations in radians as in {@code ModelPart}, position y = minus
 * the part's y offset from its initial pose, so lowering a part is negative):
 * <ul>
 *   <li>body: {@code xRot = 0.5} (standing 0), {@code y = 3.2} (initial 0)</li>
 *   <li>head: {@code y = 4.2} (initial 0)</li>
 *   <li>arms: {@code xRot += 0.4}, {@code y = 5.2} (initial 2)</li>
 *   <li>legs move too ({@code z = 4}, {@code y = 12.2}), but no melee animation keys them</li>
 * </ul>
 * Vanilla's first-person hand never crouches, so the offsets apply in the third-person model only.
 */
public final class CrouchPose {

    /** Offsets of one bone: pitch in radians and height in pixels (negative = lower). */
    public record Offset(float rotX, float posY) {
    }

    public static final Offset TORSO = new Offset(0.5f, -3.2f);
    public static final Offset HEAD = new Offset(0f, -4.2f);
    public static final Offset ARM = new Offset(0.4f, -3.2f);

    private CrouchPose() {
    }

    /** The crouch offsets of a PAL bone, or {@code null} for bones the melee animations never key (legs, items). */
    public static @Nullable Offset forBone(String bone) {
        return switch (bone) {
            case "torso" -> TORSO;
            case "head" -> HEAD;
            case "right_arm", "left_arm" -> ARM;
            default -> null;
        };
    }

    /** Whether the third-person model shows the crouch offsets on top of a sword animation. */
    public static boolean applies(boolean enabled, boolean crouching, boolean firstPersonPass) {
        return enabled && crouching && !firstPersonPass;
    }
}
