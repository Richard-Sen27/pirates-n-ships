package com.richardsenger.piratesnships.sailing.sail;

/**
 * The boom of a triangular sail (SAIL5, docs/design.md §5.2 "The boom and the triangular-sail scene"), pure: no
 * Minecraft classes, so JUnit needs no bootstrap.
 *
 * <h2>Sheet</h2>
 * The boom's {@code sheet} is a step of {@link #STEP_DEGREES} from its centred direction (the direction from the boom's
 * pivot to the sail's tack), {@link #CENTRE} being centred: 9 steps from −60° to +60°. Positive turns the boom
 * <b>clockwise seen from above</b> (Minecraft's yaw sense: east to south), in any frame with {@code y} up whose
 * horizontal axes are {@code (x, z)} the way Minecraft's are: the world, a ship's plot, and the ship frame with
 * {@code x} = port and {@code z} = forward ({@code sailing.force.ShipFrame}). {@link #turn} is that rotation.
 *
 * <h2>The wind on a boomed sail</h2>
 * Ship-frame vectors are passed as {@code (port, forward)}. The apparent wind is a flow vector (the direction it blows
 * toward). Its angle off the bow {@code β} (0 head to wind, 180 dead astern) and the side it blows toward (the leeward
 * side) are those of {@code SailForceModel}. The boom's angle {@code φ} is the angle between the boom and the ship's
 * fore-and-aft line (0..90), its side is where the boom's end lies. A boomed sail gives
 * {@code factor × BOOMED_CURVE(β)}, where the factor is:
 * <ul>
 *   <li><b>0, luffing,</b> closer than {@code minWind} (config {@code sailing.boom_min_wind_angle}) to the wind;</li>
 *   <li><b>0, aback,</b> with the boom out on the windward side ({@link #aback}): the wind presses the cloth the wrong
 *       way. Running by the lee up to {@link #BY_THE_LEE} past dead astern still draws;</li>
 *   <li><b>0, luffing,</b> when the wind runs along the cloth (the angle between the wind's line and the boom's under
 *       {@link #LUFF_DEGREES});</li>
 *   <li>otherwise {@code 1 − ((φ − φ*) / 60°)²}, where the best boom angle {@code φ* = clamp(β − minWind, 0, 60°)}:
 *       centred close-hauled, 45° out on a beam reach, all the way out (60°) from a broad reach to a run.</li>
 * </ul>
 *
 * <h2>Trim and gybe</h2>
 * {@link #bestStep} is the step with the largest factor (ties: the one nearest the current step, then the centre;
 * none drawing: the centre). A boom aback with the wind at least {@code minWind} off the bow swings across to the
 * mirrored step by itself ({@link #swing}): a <b>gybe</b> when the wind comes from aft of the beam (it crossed the
 * stern; the boom slams across), a quiet swing when it comes from forward of the beam (it crossed the bow: a tack).
 */
public final class BoomRules {

    /** Number of sheet steps (the {@code sheet} block state's values). */
    public static final int STEPS = 9;
    /** The centred step. */
    public static final int CENTRE = 4;
    /** Degrees per step. */
    public static final double STEP_DEGREES = 15.0;
    /** The farthest the boom swings out each way [degrees]. */
    public static final double MAX_DEGREES = STEP_DEGREES * CENTRE;
    /** Default of {@code sailing.boom_min_wind_angle} [degrees off the wind]. */
    public static final double DEFAULT_MIN_WIND = 45.0;
    /** How far past dead astern on the boom's side the wind may come before the sail is aback (running by the lee). */
    public static final double BY_THE_LEE = 10.0;
    /** Below this angle between the wind's line and the boom's the cloth luffs [degrees]. */
    public static final double LUFF_DEGREES = 10.0;

    private static final double EPS = 1.0e-9;

    /** What the wind does to a boom this tick ({@link #swing}). */
    public enum Swing {
        /** Nothing: the boom draws, luffs head to wind, or lies centred. */
        NONE,
        /** Aback with the wind forward of the beam: the boom swings quietly across (the ship tacked). */
        TACK,
        /** Aback with the wind aft of the beam: the boom slams across (the wind crossed the stern). */
        GYBE
    }

    private BoomRules() {
    }

    /** {@code step} kept within 0..{@link #STEPS}−1. */
    public static int clamp(int step) {
        return Math.min(STEPS - 1, Math.max(0, step));
    }

    /** The sheet angle of {@code step} [degrees, clockwise seen from above], −60..60. */
    public static double angle(int step) {
        return (clamp(step) - CENTRE) * STEP_DEGREES;
    }

    /** The same angle on the other side of the centre. */
    public static int mirror(int step) {
        return 2 * CENTRE - clamp(step);
    }

    /** One step from {@code current} toward {@code target} (or {@code current} when they are equal). */
    public static int stepToward(int current, int target) {
        return current + Integer.signum(clamp(target) - clamp(current));
    }

    /**
     * The horizontal vector {@code (x, z)} turned clockwise seen from above by {@code degrees}: {@code (1, 0)} by 90°
     * gives {@code (0, 1)} (east to south in the world, port to forward in the ship frame).
     */
    public static double[] turn(double x, double z, double degrees) {
        double r = Math.toRadians(degrees);
        double c = Math.cos(r), s = Math.sin(r);
        return new double[] {x * c - z * s, x * s + z * c};
    }

    /** The apparent wind's angle off the bow [degrees, 0 head to wind, 180 dead astern] from its flow (port, forward). */
    public static double beta(double flowPort, double flowFwd) {
        return Math.toDegrees(Math.atan2(Math.abs(flowPort), -flowFwd));
    }

    /** The side the wind blows toward: +1 port, −1 starboard, 0 straight along the ship (or calm). */
    public static int leeward(double flowPort, double flowFwd) {
        double speed = Math.hypot(flowPort, flowFwd);
        if (speed < EPS || Math.abs(flowPort) < 1.0e-6 * speed) {
            return 0;
        }
        return flowPort > 0 ? 1 : -1;
    }

    /** The side the boom's end lies on: +1 port, −1 starboard, 0 on the fore-and-aft line. */
    public static int side(double boomPort, double boomFwd) {
        double len = Math.hypot(boomPort, boomFwd);
        if (len < EPS || Math.abs(boomPort) < 1.0e-6 * len) {
            return 0;
        }
        return boomPort > 0 ? 1 : -1;
    }

    /** The boom's angle off the fore-and-aft line [degrees, 0..90]. */
    public static double boomAngle(double boomPort, double boomFwd) {
        return Math.toDegrees(Math.atan2(Math.abs(boomPort), Math.abs(boomFwd)));
    }

    /**
     * Whether the boom is out on the windward side: the wind presses the cloth against the wrong side. Not when the boom
     * lies on the fore-and-aft line or the wind blows straight along the ship, and not when running by the lee less than
     * {@link #BY_THE_LEE} past dead astern.
     */
    public static boolean aback(double flowPort, double flowFwd, double boomPort, double boomFwd) {
        int s = side(boomPort, boomFwd);
        int lee = leeward(flowPort, flowFwd);
        if (s == 0 || lee == 0 || s == lee) {
            return false;
        }
        return beta(flowPort, flowFwd) < 180.0 - BY_THE_LEE;
    }

    /** The best angle of the boom off the fore-and-aft line for a wind {@code betaDeg} off the bow [degrees]. */
    public static double bestAngle(double betaDeg, double minWind) {
        return Math.min(MAX_DEGREES, Math.max(0.0, betaDeg - minWind));
    }

    /**
     * How well a boomed sail draws, 0..1 (see the class comment), with the apparent wind's flow {@code (flowPort,
     * flowFwd)} and the boom from its pivot to its end {@code (boomPort, boomFwd)}, both in the ship frame.
     */
    public static double factor(double flowPort, double flowFwd, double boomPort, double boomFwd, double minWind) {
        double speed = Math.hypot(flowPort, flowFwd);
        double len = Math.hypot(boomPort, boomFwd);
        if (speed < EPS || len < EPS) {
            return 0.0;
        }
        double beta = beta(flowPort, flowFwd);
        if (beta < minWind || aback(flowPort, flowFwd, boomPort, boomFwd)) {
            return 0.0;
        }
        double along = Math.abs(flowPort * boomPort + flowFwd * boomFwd) / (speed * len);
        double alpha = Math.toDegrees(Math.acos(Math.min(1.0, along)));
        if (alpha < LUFF_DEGREES) {
            return 0.0;
        }
        double d = (boomAngle(boomPort, boomFwd) - bestAngle(beta, minWind)) / MAX_DEGREES;
        return Math.max(0.0, 1.0 - d * d);
    }

    /** {@link #factor} with the boom at {@code step} from its centred direction {@code (centredPort, centredFwd)}. */
    public static double factorAt(double flowPort, double flowFwd, double centredPort, double centredFwd, int step, double minWind) {
        double[] b = turn(centredPort, centredFwd, angle(step));
        return factor(flowPort, flowFwd, b[0], b[1], minWind);
    }

    /**
     * The step that draws best (largest {@link #factor}); ties go to the step nearest {@code current}, then to the
     * centre. When no step draws (head to wind, a calm), the centre: the crew hauls the boom in.
     */
    public static int bestStep(double flowPort, double flowFwd, double centredPort, double centredFwd, int current, double minWind) {
        int best = CENTRE;
        double bestFactor = 0.0;
        for (int step = 0; step < STEPS; step++) {
            double f = factorAt(flowPort, flowFwd, centredPort, centredFwd, step, minWind);
            if (f > bestFactor + 1.0e-9) {
                best = step;
                bestFactor = f;
            } else if (f > 0.0 && Math.abs(f - bestFactor) <= 1.0e-9 && closer(step, best, current)) {
                best = step;
            }
        }
        return best;
    }

    private static boolean closer(int step, int than, int current) {
        int a = Math.abs(step - current), b = Math.abs(than - current);
        return a != b ? a < b : Math.abs(step - CENTRE) < Math.abs(than - CENTRE);
    }

    /**
     * What the wind does to the boom at {@code step}: it swings across to {@link #mirror} when the sail is aback with
     * the wind at least {@code minWind} off the bow, as a {@link Swing#GYBE} from aft of the beam (β ≥ 90°), else as a
     * {@link Swing#TACK}.
     */
    public static Swing swing(double flowPort, double flowFwd, double centredPort, double centredFwd, int step, double minWind) {
        if (clamp(step) == CENTRE) {
            return Swing.NONE;
        }
        double[] b = turn(centredPort, centredFwd, angle(step));
        if (!aback(flowPort, flowFwd, b[0], b[1])) {
            return Swing.NONE;
        }
        double beta = beta(flowPort, flowFwd);
        if (beta < minWind) {
            return Swing.NONE;
        }
        return beta >= 90.0 ? Swing.GYBE : Swing.TACK;
    }

    /**
     * Which way a hand on the boom sheets it (manual sheeting): +1 (clockwise seen from above) when the click
     * {@code (hitX, hitZ)}, relative to the pivot, lies clockwise of the boom's direction {@code (armX, armZ)}, −1 when
     * it lies the other way, 0 on the boom's line. The click pushes the boom toward its side.
     */
    public static int clickSide(double armX, double armZ, double hitX, double hitZ) {
        double cross = armX * hitZ - armZ * hitX;
        return Math.abs(cross) < 1.0e-6 ? 0 : cross > 0 ? 1 : -1;
    }
}
