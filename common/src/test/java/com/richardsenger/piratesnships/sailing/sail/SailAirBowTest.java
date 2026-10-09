package com.richardsenger.piratesnships.sailing.sail;

import com.richardsenger.piratesnships.sailing.force.EfficiencyCurve;
import com.richardsenger.piratesnships.sailing.force.SailTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** VIS1c: the sails read the wind off the ship's synced bow, and draw by the force model's braced-yard rule. */
class SailAirBowTest {

    /** A square sail's out axis along plot z, the ship's bow toward +z (south), wind blowing toward -z: head to wind. */
    private static final double AX = 0, AZ = 1;

    @Test
    void knownBowLuffsHeadToWindFromTheFirstFrame() {
        SailAir.Bow bow = new SailAir.Bow().read(0, 1, AX, AZ, 0, 0.0); // nothing guessed yet, not moving
        assertTrue(bow.known);
        assertEquals(1, bow.sign);
        double beta = SailAir.beta(0, -5, bow.x, bow.z, bow.sign);
        assertEquals(0.0, beta, 1e-9, "head to wind");
        assertEquals(0f, SailAir.fillBraced(SailTypes.SQUARE_CURVE, beta), 0f, "luffs");
        // VIS1b without a bow read the same wind as from astern and drew
        double guessed = SailAir.beta(0, -5, AX, AZ, 0);
        assertEquals(180.0, guessed, 1e-9);
    }

    @Test
    void pushAsternNeverFlipsAKnownBow() {
        SailAir.Bow bow = new SailAir.Bow();
        int guess = 0;
        for (double v : new double[] {0.0, -3.0, -6.0, 2.0, -9.0}) {
            bow.read(0, 1, AX, AZ, guess, v);
            guess = bow.guess;
            assertTrue(bow.known);
            assertEquals(0.0, bow.x, 0.0);
            assertEquals(1.0, bow.z, 0.0);
            assertEquals(1, bow.sign, "pushed at " + v);
            assertEquals(1, bow.guess, "the guess follows the known bow");
        }
        // the same pushes without a bow flip the guess (VIS1b's fallback, unchanged)
        bow.read(0, 0, AX, AZ, 1, -3.0);
        assertFalse(bow.known);
        assertEquals(-1, bow.sign);
    }

    @Test
    void unknownBowKeepsTheMotionGuess() {
        SailAir.Bow bow = new SailAir.Bow();
        assertEquals(0, bow.read(0, 0, AX, AZ, 0, 0.5).sign);
        assertEquals(1, bow.read(0, 0, AX, AZ, 0, 2.0).sign);
        assertEquals(1, bow.read(0, 0, AX, AZ, 1, -0.3).sign, "drifting astern a little keeps the guess");
        assertEquals(-1, bow.read(0, 0, AX, AZ, 1, -1.5).sign);
        assertEquals(AX, bow.x, 0.0);
        assertEquals(AZ, bow.z, 0.0);
    }

    @Test
    void knownBowAcrossTheSailAxisIsUsedAsIs() {
        // a yard along the ship (its out axis across the bow): the bow is the ship's, not the cloth's
        SailAir.Bow bow = new SailAir.Bow().read(-1, 0, 0, 1, -1, 4.0);
        assertTrue(bow.known);
        assertEquals(-1.0, bow.x, 0.0);
        assertEquals(0.0, bow.z, 0.0);
        assertEquals(-1, bow.guess, "no projection on the axis: the old guess stays");
        assertEquals(0.0, SailAir.beta(5, 0, bow.x, bow.z, bow.sign), 1e-9, "wind toward +x meets a west bow head on");
        assertEquals(180.0, SailAir.beta(-5, 0, bow.x, bow.z, bow.sign), 1e-9);
    }

    @Test
    void beamReachDrawsWithBracedYards() {
        // bow +z, wind from the beam (blowing toward +x): along the cloth of square yards
        double beta = SailAir.beta(5, 0, 0, 1, 1);
        assertEquals(90.0, beta, 1e-9);
        double square = SailAir.squareness(5, 0, 0, 1);
        assertEquals(0f, SailAir.fill(SailTypes.SQUARE_CURVE, beta, square), 0f, "VIS1b: luffing");
        assertEquals(1f, SailAir.fillBraced(SailTypes.SQUARE_CURVE, beta), 1e-6f, "VIS1c: drawing");
        assertEquals(1f, SailAir.fillBraced(SailTypes.FORE_AND_AFT_CURVE, beta), 1e-6f);
    }

    @Test
    void bracedFillLuffsInTheNoGoZoneAndDrawsDownwind() {
        assertEquals(0f, SailAir.fillBraced(SailTypes.SQUARE_CURVE, 0.0), 0f);
        assertEquals(0f, SailAir.fillBraced(SailTypes.SQUARE_CURVE, 30.0), 0f);
        assertEquals(0f, SailAir.fillBraced(SailTypes.SQUARE_CURVE, 45.0), 0f, "square sails: no drive at 45");
        assertEquals(1f, SailAir.fillBraced(SailTypes.SQUARE_CURVE, 180.0), 1e-6f);
        assertEquals(0f, SailAir.fillBraced(SailTypes.FORE_AND_AFT_CURVE, 20.0), 0f);
        assertEquals(1f, SailAir.fillBraced(SailTypes.FORE_AND_AFT_CURVE, 50.0), 1e-6f, "a stay points higher");
    }

    /**
     * The braced rule draws exactly where the force model drives: for apparent winds all around a ship with its bow
     * along +z, β from {@link SailAir#beta} equals the force model's {@code atan2(|lat|, -fwd)} and the cloth draws iff
     * the drive is positive.
     */
    @Test
    void bracedFillFollowsTheForceModelAllAround() {
        for (EfficiencyCurve curve : new EfficiencyCurve[] {SailTypes.SQUARE_CURVE, SailTypes.FORE_AND_AFT_CURVE}) {
            for (int deg = 0; deg < 360; deg += 5) {
                double ax = 4 * Math.sin(Math.toRadians(deg));
                double az = 4 * Math.cos(Math.toRadians(deg));
                double beta = SailAir.beta(ax, az, 0, 1, 1);
                double model = Math.toDegrees(Math.atan2(Math.abs(ax), -az)); // SailForceModel, bow +z, port +x
                assertEquals(model, beta, 1e-9, "beta at " + deg);
                float fill = SailAir.fillBraced(curve, beta);
                double drive = curve.drive(beta);
                assertEquals(drive > 0, fill > 0f, "drawing iff driving at " + deg + " (drive " + drive + ")");
                assertTrue(fill >= 0f && fill <= 1f);
            }
        }
    }
}
