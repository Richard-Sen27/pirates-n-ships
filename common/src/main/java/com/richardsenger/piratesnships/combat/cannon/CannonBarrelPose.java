package com.richardsenger.piratesnships.combat.cannon;

/**
 * Pure geometry of the drawn cannon barrel (CAN2, docs/design.md §4.8 "Cannon barrel tilt"): the angle the barrel model
 * is drawn at and where the quoin (the wedge under the breech) sits for it. No world access, unit tested; the renderer
 * {@code client/CannonBarrelRenderer} applies it.
 *
 * <p>All lengths are pixels in the master block's model frame (muzzle to the north, the {@code facing=north} model of
 * {@code art/models/cannon.bbmodel}): the barrel turns about the trunnion axis, which runs along x through
 * ({@link #PIVOT_Y}, {@link #PIVOT_Z}); a positive angle raises the muzzle (north end) and lowers the breech, the same
 * sign as {@link CannonRules#muzzleDirection}. The barrel profile below is the part list of
 * {@code tools/gen_cannon_models.py} before the lint's 0.05 px insets, so it is never smaller than the model.
 */
public final class CannonBarrelPose {

    /** The trunnion axis: height above the master's bottom and z, in pixels ({@link CannonRules#PIVOT_HEIGHT}). */
    public static final double PIVOT_Y = CannonRules.PIVOT_HEIGHT * 16.0;
    public static final double PIVOT_Z = 8.0;

    /**
     * The angles the model can show without the barrel cutting into its carriage: at 20° the breech's base ring hangs
     * 0.1 px above the stool bed (cheeks and bed were cut for that); below −20° the muzzle hangs low over the block ahead.
     * A server that allows more keeps aiming there; only the drawing stops.
     */
    public static final double MIN_DRAWN_DEGREES = -20.0;
    public static final double MAX_DRAWN_DEGREES = 20.0;

    /** Top of the stool bed the quoin rests on. */
    public static final double STOOL_TOP = 5.0;
    /** Top of the quoin at rest (the gun level). */
    public static final double QUOIN_TOP = 9.6;
    /** The quoin's z span, handle included. */
    public static final double QUOIN_Z0 = 18.8;
    public static final double QUOIN_Z1 = 27.0;
    /** How far the quoin is drawn back (toward the rear) once the breech has come down onto the stool. */
    public static final double MAX_SLIDE = 1.7;
    /** The thinnest the quoin is squeezed to (a factor of its rest height). */
    public static final double MIN_SCALE = 0.01;
    /** The thickest it grows to while the breech rises (muzzle down). */
    public static final double MAX_SCALE = 2.0;

    /** The barrel's round sections: {z0, z1, D} in pixels (tube, rings, breech, neck, cascabel). */
    static final double[][] SECTIONS = {
            {-15.0, -14.2, 7.0}, {-14.2, -12.6, 6.5}, {-12.6, -12.0, 6.3}, {-12.0, -4.0, 5.8}, {-4.0, 1.6, 6.2},
            {1.6, 2.4, 7.0}, {2.4, 12.4, 6.9}, {12.4, 13.2, 7.7}, {13.2, 21.4, 7.5}, {21.4, 22.6, 8.3},
            {22.6, 24.0, 7.2}, {24.0, 24.9, 2.2}, {24.9, 26.0, 3.2},
    };
    private static final double SAMPLE = 0.05;

    private CannonBarrelPose() {
    }

    /** The angle the barrel is drawn at for an elevation, clamped to what the model can show. */
    public static double drawnDegrees(double elevationDegrees) {
        if (Double.isNaN(elevationDegrees)) return 0.0;
        return Math.max(MIN_DRAWN_DEGREES, Math.min(MAX_DRAWN_DEGREES, elevationDegrees));
    }

    /**
     * Height (px above the master's bottom) of the lowest point of the barrel's underside that lies over the model z
     * span {@code [z0, z1]} at {@code degrees}; {@code +∞} when no part of the barrel lies over it. The sections are
     * octagons whose bottom face is flat at D/2 below the axis, so their underside is a straight edge per section.
     */
    public static double undersideY(double degrees, double z0, double z1) {
        double a = Math.toRadians(degrees);
        double cos = Math.cos(a);
        double sin = Math.sin(a);
        double lowest = Double.POSITIVE_INFINITY;
        for (double[] s : SECTIONS) {
            double radius = s[2] / 2.0;
            int n = (int) Math.ceil((s[1] - s[0]) / SAMPLE);
            for (int i = 0; i <= n; i++) {
                double dz = s[0] + (s[1] - s[0]) * i / n - PIVOT_Z;
                double dy = -radius;
                // Axis.XP.rotationDegrees(degrees) about the pivot: y' = y cos − z sin, z' = y sin + z cos
                double y = dy * cos - dz * sin;
                double z = dy * sin + dz * cos + PIVOT_Z;
                if (z >= z0 && z <= z1) {
                    lowest = Math.min(lowest, y + PIVOT_Y);
                }
            }
        }
        return lowest;
    }

    /**
     * Where the quoin is drawn for a barrel at {@code drawnDegrees}: squeezed to {@code scale} of its rest height about
     * the stool top (its top keeps the gap it has under the breech at rest) and drawn back by {@code slide} px toward
     * the rear as it thins. At 0° it rests as modelled ({@code scale} 1, {@code slide} 0); muzzle up it shrinks to
     * nothing, so the breech can come down onto the stool; muzzle down it grows to stay under the rising breech.
     */
    public static Quoin quoin(double drawnDegrees) {
        double span1 = QUOIN_Z1 + MAX_SLIDE;
        double gap = undersideY(0.0, QUOIN_Z0, span1) - QUOIN_TOP;
        double top = undersideY(drawnDegrees, QUOIN_Z0, span1) - gap;
        double scale = (top - STOOL_TOP) / (QUOIN_TOP - STOOL_TOP);
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        double slide = MAX_SLIDE * Math.max(0.0, Math.min(1.0, 1.0 - scale));
        return new Quoin(scale, slide);
    }

    /** The quoin's draw transform: a y scale about {@link #STOOL_TOP} and a slide toward the rear (+z), in pixels. */
    public record Quoin(double scale, double slide) {
    }
}
