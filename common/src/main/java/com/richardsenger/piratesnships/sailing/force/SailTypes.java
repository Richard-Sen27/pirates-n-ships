package com.richardsenger.piratesnships.sailing.force;

import java.util.List;

/**
 * The built-in sail types (docs/design.md §5.2).
 *
 * <p>Curves (apparent wind angle off the bow → drive, leeward side force):
 * <ul>
 *   <li><b>Square</b>: useless inside 45°, barely useful at 60°, growing steadily to its maximum dead downwind.
 *       A little negative drive (windage, the sail is taken aback) close to the wind.</li>
 *   <li><b>Fore-and-aft / lateen</b>: no drive inside 30°, already 0.55 at 45° (close-hauled), maximum at a beam reach
 *       (90°), and less than half of a square sail's drive per area dead downwind. Large side force when close-hauled,
 *       which the keel must resist.</li>
 * </ul>
 */
public final class SailTypes {

    /** Apparent wind angles at or below this produce no forward drive from any built-in sail [degrees]. */
    public static final double NO_GO_DEGREES = 30.0;

    public static final EfficiencyCurve SQUARE_CURVE = EfficiencyCurve.of(
            0, -0.10, 0.00,
            15, -0.10, 0.03,
            30, -0.06, 0.08,
            45, 0.00, 0.15,
            60, 0.08, 0.30,
            75, 0.22, 0.42,
            90, 0.38, 0.48,
            105, 0.54, 0.46,
            120, 0.68, 0.40,
            135, 0.80, 0.32,
            150, 0.90, 0.22,
            165, 0.97, 0.11,
            180, 1.00, 0.00);

    public static final EfficiencyCurve FORE_AND_AFT_CURVE = EfficiencyCurve.of(
            0, -0.05, 0.00,
            15, -0.04, 0.02,
            30, 0.00, 0.10,
            45, 0.55, 0.70,
            60, 0.80, 0.65,
            75, 0.95, 0.55,
            90, 1.00, 0.45,
            105, 0.95, 0.35,
            120, 0.85, 0.28,
            135, 0.72, 0.20,
            150, 0.60, 0.13,
            165, 0.50, 0.07,
            180, 0.45, 0.00);

    /**
     * Boomed fore-and-aft sail (SAIL5): a triangular sail whose foot runs along a boom. Close to the wind and on a reach
     * like the plain fore-and-aft sail; let out on its boom it keeps drawing on a broad reach and a run (0.75 dead
     * downwind against the jib's 0.45), so it serves from close-hauled to a run on either tack. This curve assumes the
     * boom sheeted to the best angle; the real boom angle scales it ({@code sailing.sail.BoomRules#factor}, through
     * {@link SailInstance#boom()}).
     */
    public static final EfficiencyCurve BOOMED_CURVE = EfficiencyCurve.of(
            0, -0.05, 0.00,
            15, -0.04, 0.02,
            30, 0.00, 0.10,
            45, 0.60, 0.70,
            60, 0.85, 0.62,
            75, 0.97, 0.52,
            90, 1.00, 0.42,
            105, 1.00, 0.33,
            120, 0.97, 0.25,
            135, 0.92, 0.18,
            150, 0.86, 0.11,
            165, 0.80, 0.05,
            180, 0.75, 0.00);

    /**
     * Square sail between two yards (docs/design.md §5.2, rule F5a). Its real area comes from the yards and travels in
     * {@link SailInstance#area()}; 9 (three wide, three deep) is only the reference area that the three-argument
     * {@link SailInstance} constructor uses. The center of effort height is 0 because the runtime passes the centroid
     * of the drawn cloth as the sail's position.
     */
    public static final SailType SQUARE = new SailType("square", 9.0, 0.0, SQUARE_CURVE);
    /**
     * Triangular (fore-and-aft) sail between a rope stay and cleats (docs/design.md §5.2, rule F5b). Its real area comes
     * from the triangle and travels in {@link SailInstance#area()}; 16 is only the reference area of the three-argument
     * {@link SailInstance} constructor. The center of effort height is 0 because the runtime passes the centroid of the
     * drawn cloth as the sail's position.
     */
    public static final SailType FORE_AND_AFT = new SailType("fore_and_aft", 16.0, 0.0, FORE_AND_AFT_CURVE);

    /**
     * A triangular sail with a boom in its clew's place (SAIL5): its area and centroid come from the triangle with the
     * boom swung out, its drive from {@link #BOOMED_CURVE} scaled by the boom's angle to the wind.
     */
    public static final SailType BOOMED = new SailType("boomed", 16.0, 0.0, BOOMED_CURVE);

    public static final List<SailType> ALL = List.of(SQUARE, FORE_AND_AFT, BOOMED);

    private SailTypes() {
    }
}
