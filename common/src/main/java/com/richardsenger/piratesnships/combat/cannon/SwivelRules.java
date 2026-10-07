package com.richardsenger.piratesnships.combat.cannon;

import net.minecraft.world.phys.Vec3;

/**
 * Pure rules of the swivel gun (docs/design.md §8.2, P2): where its barrel points for a yaw and an elevation, where the
 * muzzle is, and how a look direction becomes an aim. No world access, unit tested. Loading follows the cannon's rules
 * ({@link CannonRules#load}).
 *
 * <p>Geometry is in the gun's own block frame (the plot frame on a ship). The gun turns about the vertical centre
 * line of its block; the barrel pivots in the yoke at {@link #PIVOT_HEIGHT} (6 px) above the block's bottom, centred
 * in x and z; the muzzle lies {@link #MUZZLE_LENGTH} (14 px) ahead of the pivot along the barrel, the breech
 * {@link #BREECH_LENGTH} (6 px) behind it, plus a tiller to about 12 px behind. Yaw uses Minecraft's convention
 * (0° = south, +z; 90° = west, −x; 180° = north; −90° = east), elevation is positive upwards.
 */
public final class SwivelRules {

    /** Height of the barrel's pivot (the yoke's trunnions) above the bottom of the block, in blocks (6 px). */
    public static final double PIVOT_HEIGHT = 6.0 / 16.0;
    /** Distance from the pivot to the muzzle face, where the shot leaves, in blocks (14 px). */
    public static final double MUZZLE_LENGTH = 14.0 / 16.0;
    /** Distance from the pivot to the breech face, in blocks (6 px; for the model). */
    public static final double BREECH_LENGTH = 6.0 / 16.0;

    /** An aim: yaw and elevation in degrees, in the block frame. */
    public record Aim(double yawDegrees, double elevationDegrees) {

        /** Whether this aim differs from {@code other} by more than {@code degrees} in yaw or elevation. */
        public boolean differs(Aim other, double degrees) {
            return Math.abs(wrapYaw(yawDegrees - other.yawDegrees)) > degrees
                    || Math.abs(elevationDegrees - other.elevationDegrees) > degrees;
        }
    }

    private SwivelRules() {
    }

    /** {@code yaw} wrapped into (−180, 180]. */
    public static double wrapYaw(double yaw) {
        double y = yaw % 360.0;
        if (y <= -180.0) y += 360.0;
        if (y > 180.0) y -= 360.0;
        return y;
    }

    /** Unit barrel direction in the block frame for {@code yaw} and {@code elevation} (degrees). */
    public static Vec3 direction(double yawDegrees, double elevationDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double e = Math.toRadians(elevationDegrees);
        double h = Math.cos(e);
        return new Vec3(-Math.sin(yaw) * h, Math.sin(e), Math.cos(yaw) * h);
    }

    /**
     * The look direction of an entity with view {@code yaw} and {@code pitch} (degrees, Minecraft's convention: pitch
     * negative = up), with exact trigonometry (vanilla's view vector uses lookup tables).
     */
    public static Vec3 lookVector(double yawDegrees, double pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double h = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * h, -Math.sin(pitch), Math.cos(yaw) * h);
    }

    /** The barrel's pivot of the swivel gun block at {@code (x, y, z)}, in the block frame. */
    public static Vec3 pivot(int x, int y, int z) {
        return new Vec3(x + 0.5, y + PIVOT_HEIGHT, z + 0.5);
    }

    /** The muzzle point: {@link #MUZZLE_LENGTH} from the pivot along the barrel. */
    public static Vec3 muzzle(int x, int y, int z, double yawDegrees, double elevationDegrees) {
        return pivot(x, y, z).add(direction(yawDegrees, elevationDegrees).scale(MUZZLE_LENGTH));
    }

    /**
     * The aim that points the barrel along {@code look} (a direction in the block frame, any length): the yaw of its
     * horizontal part ({@code previousYaw} when it has none, looking straight up or down) and its elevation, clamped
     * to {@code minElevation..maxElevation}.
     */
    public static Aim aimFromLook(Vec3 look, double previousYaw, double minElevation, double maxElevation) {
        double h = Math.hypot(look.x, look.z);
        double yaw = h < 1.0e-6 ? previousYaw : Math.toDegrees(Math.atan2(-look.x, look.z));
        double elevation = Math.toDegrees(Math.atan2(look.y, h));
        return new Aim(wrapYaw(yaw), clampElevation(elevation, minElevation, maxElevation));
    }

    public static double clampElevation(double elevation, double min, double max) {
        return Math.max(Math.min(min, max), Math.min(Math.max(min, max), elevation));
    }
}
