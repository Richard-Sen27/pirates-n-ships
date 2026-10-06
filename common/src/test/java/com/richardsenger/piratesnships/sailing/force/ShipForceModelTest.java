package com.richardsenger.piratesnships.sailing.force;

import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.wind.WindParams;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import org.joml.Vector3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The combined per-ship helper, plus small translational simulations of the whole model. */
class ShipForceModelTest {

    private static final SailingParams P = SailingParams.DEFAULTS;
    /** Stand-in for Sable's own isotropic water drag in the simulations [1/s]. */
    private static final double ISOTROPIC_DRAG = 0.1;

    @AfterEach
    void resetConfig() {
        SailingConfig.SAIL_FORCE_SCALE.reset();
        SailingConfig.REGIONAL_VARIATION.reset();
    }

    /** Wind sample (world frame) for a ship facing world +Z: from {@code angle} degrees off the bow, + = starboard. */
    private static WindSample windFrom(double angle, double speed) {
        Vector3d v = SailPolar.windFromAngle(angle, speed);
        double toward = Math.toDegrees(Math.atan2(v.x, -v.z));
        return WindSample.of(toward, speed, 1, 0);
    }

    @Test
    void windSampleHelperMatchesPolarConvention() {
        Vector3d a = windFrom(60, 7).velocity(new Vector3d());
        assertTrue(a.distance(SailPolar.windFromAngle(60, 7)) < 1e-9);
    }

    @Test
    void breakdownSumsItsContributions() {
        ShipState ship = ShipState.atRest(150, 20).withLinearVelocity(new Vector3d(0.5, 0, 3)).withAngularVelocity(new Vector3d(0, 0.1, 0.02));
        List<SailInstance> sails = List.of(
                new SailInstance(SailTypes.LARGE_SQUARE, SailTrim.FULL, new Vector3d(0, 4, 2)),
                new SailInstance(SailTypes.FORE_AND_AFT, SailTrim.HALF, new Vector3d(0, 3, -5)));
        AnchorState anchor = new AnchorState(AnchorState.Phase.DROPPING, 0.4);
        ForceBreakdown f = ShipForceModel.compute(windFrom(120, 9), ship, sails,
                new ShipForceModel.Rudder(10, new Vector3d(0, -1, -10)),
                new ShipForceModel.Anchor(anchor, new Vector3d(20, -10, 20), new Vector3d(0, 0, 9)), P);
        assertEquals(5, f.contributions().size());
        Vector3d sumF = new Vector3d();
        Vector3d sumT = new Vector3d();
        f.contributions().forEach(c -> {
            sumF.add(c.force());
            sumT.add(c.torque());
            assertTrue(c.isFinite());
        });
        assertTrue(sumF.distance(f.force()) < 1e-9);
        assertTrue(sumT.distance(f.torque()) < 1e-9);
        assertTrue(f.get("sail[0]:large_square").isPresent());
        assertTrue(f.get("sail[1]:fore_and_aft").isPresent());
        assertTrue(f.get("keel").isPresent());
        assertTrue(f.get("rudder").isPresent());
        assertTrue(f.get("anchor").isPresent());
        assertTrue(f.forceOf("sail").length() > 0);
    }

    @Test
    void optionalInputsCanBeLeftOut() {
        ForceBreakdown f = ShipForceModel.compute(WindSample.CALM, ShipState.atRest(10, 5), List.of(), null, null, P);
        assertEquals(1, f.contributions().size());
        assertEquals(0.0, f.force().length());
    }

    /** Integrates translation only (heading fixed). Returns the ship-frame velocity after {@code seconds}. */
    private static Vector3d simulate(WindSample wind, List<SailInstance> sails, SailingParams p, double seconds) {
        double dt = 0.05;
        ShipState s = ShipState.atRest(100, 20);
        Vector3d v = new Vector3d();
        for (int i = 0; i < seconds / dt; i++) {
            s = s.withLinearVelocity(v);
            Vector3d f = s.toWorld(ShipForceModel.compute(wind, s, sails, null, null, p).force(), new Vector3d());
            f.y = 0;
            f.sub(new Vector3d(v).mul(ISOTROPIC_DRAG * s.mass()));
            v.add(f.mul(dt / s.mass()));
        }
        return s.withLinearVelocity(v).localVelocity(new Vector3d());
    }

    @Test
    void runningDownwindSettlesBelowWindSpeed() {
        WindSample wind = windFrom(180, 10);
        Vector3d v = simulate(wind, List.of(new SailInstance(SailTypes.LARGE_SQUARE, SailTrim.FULL, new Vector3d(0, 4, 0))), P, 120);
        assertTrue(v.z > 3 && v.z < 10, "downwind speed " + v.z);
        assertEquals(0.0, v.x, 1e-6);
    }

    @Test
    void keelLetsAFourAndAftShipBeatToWindward() {
        List<SailInstance> sails = List.of(new SailInstance(SailTypes.FORE_AND_AFT, SailTrim.FULL, new Vector3d(0, 3, 0)));
        WindSample wind = windFrom(55, 10);
        Vector3d v = simulate(wind, sails, P, 120);
        Vector3d upwind = SailPolar.windFromAngle(55, 1).negate(); // unit vector toward where the wind comes from
        double vmg = v.dot(upwind);
        double leeway = Math.toDegrees(Math.atan2(Math.abs(v.x), v.z));
        assertTrue(v.z > 1.0, "should make headway close-hauled, forward " + v.z);
        assertTrue(vmg > 0.5, "should gain ground to windward, VMG " + vmg);
        assertTrue(leeway < 10, "keel should keep leeway small, was " + leeway);

        Vector3d noKeel = simulate(wind, sails, P.withKeelEnabled(false), 120);
        double leewayNoKeel = Math.toDegrees(Math.atan2(Math.abs(noKeel.x), noKeel.z));
        assertTrue(leewayNoKeel > 30, "without keel the ship should mostly drift, leeway " + leewayNoKeel);
        assertTrue(noKeel.dot(upwind) < vmg);
    }

    @Test
    void squareRiggerCannotBeatToWindward() {
        List<SailInstance> sails = List.of(new SailInstance(SailTypes.LARGE_SQUARE, SailTrim.FULL, new Vector3d(0, 4, 0)));
        Vector3d v = simulate(windFrom(40, 10), sails, P, 60);
        assertTrue(v.z <= 1e-6, "square sails close to the wind must not drive forward, got " + v.z);
    }

    @Test
    void configAdaptersReturnTheDefaultsWhenUnbound() {
        assertEquals(WindParams.DEFAULTS, SailingConfig.windParams());
        assertEquals(SailingParams.DEFAULTS, SailingConfig.sailingParams());
        SailingConfig.SAIL_FORCE_SCALE.set(3.0);
        SailingConfig.REGIONAL_VARIATION.set(true);
        assertEquals(3.0, SailingConfig.sailingParams().sailForceScale());
        assertTrue(SailingConfig.windParams().regionalVariation());
    }
}
