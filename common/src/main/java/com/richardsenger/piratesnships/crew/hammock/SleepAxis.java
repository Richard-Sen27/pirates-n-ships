package com.richardsenger.piratesnships.crew.hammock;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/**
 * Which way a crew member lies in its hammock (HM2, docs/design.md §7.1). Pure: no world access.
 * <p>
 * The hammock's {@code FACING} points from the foot half to the head half ({@link HammockBlock}). The crew rig's
 * {@code sleep} animation (ART1d, {@code art/README.md} "Hammock (ART1d)") lays the body along the model's z axis with
 * the feet towards the model's front (file −z, the way the entity faces: GeckoLib turns the model by
 * {@code 180 − bodyYaw}) and the head towards its back (file +z); the root offset {@code [0, -11, -4.5]} shifts the hips
 * 4.5 px forward so the body is centred on the seam. So the sleeper <b>faces the foot half</b>, the opposite of
 * {@code FACING}, and its head lies over the head half.
 */
public final class SleepAxis {

    private SleepAxis() {
    }

    /**
     * The yaw (Minecraft degrees: 0 = south, 90 = west, wrapped to [-180, 180)) of a sleeper in a hammock facing
     * {@code facing}. {@code shipOrientation} is the body to world rotation of the ship whose plot holds the hammock, or
     * null for a hammock in the world. On a ship the plot direction to the feet is turned into the world and projected
     * onto the horizontal plane, so a heeling or pitching ship changes the yaw only as far as the axis really turns.
     */
    public static float yaw(Direction facing, @Nullable Quaterniondc shipOrientation) {
        Direction toFeet = facing.getOpposite();
        if (shipOrientation == null) {
            return Mth.wrapDegrees(toFeet.toYRot());
        }
        Vector3d d = shipOrientation.transform(new Vector3d(toFeet.getStepX(), 0, toFeet.getStepZ()));
        if (d.x * d.x + d.z * d.z < 1e-8) {
            return Mth.wrapDegrees(toFeet.toYRot()); // the hammock stands on end: no horizontal axis, keep the plot one
        }
        return Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
    }

    /**
     * {@code target} moved by a whole number of turns to lie within 180 degrees of {@code current}: Minecraft
     * interpolates rotations without wrapping, so a yaw crossing ±180 on a turning ship would spin the body once round.
     */
    public static float continuous(float current, float target) {
        return current + Mth.wrapDegrees(target - current);
    }
}
