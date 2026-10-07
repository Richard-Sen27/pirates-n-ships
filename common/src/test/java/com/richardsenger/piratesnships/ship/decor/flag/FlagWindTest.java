package com.richardsenger.piratesnships.ship.decor.flag;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

/** The flag's exact downwind angle and the world-to-ship-frame turn of the wind (§4.7, §5.1, FL1). */
class FlagWindTest {

    private static final double EPS = 1.0e-9;
    private static final float DEG = 1.0e-3f;

    private static Quaterniond yaw(double degrees) {
        return new Quaterniond().rotateAxis(Math.toRadians(degrees), 0, 1, 0);
    }

    @Test
    void bearingUsesTheCompassOfTheWindSample() {
        assertEquals(0f, FlagWind.bearing(0, -1, 77f), DEG);
        assertEquals(90f, FlagWind.bearing(1, 0, 77f), DEG);
        assertEquals(180f, FlagWind.bearing(0, 1, 77f), DEG);
        assertEquals(270f, FlagWind.bearing(-1, 0, 77f), DEG);
        // Not snapped: anything in between comes out as it is.
        assertEquals(45f, FlagWind.bearing(1, -1, 0f), DEG);
        assertEquals(30f, FlagWind.bearing(Math.sin(Math.toRadians(30)), -Math.cos(Math.toRadians(30)), 0f), DEG);
        assertEquals(213.5f, FlagWind.bearing(FlagWind.toward(213.5)[0], FlagWind.toward(213.5)[1], 0f), DEG);
    }

    @Test
    void towardIsTheInverseOfBearing() {
        for (int deg = 0; deg < 360; deg += 15) {
            double[] v = FlagWind.toward(deg);
            assertEquals(1.0, Math.hypot(v[0], v[1]), EPS);
            assertEquals(deg, FlagWind.bearing(v[0], v[1], -1f), DEG);
        }
    }

    @Test
    void calmKeepsTheLastAngle() {
        assertEquals(123f, FlagWind.bearing(0, 0, 123f));
        assertEquals(123f, FlagWind.downwindAngle(0, 0, null, 123f));
        assertEquals(123f, FlagWind.downwindAngle(1.0e-9, 0, yaw(90), 123f));
        assertEquals(Float.NaN, FlagWind.bearing(0, 0, Float.NaN), "no angle yet stays unknown");
    }

    @Test
    void onLandTheAngleIsTheWorldDownwind() {
        assertEquals(180f, FlagWind.downwindAngle(0, 8, null, 0f), DEG);
        double[] v = FlagWind.toward(225);
        assertEquals(225f, FlagWind.downwindAngle(3 * v[0], 3 * v[1], null, 0f), DEG);
    }

    @Test
    void identityOrientationLeavesTheWindUnchanged() {
        assertArrayEquals(new double[]{0.3, -0.8}, FlagWind.shipFrame(0.3, -0.8, new Quaterniond()), EPS);
        for (int deg = 0; deg < 360; deg += 30) {
            double[] v = FlagWind.toward(deg);
            assertEquals(deg, FlagWind.downwindAngle(v[0], v[1], new Quaterniond(), -1f), DEG);
        }
    }

    @Test
    void quarterTurnMapsTheWindIntoThePlotFrame() {
        // A +90 degree yaw turns plot west (-X) to world south (+Z): a wind blowing south blows toward plot west.
        assertArrayEquals(new double[]{-1, 0}, FlagWind.shipFrame(0, 1, yaw(90)), EPS);
        assertEquals(270f, FlagWind.downwindAngle(0, 1, yaw(90), 0f), DEG);
        assertEquals(180f, FlagWind.downwindAngle(1, 0, yaw(90), 0f), DEG);
        assertEquals(90f, FlagWind.downwindAngle(0, 1, yaw(-90), 0f), DEG);
    }

    @Test
    void halfTurnReversesTheWind() {
        assertArrayEquals(new double[]{-0.3, 0.8}, FlagWind.shipFrame(0.3, -0.8, yaw(180)), EPS);
        for (int deg = 0; deg < 360; deg += 30) {
            double[] v = FlagWind.toward(deg);
            assertEquals(0f, FlagYaw.delta(deg + 180f, FlagWind.downwindAngle(v[0], v[1], yaw(180), -1f)), DEG);
        }
    }

    @Test
    void anyTurnShiftsTheAngleByExactlyTheTurnWithoutSnapping() {
        // A +theta yaw turns the ship's bow (plot north) toward world -theta, so the plot angle is world + theta.
        float at45 = FlagWind.downwindAngle(0, 1, yaw(45), 0f);
        assertEquals(225f, at45, DEG);
        assertNotEquals(0f, at45 % 90f, "not a four-way facing");
        for (int turn = -170; turn <= 180; turn += 17) {
            for (int deg = 5; deg < 360; deg += 40) {
                double[] v = FlagWind.toward(deg);
                assertEquals(0f, FlagYaw.delta(deg + turn, FlagWind.downwindAngle(v[0], v[1], yaw(turn), -1f)), 1.0e-2f,
                        "wind " + deg + ", turn " + turn);
            }
        }
    }

    @Test
    void aHeeledShipKeepsTheHorizontalHeading() {
        Quaterniond q = yaw(37).rotateAxis(Math.toRadians(8), 0, 0, 1); // turned and heeled
        double[] local = FlagWind.shipFrame(0.6, 0.8, q);
        org.joml.Vector3d back = q.transform(new org.joml.Vector3d(local[0], 0, local[1]));
        // Heel drops a little of the wind into the plot's vertical; the horizontal heading survives.
        assertEquals(Math.atan2(0.8, 0.6), Math.atan2(back.z, back.x), 0.02);
        float world = FlagWind.bearing(0.6, 0.8, 0f);
        assertEquals(0f, FlagYaw.delta(world + 37f, FlagWind.downwindAngle(0.6, 0.8, q, 0f)), 1.5f);
        // Heeled 25 degrees about the bow axis with the wind abeam: still within a few degrees.
        Quaterniond heel = yaw(0).rotateAxis(Math.toRadians(25), 0, 0, 1);
        assertEquals(0f, FlagYaw.delta(90f, FlagWind.downwindAngle(1, 0, heel, 0f)), 1.0e-2f);
    }

    @Test
    void aShipOnItsSideWithTheWindThroughItsDeckKeepsTheLastAngle() {
        // Rolled 90 degrees about the bow (plot Z) axis: a wind along world X points straight along the plot's Y.
        Quaterniond onItsSide = new Quaterniond().rotateAxis(Math.toRadians(90), 0, 0, 1);
        assertEquals(42f, FlagWind.downwindAngle(5, 0, onItsSide, 42f));
    }

    @Test
    void shipFrameDoesNotMutateTheOrientation() {
        Quaterniond q = yaw(90);
        Quaterniond copy = new Quaterniond(q);
        FlagWind.shipFrame(1, 0, q);
        FlagWind.downwindAngle(1, 0, q, 0f);
        assertEquals(copy, q);
    }
}
