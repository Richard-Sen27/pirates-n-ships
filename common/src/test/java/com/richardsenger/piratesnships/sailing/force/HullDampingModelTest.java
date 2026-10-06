package com.richardsenger.piratesnships.sailing.force;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Roll and pitch damping of the hull. */
class HullDampingModelTest {

    private static final HullDampingModel.Params P = new HullDampingModel.Params(true, 1.5, 2.0);
    private static final double BEAM = 5.0;

    private static ShipState spinning(ShipState base, double roll, double pitch, double yaw) {
        Vector3d local = new Vector3d(ShipFrame.FORWARD).mul(roll).add(new Vector3d(ShipFrame.PORT).mul(pitch))
                .add(new Vector3d(ShipFrame.UP).mul(yaw));
        return base.withAngularVelocity(base.toWorld(local, new Vector3d()));
    }

    private static Vector3d localTorque(ForceContribution c) {
        return new Vector3d(c.torque());
    }

    @Test
    void zeroAtRest() {
        ForceContribution c = HullDampingModel.compute(ShipState.atRest(100, 8), BEAM, P);
        assertEquals(0.0, c.torque().length());
        assertEquals(0.0, c.force().length());
        assertEquals(HullDampingModel.SOURCE, c.source());
    }

    @Test
    void zeroOutOfTheWaterAndWhenDisabled() {
        ShipState rolling = spinning(ShipState.atRest(100, 8), 1.0, 1.0, 0.0);
        assertEquals(0.0, HullDampingModel.compute(rolling.withSubmergedFraction(0.0), BEAM, P).torque().length());
        assertEquals(0.0, HullDampingModel.compute(rolling, BEAM, new HullDampingModel.Params(false, 1.5, 2.0)).torque().length());
        assertEquals(0.0, HullDampingModel.compute(rolling, BEAM, new HullDampingModel.Params(true, 0.0, 0.0)).torque().length());
    }

    @Test
    void opposesRollAndPitchAndLeavesYaw() {
        ShipState s = spinning(ShipState.atRest(100, 8), 0.4, -0.3, 0.7);
        Vector3d t = localTorque(HullDampingModel.compute(s, BEAM, P));
        assertTrue(t.dot(ShipFrame.FORWARD) < 0, "roll torque must oppose a positive roll rate: " + t);
        assertTrue(t.dot(ShipFrame.PORT) > 0, "pitch torque must oppose a negative pitch rate: " + t);
        assertEquals(0.0, t.dot(ShipFrame.UP), 1e-12, "yaw is the keel's job");
        // exact formula
        assertEquals(-1.5 * 100 * 25 / 12.0 * 0.4, t.dot(ShipFrame.FORWARD), 1e-9);
        assertEquals(-2.0 * 100 * 64 / 12.0 * -0.3, t.dot(ShipFrame.PORT), 1e-9);
    }

    @Test
    void proportionalToAngularVelocityAndSubmergedFraction() {
        ShipState base = ShipState.atRest(100, 8);
        double t1 = HullDampingModel.compute(spinning(base, 0.2, 0, 0), BEAM, P).torque().length();
        double t2 = HullDampingModel.compute(spinning(base, 0.4, 0, 0), BEAM, P).torque().length();
        double half = HullDampingModel.compute(spinning(base, 0.4, 0, 0).withSubmergedFraction(0.5), BEAM, P).torque().length();
        assertEquals(2.0 * t1, t2, 1e-9);
        assertEquals(0.5 * t2, half, 1e-9);
    }

    @Test
    void usesTheShipFrameOfATurnedAndHeeledShip() {
        Quaterniond q = new Quaterniond().rotateY(Math.toRadians(70)).rotateZ(Math.toRadians(15));
        ShipState s = spinning(ShipState.atRest(100, 8).withOrientation(q), 0.5, 0.0, 0.0);
        // world torque = toWorld(local torque); it must point against the world-frame angular velocity
        Vector3d worldTorque = s.toWorld(HullDampingModel.compute(s, BEAM, P).torque(), new Vector3d());
        Vector3dc w = s.angularVelocity();
        assertEquals(-1.0, worldTorque.normalize(new Vector3d()).dot(w.normalize(new Vector3d())), 1e-9);
    }

    /** Scaling: a hull twice as large (mass ×8, dimensions ×2) decays at the same rate relative to its inertia. */
    @Test
    void scalesWithInertiaSoTheDecayRateIsSizeIndependent() {
        double mSmall = 40, mLarge = 320;
        ShipState small = spinning(ShipState.atRest(mSmall, 5), 0.5, 0.5, 0);
        ShipState large = spinning(ShipState.atRest(mLarge, 10), 0.5, 0.5, 0);
        Vector3d ts = localTorque(HullDampingModel.compute(small, 4, P));
        Vector3d tl = localTorque(HullDampingModel.compute(large, 8, P));
        // inertia of a box scales with m·size²: the ratio torque/inertia (the angular deceleration) must be equal
        double rollDecelSmall = ts.dot(ShipFrame.FORWARD) / (mSmall * 16 / 12.0);
        double rollDecelLarge = tl.dot(ShipFrame.FORWARD) / (mLarge * 64 / 12.0);
        assertEquals(rollDecelSmall, rollDecelLarge, 1e-9);
        double pitchDecelSmall = ts.dot(ShipFrame.PORT) / (mSmall * 25 / 12.0);
        double pitchDecelLarge = tl.dot(ShipFrame.PORT) / (mLarge * 100 / 12.0);
        assertEquals(pitchDecelSmall, pitchDecelLarge, 1e-9);
        // and that deceleration is c · ω for a slab: the coefficient is a decay rate
        assertEquals(-1.5 * 0.5, rollDecelSmall, 1e-9);
    }

    @Test
    void neverAddsEnergy() {
        Random r = new Random(42);
        for (int i = 0; i < 2000; i++) {
            Quaterniond q = new Quaterniond().rotateXYZ(r.nextGaussian(), r.nextDouble() * 6.3, r.nextGaussian());
            ShipState s = spinning(ShipState.atRest(1 + r.nextDouble() * 500, r.nextDouble() * 30).withOrientation(q)
                    .withSubmergedFraction(r.nextDouble()), r.nextGaussian(), r.nextGaussian(), r.nextGaussian());
            HullDampingModel.Params p = new HullDampingModel.Params(true, r.nextDouble() * 10, r.nextDouble() * 10);
            Vector3d torqueWorld = s.toWorld(HullDampingModel.compute(s, r.nextDouble() * 20, p).torque(), new Vector3d());
            assertTrue(torqueWorld.dot(s.angularVelocity()) <= 1e-12, "damping added energy at sample " + i);
        }
    }

    /**
     * A simple roll oscillator (slab inertia, linear righting moment) integrated with explicit steps like Sable's
     * substeps: the damped amplitude falls, the undamped one does not, and the damped one never grows.
     */
    @Test
    void dampsASimulatedRollOscillator() {
        double mass = 50, beam = 5, inertia = mass * beam * beam / 12.0, stiffness = inertia * 4.0; // ω0 = 2 rad/s
        double dt = 0.025;
        double[] undamped = simulate(new HullDampingModel.Params(true, 0, 0), mass, beam, inertia, stiffness, dt);
        double[] damped = simulate(new HullDampingModel.Params(true, 1.5, 1.5), mass, beam, inertia, stiffness, dt);
        assertTrue(undamped[1] > 0.9 * undamped[0], "undamped roll lost amplitude: " + undamped[1]);
        assertTrue(damped[1] < 0.15 * damped[0], "damped roll still large after 3 s (e^-0.75t = 0.11): " + damped[1]);
    }

    /** Returns {initial amplitude, max |angle| over the last second of 4 s}. */
    private static double[] simulate(HullDampingModel.Params p, double mass, double beam, double inertia, double stiffness, double dt) {
        double angle = 0.0, rate = 1.0, maxLate = 0.0;
        for (int i = 0; i < (int) (4.0 / dt); i++) {
            ShipState s = spinning(ShipState.atRest(mass, 8), rate, 0, 0);
            double tau = HullDampingModel.compute(s, beam, p).torque().dot(ShipFrame.FORWARD) - stiffness * angle;
            rate += tau / inertia * dt;
            angle += rate * dt;
            if (i * dt >= 3.0) maxLate = Math.max(maxLate, Math.abs(angle));
        }
        return new double[] {1.0 / Math.sqrt(stiffness / inertia), maxLate};
    }
}
