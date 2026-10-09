package com.richardsenger.piratesnships.ship.hull.pump;

/**
 * Geometry of the bilge pump's handle and piston rod (PMP1), in the pixels of the {@code facing=north} model
 * ({@code art/models/bilge_pump.bbmodel}, groups {@code handle} and {@code rod}). The brake handle turns about the pin
 * at {@link #PIVOT_Y}, {@link #PIVOT_Z}; as built it rises {@link #REST_DEGREES} towards north (its grip end), which is
 * the top of the stroke and the rest pose. A swing of {@code s} degrees lowers the grip, so the drawn handle stands at
 * {@code REST_DEGREES - s} (a positive angle about +x lifts the north end, as in the model file). The rod stands on the
 * cylinder under the handle, {@link #ROD_ARM} in front of the pin, and follows the handle's underside up and down
 * ({@link #rodLift}). Pure, unit tested.
 */
public final class PumpHandlePose {

    /** The pin the handle turns about (model pixels). */
    public static final double PIVOT_Y = 16.0;
    public static final double PIVOT_Z = 11.0;
    /** The handle's angle as built, at the top of its stroke (degrees about +x, north end up). */
    public static final double REST_DEGREES = 22.5;
    /** Half the handle beam's thickness (it is 1.2 px high around the pin). */
    public static final double HALF_THICKNESS = 0.6;
    /** Distance from the pin to the rod's axis (z 8), towards the grip. */
    public static final double ROD_ARM = 3.0;
    /** Distance from the pin to the front edge of the cylinder's iron lip (z 5.3), and the lip's top. */
    public static final double LIP_ARM = 5.7;
    public static final double LIP_TOP = 14.4;
    /** The largest swing whose handle still clears the cylinder lip by 0.05 px (about 32.2 degrees). */
    public static final double MAX_SWING = maxSwing(0.05);

    private PumpHandlePose() {
    }

    /** The drawn handle angle (degrees about +x, north end up) for a swing of {@code swing} degrees down. */
    public static double handleDegrees(double swing) {
        return REST_DEGREES - swing;
    }

    /** Height (model pixels) of the handle's underside {@code arm} pixels in front of the pin at handle angle {@code degrees}. */
    public static double undersideAt(double arm, double degrees) {
        double a = Math.toRadians(degrees);
        return PIVOT_Y - HALF_THICKNESS * Math.cos(a) + arm * Math.sin(a);
    }

    /** How far (model pixels, negative = down) the rod moves at a swing of {@code swing} degrees, against rest. */
    public static double rodLift(double swing) {
        return undersideAt(ROD_ARM, handleDegrees(swing)) - undersideAt(ROD_ARM, REST_DEGREES);
    }

    /** The largest swing at which the handle's underside stays {@code gap} px above the cylinder lip. */
    static double maxSwing(double gap) {
        double lo = 0;
        double hi = 90;
        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) / 2;
            if (undersideAt(LIP_ARM, handleDegrees(mid)) >= LIP_TOP + gap) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
