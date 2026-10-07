package com.richardsenger.piratesnships.sailing.waves;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.sailing.ship.BowFrame;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class WaveTorqueRuleTest {

    private static final WaveTorqueRule.Params P = new WaveTorqueRule.Params(true, 6.0, 2.0);

    @Test
    void sizeScaleIsOneAtTwoHundredBlocks() {
        assertEquals(1.0, WaveTorqueRule.sizeScale(200), 1e-12);
        assertEquals(0.5, WaveTorqueRule.sizeScale(800), 1e-12);
        assertEquals(2.0, WaveTorqueRule.sizeScale(50), 1e-12);
        assertEquals(Math.sqrt(200), WaveTorqueRule.sizeScale(0), 1e-9);
    }

    @Test
    void torqueFollowsTheSlopeWithTheSignConventions() {
        // port side higher: roll lifts port (+); bow higher: pitch lifts the bow (negative about port)
        WaveTorqueRule.Torque t = WaveTorqueRule.torque(0.05, 0.02, 100.0, 200, P);
        assertEquals(6.0 * 100 * 0.05, t.roll(), 1e-9);
        assertEquals(-6.0 * 100 * 0.02, t.pitch(), 1e-9);
        Vector3d v = t.toShipVector(new Vector3d());
        assertEquals(t.pitch(), v.x, 1e-12);
        assertEquals(t.roll(), v.z, 1e-12);
        assertEquals(0.0, v.y);
    }

    @Test
    void biggerShipsGetLessTorquePerMass() {
        double small = WaveTorqueRule.torque(0.05, 0, 100.0, 200, P).roll() / 100.0;
        double big = WaveTorqueRule.torque(0.05, 0, 1000.0, 1800, P).roll() / 1000.0;
        assertEquals(small / 3.0, big, 1e-9);
    }

    @Test
    void torqueIsCappedPerMass() {
        WaveTorqueRule.Torque t = WaveTorqueRule.torque(0.3, 0.4, 50.0, 30, P);
        assertEquals(2.0 * 50.0, t.magnitude(), 1e-9);
        assertEquals(0.3 / 0.4, t.roll() / -t.pitch(), 1e-9); // direction kept
    }

    @Test
    void disabledOrMasslessGivesNothing() {
        assertEquals(WaveTorqueRule.Torque.ZERO, WaveTorqueRule.torque(0.1, 0.1, 100, 200, new WaveTorqueRule.Params(false, 6, 2)));
        assertEquals(WaveTorqueRule.Torque.ZERO, WaveTorqueRule.torque(0.1, 0.1, 0, 200, P));
        assertEquals(WaveTorqueRule.Torque.ZERO, WaveTorqueRule.torque(Double.NaN, 0.1, 100, 200, P));
        assertEquals(0.0, WaveTorqueRule.torque(0.1, 0.1, 100, 200, new WaveTorqueRule.Params(true, 0, 2)).magnitude());
    }

    @Test
    void slopeAcrossASpan() {
        assertEquals(0.1, WaveTorqueRule.slope(0.6, -0.1, 7.0), 1e-12);
        assertEquals(0.0, WaveTorqueRule.slope(0.6, -0.1, 0.0));
    }

    @Test
    void samplePointsSitOnTheFacesOfTheBox() {
        int[] box = {10, 5, 20, 16, 8, 36}; // 7 wide in x, 17 long in z
        Vector3d[] p = WaveTorqueRule.samplePoints(BowFrame.SOUTH, box, 6.0); // bow +Z, port +X
        assertEquals(new Vector3d(13.5, 6, 37), p[0]);
        assertEquals(new Vector3d(13.5, 6, 20), p[1]);
        assertEquals(new Vector3d(17, 6, 28.5), p[2]);
        assertEquals(new Vector3d(10, 6, 28.5), p[3]);
        Vector3d[] e = WaveTorqueRule.samplePoints(BowFrame.byName("east"), box, 6.0); // bow +X, port -Z
        assertEquals(new Vector3d(17, 6, 28.5), e[0]);
        assertEquals(new Vector3d(10, 6, 28.5), e[1]);
        assertEquals(new Vector3d(13.5, 6, 20), e[2]);
        assertEquals(new Vector3d(13.5, 6, 37), e[3]);
        assertTrue(WaveTorqueRule.center(box, 6).equals(new Vector3d(13.5, 6, 28.5)));
    }
}
