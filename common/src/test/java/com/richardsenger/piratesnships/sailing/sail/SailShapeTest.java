package com.richardsenger.piratesnships.sailing.sail;

import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.force.SailTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The moving sail cloth (VIS1b): belly, flutter, slack, furled, easing and continuity. */
class SailShapeTest {

    private static final float MAX_BELLY = 0.6f;
    private static final float FLUTTER = 0.15f;
    private static final float EPS = 1.0e-5f;

    /** A look settled on the targets of the given air. */
    private static SailShape.Look settled(SailTrim trim, float drop, double wind, float fill, int side) {
        SailShape.Look look = new SailShape.Look(side, 0);
        look.approach(SailShape.bellyTarget(trim, drop, wind, fill, MAX_BELLY),
                SailShape.flutterTarget(trim, drop, wind, fill, FLUTTER), side, fill, 0.0);
        return look;
    }

    /** Largest |displacement| of a square sail's grid over a stretch of time. */
    private static float maxSquare(SailShape.Look look, float height) {
        float max = 0f;
        for (int k = 0; k < 120; k++) {
            for (int i = 0; i <= 8; i++) {
                for (int j = 0; j <= 8; j++) {
                    max = Math.max(max, Math.abs(SailShape.square(look, j / 8f, i / 8f, false, height, k * 0.7, 0.4f)));
                }
            }
        }
        return max;
    }

    @Test
    void aDrawingSailBelliesToLeeward() {
        for (int side : new int[]{1, -1}) {
            SailShape.Look look = settled(SailTrim.FULL, 4f, 10.0, 1f, side);
            for (int k = 0; k < 100; k++) {
                float mid = SailShape.square(look, 0.5f, 0.5f, false, 4f, k * 1.3, 2f);
                assertTrue(mid * side > 0.3f, "square belly on side " + side + ": " + mid);
                float tri = SailShape.triangle(look, 1f / 3f, 1f / 3f, 5f, k * 1.3, 2f);
                assertTrue(tri * side > 0.3f, "triangle belly on side " + side + ": " + tri);
            }
        }
    }

    @Test
    void theBellyIsDeepestInTheMiddleAndZeroAtTheEdges() {
        SailShape.Look look = settled(SailTrim.FULL, 4f, 12.0, 1f, 1);
        double time = 3.0;
        float mid = SailShape.square(look, 0.5f, 0.5f, false, 4f, time, 0f);
        assertEquals(0f, SailShape.square(look, 0.5f, 0f, false, 4f, time, 0f), EPS, "at the upper yard");
        assertEquals(0f, SailShape.square(look, 0.5f, 1f, false, 4f, time, 0f), EPS, "at the lower yard");
        assertEquals(0f, SailShape.square(look, 0f, 0.5f, false, 4f, time, 0f), EPS, "at the side edge");
        assertTrue(mid > SailShape.square(look, 0.25f, 0.5f, false, 4f, time, 0f));
        assertTrue(mid > SailShape.square(look, 0.5f, 0.25f, false, 4f, time, 0f));
        // a reefed sail's free foot keeps its belly
        assertTrue(SailShape.square(look, 0.5f, 1f, true, 2f, time, 0f) > 0.3f);
        // the triangle is held on all three edges
        assertEquals(0f, SailShape.triangle(look, 0f, 0.5f, 5f, time, 0f), EPS);
        assertEquals(0f, SailShape.triangle(look, 0.5f, 0f, 5f, time, 0f), EPS);
        assertEquals(0f, SailShape.triangle(look, 0.5f, 0.5f, 5f, time, 0f), EPS);
    }

    @Test
    void theDepthGrowsWithTheWindUpToTheCap() {
        float last = -1f;
        for (double wind = 0.0; wind <= 30.0; wind += 1.0) {
            float b = SailShape.bellyTarget(SailTrim.FULL, 4f, wind, 1f, MAX_BELLY);
            assertTrue(b >= last - EPS, "monotonic at " + wind);
            assertTrue(b <= MAX_BELLY + EPS, "capped at " + wind);
            last = b;
        }
        assertEquals(MAX_BELLY, SailShape.bellyTarget(SailTrim.FULL, 4f, SailShape.FULL_WIND, 1f, MAX_BELLY), EPS,
                "max_belly over a 4-block drop in a full wind");
        assertTrue(SailShape.bellyTarget(SailTrim.FULL, 4f, 3.0, 1f, MAX_BELLY)
                < SailShape.bellyTarget(SailTrim.FULL, 4f, 9.0, 1f, MAX_BELLY));
        // bigger sails belly deeper in proportion, within limits
        assertEquals(2f * MAX_BELLY, SailShape.cap(8f, MAX_BELLY), EPS);
        assertEquals(SailShape.MAX_SIZE_FACTOR * MAX_BELLY, SailShape.cap(30f, MAX_BELLY), EPS);
        assertEquals(MAX_BELLY * 0.5f, SailShape.cap(2f, MAX_BELLY), EPS);
        // the drawn shape never goes past the cap and the breathing
        SailShape.Look look = settled(SailTrim.FULL, 4f, 50.0, 1f, 1);
        assertTrue(maxSquare(look, 4f) <= MAX_BELLY * (1f + SailShape.BREATH) + EPS);
        assertTrue(maxSquare(look, 4f) <= SailShape.maxReach(MAX_BELLY, FLUTTER));
    }

    @Test
    void aReefedSailBelliesLess() {
        float full = SailShape.bellyTarget(SailTrim.FULL, 4f, 10.0, 1f, MAX_BELLY);
        float half = SailShape.bellyTarget(SailTrim.HALF, 4f, 10.0, 1f, MAX_BELLY);
        assertEquals(full * SailShape.REEF_FACTOR, half, EPS);
        assertTrue(half > 0f);
    }

    @Test
    void flutterOnlyWhileLuffing() {
        assertEquals(0f, SailShape.flutterTarget(SailTrim.FULL, 4f, 12.0, 1f, FLUTTER), EPS, "drawing: no flutter");
        float luff = SailShape.flutterTarget(SailTrim.FULL, 4f, 12.0, 0f, FLUTTER);
        assertEquals(FLUTTER, luff, EPS, "luffing in a full wind");
        assertTrue(SailShape.flutterTarget(SailTrim.FULL, 4f, 4.0, 0f, FLUTTER) < luff, "less in less wind");
        assertTrue(SailShape.flutterTarget(SailTrim.HALF, 4f, 12.0, 0f, FLUTTER) < luff, "reefed less");
        // a luffing sail moves both ways over time; a drawing one keeps its side
        SailShape.Look luffing = settled(SailTrim.FULL, 4f, 12.0, 0f, 1);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int k = 0; k < 40; k++) {
            float d = SailShape.square(luffing, 0.5f, 0.5f, false, 4f, k * 0.5, 0f);
            min = Math.min(min, d);
            max = Math.max(max, d);
        }
        assertTrue(min < 0f && max > 0f, "luffing cloth shakes through its rest plane: " + min + " .. " + max);
        assertTrue(max - min > FLUTTER, "and visibly: " + (max - min));
        SailShape.Look drawing = settled(SailTrim.FULL, 4f, 12.0, 1f, 1);
        float dMin = Float.MAX_VALUE, dMax = -Float.MAX_VALUE;
        for (int k = 0; k < 200; k++) {
            float d = SailShape.square(drawing, 0.5f, 0.5f, false, 4f, k * 0.5, 0f);
            dMin = Math.min(dMin, d);
            dMax = Math.max(dMax, d);
        }
        assertTrue(dMin > 0f, "a drawing sail never flaps through");
        assertTrue(dMax - dMin <= 2f * SailShape.BREATH * MAX_BELLY + EPS, "it only breathes: " + (dMax - dMin));
    }

    @Test
    void calmAirLeavesASlightSag() {
        for (float fill : new float[]{0f, 0.5f, 1f}) {
            float b = SailShape.bellyTarget(SailTrim.FULL, 4f, 0.0, fill, MAX_BELLY);
            assertEquals(SailShape.SLACK * MAX_BELLY, b, EPS, "sag at fill " + fill);
            assertEquals(0f, SailShape.flutterTarget(SailTrim.FULL, 4f, 0.0, fill, FLUTTER), EPS, "no flutter in a calm");
        }
        SailShape.Look calm = settled(SailTrim.FULL, 4f, 0.0, 0f, -1);
        float first = SailShape.square(calm, 0.5f, 0.5f, false, 4f, 0.0, 0f);
        assertTrue(first < 0f && first > -0.1f, "a slight sag to the side: " + first);
        for (int k = 1; k < 100; k++) {
            assertEquals(first, SailShape.square(calm, 0.5f, 0.5f, false, 4f, k * 1.7, 0f), EPS, "still in a calm");
        }
    }

    @Test
    void aFurledSailHasNoShape() {
        for (double wind : new double[]{0.0, 6.0, 20.0}) {
            for (float fill : new float[]{0f, 1f}) {
                assertEquals(0f, SailShape.bellyTarget(SailTrim.FURLED, 4f, wind, fill, MAX_BELLY), 0f);
                assertEquals(0f, SailShape.flutterTarget(SailTrim.FURLED, 4f, wind, fill, FLUTTER), 0f);
            }
        }
        SailShape.Look look = settled(SailTrim.FURLED, 4f, 12.0, 0f, 1);
        assertEquals(0f, maxSquare(look, 4f), 0f);
        assertEquals(0f, SailShape.triangle(look, 0.3f, 0.3f, 5f, 7.0, 1f), 0f);
    }

    @Test
    void theLookEasesWithoutJumps() {
        SailShape.Look look = settled(SailTrim.FULL, 4f, 0.0, 0f, 1);
        float target = SailShape.bellyTarget(SailTrim.FULL, 4f, 12.0, 1f, MAX_BELLY);
        float maxStep = 0f;
        float prev = look.belly;
        double t = 0.0;
        for (int k = 0; k < 400; k++) {
            t += 0.37;
            look.approach(target, 0f, -1, 1f, t);
            maxStep = Math.max(maxStep, Math.abs(look.belly - prev));
            prev = look.belly;
            assertTrue(look.side >= -1f && look.side <= 1f);
        }
        assertTrue(maxStep < target * 0.05f, "belly step per frame " + maxStep);
        assertEquals(target, look.belly, 1.0e-3f, "settles");
        assertEquals(-1f, look.side, 0f, "the cloth crossed to the other side");
        // a long pause does not overshoot
        look.approach(0f, 0f, -1, 0f, t + 10_000.0);
        assertTrue(look.belly >= 0f && look.belly < target);
    }

    @Test
    void theClothMovesContinuouslyOverTime() {
        SailShape.Look look = settled(SailTrim.FULL, 6f, 12.0, 0.4f, 1);
        float dt = 0.05f; // a frame at 400 fps
        for (int i = 0; i <= 6; i++) {
            for (int j = 0; j <= 6; j++) {
                float t = j / 6f, s = i / 6f;
                for (int k = 0; k < 400; k++) {
                    double time = k * dt;
                    float a = SailShape.square(look, t, s, false, 6f, time, 1.1f);
                    float b = SailShape.square(look, t, s, false, 6f, time + dt, 1.1f);
                    assertTrue(Math.abs(a - b) < 0.02f, "square jump at " + t + "," + s + " t=" + time);
                    float c = SailShape.triangle(look, t * (1f - s), s * (1f - t), 6f, time, 1.1f);
                    float d = SailShape.triangle(look, t * (1f - s), s * (1f - t), 6f, time + dt, 1.1f);
                    assertTrue(Math.abs(c - d) < 0.02f, "triangle jump");
                }
            }
        }
    }

    @Test
    void theVelocityFollowsThePosition() {
        SailShape.Look look = new SailShape.Look(1, 0);
        double x = 0.0;
        for (int k = 0; k <= 200; k++) {
            look.trackPosition(x, 5.0, k);
            x += 0.2; // 4 blocks per second along x
        }
        assertEquals(4.0, look.velocityX, 0.01);
        assertEquals(0.0, look.velocityZ, 1.0e-6);
        look.trackPosition(500.0, 5.0, 201.0); // a teleport is not a speed
        assertEquals(0.0, look.velocityX, 0.0);
        look.still();
        assertEquals(0.0, look.velocityX, 0.0);
    }

    @Test
    void phasesDifferBetweenSails() {
        assertTrue(Math.abs(SailShape.phase(1L) - SailShape.phase(2L)) > 1.0e-3);
        for (long k = -50; k < 50; k++) {
            float p = SailShape.phase(k * 31L);
            assertTrue(p >= 0f && p <= Math.PI * 2.0 + 1e-6);
        }
    }

    // --- SailAir: whether the sail draws ---

    @Test
    void betaIsTheAngleOffTheBow() {
        // bow +z; wind blowing toward -z comes from ahead
        assertEquals(0.0, SailAir.beta(0, -5, 0, 1, 1), 1e-9);
        assertEquals(180.0, SailAir.beta(0, 5, 0, 1, 1), 1e-9);
        assertEquals(90.0, SailAir.beta(5, 0, 0, 1, 1), 1e-9);
        assertEquals(180.0, SailAir.beta(0, -5, 0, 1, -1), 1e-9, "bow the other way");
        assertEquals(180.0, SailAir.beta(0, -5, 0, 1, 0), 1e-9, "unknown bow: read from astern");
        assertEquals(135.0, SailAir.beta(3, 3, 0, 1, 0), 1e-9);
        assertEquals(180.0, SailAir.beta(0, 0, 0, 1, 1), 0.0, "calm");
    }

    @Test
    void theBowFollowsClearMotion() {
        assertEquals(0, SailAir.bowSign(0, 0.5));
        assertEquals(1, SailAir.bowSign(0, 2.0));
        assertEquals(1, SailAir.bowSign(1, -0.3), "drifting astern a little keeps the bow");
        assertEquals(-1, SailAir.bowSign(1, -1.5));
    }

    @Test
    void aSailDrawsOnlyOutsideTheNoGoZoneAndWithWindAcrossTheCloth() {
        // square sail, wind from astern straight into the cloth
        assertEquals(1f, SailAir.fill(SailTypes.SQUARE_CURVE, 180.0, 1.0), 1e-6);
        // head to wind: no drive, luffs
        assertEquals(0f, SailAir.fill(SailTypes.SQUARE_CURVE, 0.0, 1.0), 1e-6);
        assertEquals(0f, SailAir.fill(SailTypes.FORE_AND_AFT_CURVE, 20.0, 1.0), 1e-6);
        // close-hauled fore-and-aft still draws, a square sail there does not
        assertTrue(SailAir.fill(SailTypes.FORE_AND_AFT_CURVE, 50.0, 0.6) > 0.9f);
        assertTrue(SailAir.fill(SailTypes.SQUARE_CURVE, 40.0, 0.6) < 0.01f);
        // wind running along the cloth: luffs whatever the angle off the bow
        assertEquals(0f, SailAir.fill(SailTypes.FORE_AND_AFT_CURVE, 90.0, 0.05), 1e-6);
        assertEquals(1f, SailAir.fill(SailTypes.FORE_AND_AFT_CURVE, 90.0, SailAir.SQUARE_ON), 1e-6);
        assertEquals(1.0, SailAir.squareness(0, 5, 0, 1), 1e-9);
        assertEquals(0.0, SailAir.squareness(5, 0, 0, 1), 1e-9);
        assertEquals(0.0, SailAir.squareness(0, 0, 0, 1), 0.0);
        // fill is continuous over the angle
        float prev = SailAir.fill(SailTypes.SQUARE_CURVE, 0.0, 1.0);
        for (double b = 0.5; b <= 180.0; b += 0.5) {
            float f = SailAir.fill(SailTypes.SQUARE_CURVE, b, 1.0);
            assertTrue(Math.abs(f - prev) < 0.05f, "jump at " + b);
            prev = f;
        }
    }
}
