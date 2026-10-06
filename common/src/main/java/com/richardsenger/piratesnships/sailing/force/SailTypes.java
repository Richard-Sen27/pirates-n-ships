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

    /** 3×3 square sail. */
    public static final SailType SMALL_SQUARE = new SailType("small_square", 9.0, 1.5, SQUARE_CURVE);
    /** 5×5 square sail. */
    public static final SailType LARGE_SQUARE = new SailType("large_square", 25.0, 2.5, SQUARE_CURVE);
    /** Fore-and-aft (lateen) sail, roughly a 4×4 triangle-ish sheet with a low center of effort. */
    public static final SailType FORE_AND_AFT = new SailType("fore_and_aft", 16.0, 2.0, FORE_AND_AFT_CURVE);

    public static final List<SailType> ALL = List.of(SMALL_SQUARE, LARGE_SQUARE, FORE_AND_AFT);

    private SailTypes() {
    }
}
