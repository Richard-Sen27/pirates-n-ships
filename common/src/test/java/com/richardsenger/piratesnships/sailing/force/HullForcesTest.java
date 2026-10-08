package com.richardsenger.piratesnships.sailing.force;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Rudder and keel. */
class HullForcesTest {

    private static final SailingParams P = SailingParams.DEFAULTS;
    private static final ShipState REST = ShipState.atRest(200, 20);
    private static final Vector3dc STERN = new Vector3d(0, -1, -10);

    private static ShipState moving(double fwd, double side) {
        return REST.withLinearVelocity(new Vector3d(ShipFrame.FORWARD).mul(fwd).add(new Vector3d(ShipFrame.PORT).mul(side)));
    }

    /** Direction the bow starts to swing for a given torque (isotropic inertia): + = starboard. */
    private static double bowSwing(Vector3dc torque) {
        return torque.cross(ShipFrame.FORWARD, new Vector3d()).dot(ShipFrame.STARBOARD);
    }

    @Test
    void rudderDoesNothingAtStandstill() {
        ForceContribution c = RudderModel.compute(30, STERN, REST, P);
        assertEquals(0.0, c.force().length());
        assertEquals(0.0, c.torque().length());
    }

    @Test
    void rudderTurnsTheWayItIsPutAndReversesAstern() {
        assertTrue(bowSwing(RudderModel.compute(20, STERN, moving(5, 0), P).torque()) > 0, "positive angle turns to starboard");
        assertTrue(bowSwing(RudderModel.compute(-20, STERN, moving(5, 0), P).torque()) < 0, "negative angle turns to port");
        assertTrue(bowSwing(RudderModel.compute(20, STERN, moving(-2, 0), P).torque()) < 0, "reversed when moving astern");
    }

    @Test
    void rudderScalesWithSpeedAndStrengthAndIsClamped() {
        double slow = RudderModel.compute(20, STERN, moving(2, 0), P).torque().y();
        double fast = RudderModel.compute(20, STERN, moving(6, 0), P).torque().y();
        assertEquals(3.0, fast / slow, 1e-9);
        double max = RudderModel.compute(P.maxRudderAngleDeg(), STERN, moving(4, 0), P).torque().y();
        assertEquals(max, RudderModel.compute(80, STERN, moving(4, 0), P).torque().y(), 1e-9);
        ShipState dry = moving(4, 0).withSubmergedFraction(0);
        assertEquals(0.0, RudderModel.compute(20, STERN, dry, P).force().length());
        // Only the forward speed counts, not drift.
        assertEquals(RudderModel.compute(20, STERN, moving(4, 0), P).force().length(),
                RudderModel.compute(20, STERN, moving(4, 3), P).force().length(), 1e-9);
    }

    private static SailingParams withFactor(double factor) {
        return new SailingParams(P.sailForceScale(), P.halfTrimFactor(), P.rudderStrength(), factor, P.maxRudderAngleDeg(),
                P.keelEnabled(), P.keelLongitudinalDrag(), P.keelLateralDrag(), P.keelYawDragFactor());
    }

    /** SH2: the turning authority multiplies the rudder force and its torque, nothing else; 0 disables the rudder. */
    @Test
    void rudderForceFactorScalesTheRudderLinearly() {
        ShipState s = moving(3, 0);
        ForceContribution one = RudderModel.compute(-25, STERN, s, withFactor(1.0));
        ForceContribution three = RudderModel.compute(-25, STERN, s, withFactor(3.0));
        assertEquals(3.0, three.force().length() / one.force().length(), 1e-9);
        assertEquals(3.0, three.torque().y() / one.torque().y(), 1e-9);
        assertEquals(0.0, RudderModel.compute(-25, STERN, s, withFactor(0.0)).force().length());
        assertEquals(0.0, RudderModel.compute(-25, STERN, REST, withFactor(3.0)).force().length(), "still no force without way");
        assertEquals(0.0, withFactor(-2.0).rudderForceFactor(), "negative factors clamp to 0");
        // factor 1 is the rudder of milestone 3: F = rudderStrength · mass · submerged · v_fwd · sin δ
        double old = P.rudderStrength() * s.mass() * s.submergedFraction() * 3.0 * Math.sin(Math.toRadians(25));
        assertEquals(old, one.force().length(), 1e-9);
        // the factor is not the keel's business
        assertEquals(KeelModel.compute(moving(2, 2), withFactor(1.0)).force().length(),
                KeelModel.compute(moving(2, 2), withFactor(5.0)).force().length(), 1e-12);
    }

    /**
     * SH2: the force grows linearly with the forward speed (not with its square), so the steady turning circle
     * (radius = v / ω with ω ∝ v against the keel's yaw damping) does not depend on the speed: a slow ship answers the
     * helm with the same circle, only more slowly. No minimum steerage term is needed.
     */
    @Test
    void steadyTurningCircleDoesNotDependOnSpeed() {
        double inertia = REST.mass() * 20 * 20 / 12.0;
        double[] radius = new double[2];
        double[] speeds = {1.0, 5.0};
        for (int k = 0; k < 2; k++) {
            ShipState s = moving(speeds[k], 0);
            double w = 0;
            for (int i = 0; i < 20 * 60; i++) {
                ShipState now = s.withAngularVelocity(new Vector3d(0, w, 0));
                double tau = RudderModel.compute(P.maxRudderAngleDeg(), STERN, now, P).torque().y()
                        + KeelModel.compute(now.withLinearVelocity(new Vector3d()), P).torque().y();
                w += tau / inertia * 0.05;
            }
            radius[k] = speeds[k] / Math.abs(w);
        }
        assertEquals(radius[0], radius[1], radius[1] * 1e-3);
    }

    @Test
    void keelResistsSidewaysMuchMoreThanForward() {
        ForceContribution fwd = KeelModel.compute(moving(3, 0), P);
        ForceContribution side = KeelModel.compute(moving(0, 3), P);
        assertTrue(fwd.force().dot(ShipFrame.FORWARD) < 0);
        assertTrue(side.force().dot(ShipFrame.PORT) < 0);
        double ratio = side.force().length() / fwd.force().length();
        assertEquals(P.keelLateralDrag() / P.keelLongitudinalDrag(), ratio, 1e-9);
        assertTrue(ratio >= 10);
    }

    @Test
    void keelAlwaysOpposesMotion() {
        for (int i = 0; i < 360; i += 10) {
            double r = Math.toRadians(i);
            ShipState s = moving(4 * Math.cos(r), 4 * Math.sin(r)).withAngularVelocity(new Vector3d(0.1, 0.3 * Math.sin(r), 0));
            ForceContribution c = KeelModel.compute(s, P);
            Vector3d vLocal = s.toLocal(s.velocityAt(s.keelCenter(), new Vector3d()), new Vector3d());
            assertTrue(c.force().dot(vLocal) <= 1e-9, "keel must never push along the motion");
            double yaw = s.toLocal(s.angularVelocity(), new Vector3d()).y;
            assertTrue(c.torque().y() * yaw <= 1e-9 || Math.abs(yaw) < 1e-12, "keel must damp yaw");
            assertTrue(c.isFinite());
        }
    }

    @Test
    void keelScalesWithImmersionAndCanBeDisabled() {
        ForceContribution full = KeelModel.compute(moving(2, 2), P);
        ForceContribution half = KeelModel.compute(moving(2, 2).withSubmergedFraction(0.5), P);
        assertEquals(0.5, half.force().length() / full.force().length(), 1e-9);
        assertEquals(0.0, KeelModel.compute(moving(2, 2).withSubmergedFraction(0), P).force().length());
        assertEquals(0.0, KeelModel.compute(moving(2, 2), P.withKeelEnabled(false)).force().length());
    }

    @Test
    void keelWorksInTheShipFrameWhateverTheHeading() {
        Quaterniond q = new Quaterniond().rotateY(Math.toRadians(-130));
        ShipState turned = REST.withOrientation(q).withLinearVelocity(q.transform(new Vector3d(ShipFrame.PORT).mul(3)));
        ForceContribution a = KeelModel.compute(moving(0, 3), P);
        ForceContribution b = KeelModel.compute(turned, P);
        assertTrue(a.force().distance(b.force()) < 1e-9);
    }

    @Test
    void steadyTurnRateIsReasonable() {
        // Integrate only yaw: inertia m L^2 / 12, rudder vs keel yaw damping, constant forward speed.
        ShipState s = moving(5, 0);
        double inertia = s.mass() * 20 * 20 / 12.0;
        double w = 0;
        for (int i = 0; i < 20 * 30; i++) {
            ShipState now = s.withAngularVelocity(new Vector3d(0, w, 0));
            double tau = RudderModel.compute(P.maxRudderAngleDeg(), STERN, now, P).torque().y()
                    + KeelModel.compute(now.withLinearVelocity(new Vector3d()), P).torque().y();
            w += tau / inertia * 0.05;
        }
        double degPerSecond = Math.toDegrees(Math.abs(w));
        assertTrue(degPerSecond > 3 && degPerSecond < 30, "turn rate " + degPerSecond + " deg/s");
    }
}
