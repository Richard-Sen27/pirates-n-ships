package com.richardsenger.piratesnships.sailing.force;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The SH1 righting torque and heel cap. */
class RightingModelTest {

    private static final RightingModel.Params P = new RightingModel.Params(true, 0.5, 1.0, 30.0, 4.0, 0.7, 3.0, 25.0);
    /** The starter sloop's size: 9 wide, 29 long, 21 high, 3 deep. */
    private static final RightingModel.Hull SLOOP = new RightingModel.Hull(9, 29, 21, 3);
    private static final double G = 11.0;
    private static final double MASS = 320.0;

    /** A ship at rest rotated by {@code roll} about the bow axis and {@code pitch} about the port axis [degrees]. */
    private static ShipState tilted(double roll, double pitch) {
        Quaterniond q = new Quaterniond().rotateAxis(Math.toRadians(roll), 0, 0, 1).rotateAxis(Math.toRadians(pitch), 1, 0, 0);
        return ShipState.atRest(MASS, 29).withOrientation(q);
    }

    private static double rollTorque(ShipState s, RightingModel.Params p) {
        return RightingModel.compute(s, SLOOP, G, p).torque().dot(ShipFrame.FORWARD);
    }

    private static double pitchTorque(ShipState s, RightingModel.Params p) {
        return RightingModel.compute(s, SLOOP, G, p).torque().dot(ShipFrame.PORT);
    }

    @Test
    void zeroAtZeroHeelAndAtRest() {
        ForceContribution c = RightingModel.compute(tilted(0, 0), SLOOP, G, P);
        assertEquals(0.0, c.torque().length(), 1e-9);
        assertEquals(0.0, c.force().length());
        assertEquals(RightingModel.SOURCE, c.source());
    }

    @Test
    void zeroWhenDisabledOrOutOfTheWater() {
        ShipState heeled = tilted(10, 5);
        RightingModel.Params off = new RightingModel.Params(false, 0.5, 1.0, 30, 4, 0.7, 3, 25);
        assertEquals(0.0, RightingModel.compute(heeled, SLOOP, G, off).torque().length());
        assertEquals(0.0, RightingModel.compute(heeled.withSubmergedFraction(0.0), SLOOP, G, P).torque().length());
        assertEquals(0.0, RightingModel.compute(heeled, SLOOP, 0.0, P).torque().length());
    }

    @Test
    void anglesHaveTheShipFrameSigns() {
        assertEquals(10.0, Math.toDegrees(RightingModel.roll(tilted(10, 0))), 1e-9, "+ roll about the bow axis = port side up");
        assertEquals(-7.0, Math.toDegrees(RightingModel.roll(tilted(-7, 0))), 1e-9);
        assertEquals(6.0, Math.toDegrees(RightingModel.pitch(tilted(0, 6))), 1e-9, "+ pitch about the port axis = bow down");
        // + rotation about the port axis really puts the bow down
        Vector3d bow = tilted(0, 6).toWorld(new Vector3d(ShipFrame.FORWARD), new Vector3d());
        assertTrue(bow.y < 0, "bow " + bow);
        Vector3d port = tilted(10, 0).toWorld(new Vector3d(ShipFrame.PORT), new Vector3d());
        assertTrue(port.y > 0, "port " + port);
    }

    @Test
    void opposesTheHeelOnBothAxes() {
        assertTrue(rollTorque(tilted(8, 0), P) < 0, "port side up: the torque must roll it back");
        assertTrue(rollTorque(tilted(-8, 0), P) > 0, "starboard side up: the torque must roll it back");
        assertTrue(pitchTorque(tilted(0, 4), P) < 0, "bow down: the torque must lift the bow");
        assertTrue(pitchTorque(tilted(0, -4), P) > 0, "bow up: the torque must push the bow down");
        assertEquals(0.0, RightingModel.compute(tilted(8, 4), SLOOP, G, P).torque().dot(ShipFrame.UP), 1e-9, "no yaw");
    }

    @Test
    void exactFormulaAndLinearUpToTheLimit() {
        double gm = 81.0 / (12.0 * 3.0) * 0.5; // 1.125 blocks
        assertEquals(gm, RightingModel.metacentricHeight(9, 3, P), 1e-12);
        double k = MASS * G * gm;
        assertEquals(-k * Math.toRadians(5), rollTorque(tilted(5, 0), P), 1e-6);
        double t5 = rollTorque(tilted(5, 0), P), t10 = rollTorque(tilted(10, 0), P), t20 = rollTorque(tilted(20, 0), P);
        assertEquals(2.0, t10 / t5, 1e-9);
        assertEquals(4.0, t20 / t5, 1e-9);
        // constant beyond max_righting_degrees (30)
        assertEquals(rollTorque(tilted(30, 0), P), rollTorque(tilted(50, 0), P), 1e-6);
        assertEquals(rollTorque(tilted(30, 0), P), rollTorque(tilted(120, 0), P), 1e-6);
    }

    @Test
    void metacentricHeightIsClampedAndScales() {
        assertEquals(4.0, RightingModel.metacentricHeight(29, 3, P), 1e-12, "the GM of a long hull is clamped");
        assertEquals(4.0, RightingModel.pitchMetacentricHeight(29, 3, P), 1e-12, "the trim GM of a long hull is clamped");
        RightingModel.Params half = new RightingModel.Params(true, 0.5, 0.1, 30, 4, 0.7, 3, 25);
        assertEquals(841.0 / 36.0 * 0.05, RightingModel.pitchMetacentricHeight(29, 3, half), 1e-12, "pitch factor scales the trim GM");
        assertEquals(81.0 / (12 * RightingModel.MIN_DRAFT) * 0.5 > 4 ? 4.0 : 0, RightingModel.metacentricHeight(9, 0.0, P), 1e-12,
                "a ship lifting out of the water uses the smallest draft and the clamp");
        assertEquals(0.0, RightingModel.metacentricHeight(9, 3, new RightingModel.Params(true, 0, 1.0, 30, 4, 0.7, 3, 25)));
        assertEquals(2 * RightingModel.metacentricHeight(5, 2, P),
                RightingModel.metacentricHeight(5, 2, new RightingModel.Params(true, 1.0, 1.0, 30, 4, 0.7, 3, 25)), 1e-12);
    }

    @Test
    void pitchIsOffByDefault() {
        assertEquals(0.0, RightingModel.Params.DEFAULTS.pitchRightingFactor());
        double pitch = RightingModel.compute(tilted(0, 6), SLOOP, G, RightingModel.Params.DEFAULTS).torque().dot(ShipFrame.PORT);
        assertEquals(0.0, pitch, 1e-9);
        assertTrue(RightingModel.compute(tilted(6, 0), SLOOP, G, RightingModel.Params.DEFAULTS).torque().dot(ShipFrame.FORWARD) < 0);
    }

    @Test
    void dampingOpposesTheRollRateOnly() {
        ShipState upright = tilted(0, 0);
        ShipState rolling = upright.withAngularVelocity(upright.toWorld(new Vector3d(ShipFrame.FORWARD).mul(0.3), new Vector3d()));
        double t = rollTorque(rolling, P);
        assertTrue(t < 0, "damping must oppose a positive roll rate: " + t);
        double k = MASS * G * RightingModel.metacentricHeight(9, 3, P);
        double inertia = MASS * (81 + 441) / 12.0;
        assertEquals(-2 * 0.7 * Math.sqrt(k * inertia) * 0.3, t, 1e-6);
        RightingModel.Params undamped = new RightingModel.Params(true, 0.5, 1.0, 30, 4, 0.0, 3, 25);
        assertEquals(0.0, rollTorque(rolling, undamped), 1e-9);
        // never adds energy: torque · rate <= 0 when upright
        ShipState yawing = upright.withAngularVelocity(new Vector3d(0, 0.5, 0));
        assertEquals(0.0, RightingModel.compute(yawing, SLOOP, G, P).torque().length(), 1e-9, "yaw is not damped here");
    }

    @Test
    void criticallyDampedReturnDoesNotOvershoot() {
        // integrate one axis: I φ'' = τ(φ, φ') with ζ = 1 from 10 degrees; it must come back without crossing zero
        RightingModel.Params critical = new RightingModel.Params(true, 0.5, 1.0, 30, 4, 1.0, 3, 25);
        double gm = RightingModel.metacentricHeight(9, 3, critical);
        double inertia = MASS * (81 + 441) / 12.0;
        double phi = Math.toRadians(10), rate = 0, dt = 1.0 / 40;
        double min = phi;
        for (int i = 0; i < 40 * 20; i++) {
            double tau = RightingModel.axisTorque(phi, rate, MASS, G, gm, inertia, critical);
            rate += tau / inertia * dt;
            phi += rate * dt;
            min = Math.min(min, phi);
        }
        assertTrue(min > -Math.toRadians(0.1), "overshoot to " + Math.toDegrees(min));
        assertTrue(Math.abs(phi) < Math.toRadians(0.1), "not back upright after 20 s: " + Math.toDegrees(phi));
    }

    @Test
    void heelCapLimitsPerMass() {
        double cap = 3.0 * MASS;
        assertEquals(cap, RightingModel.limitHeel(5000, 0.0, MASS, P), 1e-9);
        assertEquals(-cap, RightingModel.limitHeel(-5000, 0.0, MASS, P), 1e-9);
        assertEquals(500, RightingModel.limitHeel(500, Math.toRadians(3), MASS, P), 1e-9, "below the cap and the fade: unchanged");
        RightingModel.Params off = new RightingModel.Params(false, 0.5, 1.0, 30, 4, 0.7, 3, 25);
        assertEquals(5000, RightingModel.limitHeel(5000, Math.toRadians(40), MASS, off), "disabled: unchanged");
    }

    @Test
    void heelingMomentFadesTowardMaxHeelButRightingMomentDoesNot() {
        double rad = Math.toRadians(1);
        // heeling further over (same sign as the angle): full until 15 degrees, half at 20, none from 25
        assertEquals(500, RightingModel.limitHeel(500, 15 * rad, MASS, P), 1e-9);
        assertEquals(250, RightingModel.limitHeel(500, 20 * rad, MASS, P), 1e-9);
        assertEquals(0, RightingModel.limitHeel(500, 25 * rad, MASS, P), 1e-9);
        assertEquals(0, RightingModel.limitHeel(-500, -40 * rad, MASS, P), 1e-9);
        // a moment back toward upright is never faded
        assertEquals(-500, RightingModel.limitHeel(-500, 40 * rad, MASS, P), 1e-9);
        assertEquals(500, RightingModel.limitHeel(500, -22 * rad, MASS, P), 1e-9);
    }
}
