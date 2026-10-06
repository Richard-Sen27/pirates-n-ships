package com.richardsenger.piratesnships.sailing.force;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SailForceModelTest {

    private static final SailingParams P = SailingParams.DEFAULTS;
    private static final ShipState REST = ShipState.atRest(100, 20);
    private static final Vector3dc MAST = new Vector3d(0, 3, 0);

    static ForceContribution force(SailType type, SailTrim trim, double windAngle, double windSpeed, ShipState ship) {
        return SailForceModel.compute(new SailInstance(type, trim, MAST), SailPolar.windFromAngle(windAngle, windSpeed), ship, P, "s");
    }

    static double drive(ForceContribution c) {
        return c.force().dot(ShipFrame.FORWARD);
    }

    @Test
    void furledSailGivesNothing() {
        for (SailType t : SailTypes.ALL) {
            for (double a = -180; a <= 180; a += 5) {
                ForceContribution c = force(t, SailTrim.FURLED, a, 10, REST);
                assertEquals(0.0, c.force().length());
                assertEquals(0.0, c.torque().length());
            }
        }
    }

    @Test
    void forceGrowsWithAreaTrimAndWind() {
        double small = drive(force(SailTypes.SMALL_SQUARE, SailTrim.FULL, 150, 10, REST));
        double large = drive(force(SailTypes.LARGE_SQUARE, SailTrim.FULL, 150, 10, REST));
        assertEquals(25.0 / 9.0, large / small, 1e-9);
        double half = drive(force(SailTypes.LARGE_SQUARE, SailTrim.HALF, 150, 10, REST));
        assertEquals(P.halfTrimFactor(), half / large, 1e-9);
        double weak = drive(force(SailTypes.LARGE_SQUARE, SailTrim.FULL, 150, 5, REST));
        assertEquals(0.5, weak / large, 1e-9);
        double scaled = drive(SailForceModel.compute(new SailInstance(SailTypes.LARGE_SQUARE, SailTrim.FULL, MAST),
                SailPolar.windFromAngle(150, 10), REST, P.withSailForceScale(2.0), "s"));
        assertEquals(2.0, scaled / large, 1e-9);
    }

    @Test
    void squareSailIsBestDownwindAndUselessCloseToTheWind() {
        List<SailPolar.Point> polar = SailPolar.curve(SailTypes.LARGE_SQUARE, 1);
        SailPolar.Point best = polar.stream().max((a, b) -> Double.compare(a.drive(), b.drive())).orElseThrow();
        assertEquals(180.0, best.windAngleDeg(), 1e-9);
        for (SailPolar.Point p : polar) {
            if (p.windAngleDeg() <= 45) {
                assertTrue(p.drive() <= 0.0, "square sail drives at " + p.windAngleDeg());
            }
        }
        assertTrue(SailPolar.at(SailTypes.LARGE_SQUARE, 60).drive() < 0.1 * best.drive());
    }

    @Test
    void foreAndAftSailIsBestOnABeamReachAndDrivesAt45() {
        List<SailPolar.Point> polar = SailPolar.curve(SailTypes.FORE_AND_AFT, 1);
        SailPolar.Point best = polar.stream().max((a, b) -> Double.compare(a.drive(), b.drive())).orElseThrow();
        assertTrue(best.windAngleDeg() >= 75 && best.windAngleDeg() <= 105, "best at " + best.windAngleDeg());
        double at45 = SailPolar.at(SailTypes.FORE_AND_AFT, 45).drive();
        assertTrue(at45 > 0.4 * best.drive(), "fore-and-aft should still drive at 45 degrees, got " + at45);
        assertTrue(at45 > SailPolar.at(SailTypes.LARGE_SQUARE, 45).drive() + 0.3, "fore-and-aft points higher than square");
        assertTrue(SailPolar.at(SailTypes.FORE_AND_AFT, 180).drive() < SailPolar.at(SailTypes.LARGE_SQUARE, 180).drive(),
                "square beats fore-and-aft per area downwind");
    }

    @Test
    void noForwardForceInsideTheNoGoZone() {
        for (SailType t : SailTypes.ALL) {
            for (double a = 0; a <= SailTypes.NO_GO_DEGREES; a += 0.25) {
                assertTrue(drive(force(t, SailTrim.FULL, a, 12, REST)) <= 1e-12, t.id() + " drives at " + a);
                assertTrue(drive(force(t, SailTrim.FULL, -a, 12, REST)) <= 1e-12, t.id() + " drives at " + -a);
            }
        }
    }

    @Test
    void shipRunningAtWindSpeedFeelsNoWind() {
        Vector3d wind = SailPolar.windFromAngle(180, 8);
        ShipState running = REST.withLinearVelocity(wind);
        ForceContribution c = SailForceModel.compute(new SailInstance(SailTypes.LARGE_SQUARE, SailTrim.FULL, MAST), wind, running, P, "s");
        assertEquals(0.0, c.force().length(), 1e-12);
        ShipState half = REST.withLinearVelocity(new Vector3d(wind).mul(0.5));
        double h = drive(SailForceModel.compute(new SailInstance(SailTypes.LARGE_SQUARE, SailTrim.FULL, MAST), wind, half, P, "s"));
        double full = drive(force(SailTypes.LARGE_SQUARE, SailTrim.FULL, 180, 8, REST));
        assertEquals(0.5, h / full, 1e-9);
    }

    @Test
    void apparentWindMovesForwardWhenSailing() {
        // Beam reach while moving forward: the apparent wind comes from further ahead, so the square sail drives less.
        ShipState moving = REST.withLinearVelocity(new Vector3d(ShipFrame.FORWARD).mul(4));
        double still = drive(force(SailTypes.LARGE_SQUARE, SailTrim.FULL, 90, 8, REST)) / 8.0;
        ForceContribution m = force(SailTypes.LARGE_SQUARE, SailTrim.FULL, 90, 8, moving);
        double apparent = Math.hypot(8, 4);
        assertTrue(drive(m) / apparent < still);
    }

    @Test
    void portAndStarboardAreMirrorImages() {
        for (SailType t : SailTypes.ALL) {
            for (double a = 0; a <= 180; a += 7.5) {
                ForceContribution s = force(t, SailTrim.FULL, a, 10, REST);
                ForceContribution p = force(t, SailTrim.FULL, -a, 10, REST);
                assertEquals(s.force().z(), p.force().z(), 1e-9);
                assertEquals(s.force().x(), -p.force().x(), 1e-9);
                assertEquals(s.torque().z(), -p.torque().z(), 1e-9);
                assertEquals(s.torque().y(), -p.torque().y(), 1e-9);
            }
        }
    }

    @Test
    void heelTorqueLeansTheShipToLeeward() {
        for (double a : new double[]{45, 90, 120}) {
            // Wind from starboard: leeward is port.
            ForceContribution c = force(SailTypes.FORE_AND_AFT, SailTrim.FULL, a, 10, REST);
            Vector3d top = new Vector3d(MAST).add(0, SailTypes.FORE_AND_AFT.centerOfEffortHeight(), 0);
            // With isotropic inertia the angular acceleration is parallel to the torque; the mast top moves along τ × r.
            Vector3d topMotion = c.torque().cross(top, new Vector3d());
            assertTrue(topMotion.dot(ShipFrame.PORT) > 0, "mast top should lean to port at " + a);
            assertTrue(c.force().dot(ShipFrame.PORT) > 0, "side force should push to leeward at " + a);
        }
    }

    @Test
    void forcesAreFiniteAndContinuousAllAround() {
        for (SailType t : SailTypes.ALL) {
            ForceContribution prev = force(t, SailTrim.FULL, -180, 10, REST);
            double maxStep = 0;
            for (int i = 1; i <= 3600; i++) {
                double a = -180 + i * 0.1;
                ForceContribution c = force(t, SailTrim.FULL, a, 10, REST);
                assertTrue(c.isFinite());
                maxStep = Math.max(maxStep, c.force().distance(prev.force()) / (t.area() * 10));
                prev = c;
            }
            assertTrue(maxStep < 0.01, t.id() + " jumps by " + maxStep + " per 0.1 degree");
        }
    }

    @Test
    void resultDoesNotDependOnWorldHeading() {
        Quaterniond turned = new Quaterniond().rotateY(Math.toRadians(73)).rotateX(Math.toRadians(5));
        ShipState ship = REST.withOrientation(turned);
        Vector3d localWind = SailPolar.windFromAngle(110, 9);
        Vector3d worldWind = turned.transform(new Vector3d(localWind));
        SailInstance sail = new SailInstance(SailTypes.LARGE_SQUARE, SailTrim.FULL, MAST);
        ForceContribution a = SailForceModel.compute(sail, localWind, REST, P, "s");
        ForceContribution b = SailForceModel.compute(sail, worldWind, ship, P, "s");
        assertTrue(a.force().distance(b.force()) < 1e-9);
        assertTrue(a.torque().distance(b.torque()) < 1e-9);
    }

    @Test
    void rotationChangesTheApparentWindAtTheSail() {
        // Yawing to port moves a mast ahead of the COM to port, which adds apparent wind from port.
        Vector3d ahead = new Vector3d(0, 3, 8);
        ShipState yawing = REST.withAngularVelocity(new Vector3d(0, 0.5, 0));
        Vector3d atSail = yawing.velocityAt(new Vector3d(ahead).add(0, SailTypes.SMALL_SQUARE.centerOfEffortHeight(), 0), new Vector3d());
        assertTrue(atSail.dot(ShipFrame.PORT) > 0);
        ForceContribution still = SailForceModel.compute(new SailInstance(SailTypes.SMALL_SQUARE, SailTrim.FULL, ahead),
                new Vector3d(), REST, P, "s");
        ForceContribution spun = SailForceModel.compute(new SailInstance(SailTypes.SMALL_SQUARE, SailTrim.FULL, ahead),
                new Vector3d(), yawing, P, "s");
        assertEquals(0.0, still.force().length());
        assertTrue(spun.force().length() > 0);
    }

    @Test
    void polarMatchesTheEfficiencyTables() {
        for (SailType t : SailTypes.ALL) {
            for (SailPolar.Point p : SailPolar.curve(t, 15)) {
                assertEquals(t.curve().drive(p.windAngleDeg()), p.drive(), 1e-9);
                assertEquals(t.curve().side(p.windAngleDeg()), p.side(), 1e-9);
            }
            assertEquals(13, SailPolar.curve(t, 15).size());
        }
    }

    @Test
    void efficiencyCurveValidation() {
        assertThrows(IllegalArgumentException.class, () -> EfficiencyCurve.of(0, 0, 0, 90, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> EfficiencyCurve.of(0, 0, 0.1, 180, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> EfficiencyCurve.of(0, 0, 0, 90, 1, 1, 90, 1, 1, 180, 1, 0));
        EfficiencyCurve c = EfficiencyCurve.of(0, 0, 0, 90, 1, 1, 180, 0.5, 0);
        assertEquals(0.5, c.drive(45), 1e-12);
        assertEquals(0.75, c.drive(135), 1e-12);
        assertEquals(0.5, c.drive(500), 1e-12);
        assertEquals(0.0, c.drive(-3), 1e-12);
    }
}
