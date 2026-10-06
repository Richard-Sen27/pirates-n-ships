package com.richardsenger.piratesnships.ship.assembly;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;

/**
 * Pure math for putting a ship back on the grid. No Sable, no world access.
 *
 * <p>Convention (matches Sable's {@code AssemblyTransform}): a rotation by {@code k} quarter turns is
 * {@code Vec3.yRot(k·π/2)}, which equals JOML's {@code rotationY(k·π/2)}: counter-clockwise seen from above, east
 * turns to north.
 */
public final class DisassemblyMath {

    private DisassemblyMath() {
    }

    /** The yaw of a body-to-world rotation, in radians, in JOML's {@code rotationY} sense, range (−π, π]. */
    public static double yaw(Quaterniondc orientation) {
        // The body X axis in world space is (1 − 2(y² + z²), …, 2(xz − wy)); yaw = atan2(−z', x').
        double x = orientation.x(), y = orientation.y(), z = orientation.z(), w = orientation.w();
        return Math.atan2(2 * (w * y - x * z), 1 - 2 * (y * y + z * z));
    }

    /** The nearest 90° step of the yaw, as counter-clockwise quarter turns in 0..3. */
    public static int quarterTurns(Quaterniondc orientation) {
        return Math.floorMod((int) Math.round(yaw(orientation) / (Math.PI / 2)), 4);
    }

    /** Angle between the ship's up axis and world up, in degrees (0 = perfectly level). */
    public static double tiltDegrees(Quaterniondc orientation) {
        // World Y of the body up axis: 1 − 2(x² + z²), for a unit quaternion.
        double x = orientation.x(), z = orientation.z();
        double n = orientation.lengthSquared();
        double upY = 1 - 2 * (x * x + z * z) / n;
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, upY))));
    }

    /** Rotates an integer horizontal offset by {@code turns} counter-clockwise quarter turns. */
    static int[] rotate(int dx, int dz, int turns) {
        return switch (Math.floorMod(turns, 4)) {
            case 1 -> new int[] {dz, -dx};
            case 2 -> new int[] {-dx, -dz};
            case 3 -> new int[] {-dz, dx};
            default -> new int[] {dx, dz};
        };
    }

    /** Where the plot block {@code plotPos} lands when {@code plotAnchor} lands on {@code worldGoal}, rotated by {@code turns}. */
    public static BlockPos target(BlockPos plotPos, BlockPos plotAnchor, BlockPos worldGoal, int turns) {
        int[] r = rotate(plotPos.getX() - plotAnchor.getX(), plotPos.getZ() - plotAnchor.getZ(), turns);
        return new BlockPos(worldGoal.getX() + r[0], worldGoal.getY() + plotPos.getY() - plotAnchor.getY(), worldGoal.getZ() + r[1]);
    }

    /** The same mapping for a continuous position (entities): rotation about the anchor block's center. */
    public static Vec3 target(Vec3 plotPos, BlockPos plotAnchor, BlockPos worldGoal, int turns) {
        Vec3 offset = plotPos.subtract(Vec3.atCenterOf(plotAnchor));
        double c = Math.round(Math.cos(turns * Math.PI / 2));
        double s = Math.round(Math.sin(turns * Math.PI / 2));
        Vec3 rotated = new Vec3(offset.x * c + offset.z * s, offset.y, offset.z * c - offset.x * s);
        return rotated.add(Vec3.atCenterOf(worldGoal));
    }
}
