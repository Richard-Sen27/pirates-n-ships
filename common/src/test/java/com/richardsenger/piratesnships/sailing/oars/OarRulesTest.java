package com.richardsenger.piratesnships.sailing.oars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.sailing.oars.OarRules.Params;
import com.richardsenger.piratesnships.sailing.oars.OarRules.Rower;
import com.richardsenger.piratesnships.sailing.oars.OarRules.Side;
import com.richardsenger.piratesnships.sailing.oars.OarRules.Thrust;
import java.util.List;
import org.junit.jupiter.api.Test;

/** OAR1: the oars' thrust (count, bench bonus, work speed, the fade toward max_speed, side torque) and the watch. */
class OarRulesTest {

    private static final Params P = Params.DEFAULTS;
    private static final Rower PORT = new Rower(false, Side.PORT);
    private static final Rower STBD = new Rower(false, Side.STARBOARD);
    private static final Rower BENCH_PORT = new Rower(true, Side.PORT);
    private static final Rower BENCH_STBD = new Rower(true, Side.STARBOARD);

    @Test
    void defaultsAreTheDecision() {
        assertEquals(1.25, P.benchBonus(), 1e-12);
        assertEquals(3.0, P.maxSpeed(), 1e-12);
        assertTrue(P.thrustPerRower() > 0);
    }

    @Test
    void thrustGrowsWithTheRowersFromRest() {
        assertEquals(Thrust.NONE, OarRules.thrust(List.of(), 1.0, 0.0, P));
        Thrust one = OarRules.thrust(List.of(PORT), 1.0, 0.0, P);
        Thrust three = OarRules.thrust(List.of(PORT, STBD, PORT), 1.0, 0.0, P);
        assertEquals(P.thrustPerRower(), one.force(), 1e-9);
        assertEquals(3 * P.thrustPerRower(), three.force(), 1e-9);
        assertEquals(1.0, three.fade(), 1e-12);
    }

    @Test
    void aSeatedRowerPullsTheBenchBonus() {
        double rail = OarRules.thrust(List.of(PORT, STBD), 1.0, 0.0, P).force();
        double benches = OarRules.thrust(List.of(BENCH_PORT, BENCH_STBD), 1.0, 0.0, P).force();
        assertEquals(1.25, benches / rail, 1e-9);
        double mixed = OarRules.thrust(List.of(BENCH_PORT, BENCH_STBD, PORT), 1.0, 0.0, P).force();
        assertEquals(3.5 * P.thrustPerRower(), mixed, 1e-9);
    }

    @Test
    void aTiredCrewRowsSlowerButNeverStops() {
        double fed = OarRules.thrust(List.of(PORT, STBD), 1.0, 0.0, P).force();
        assertEquals(0.5 * fed, OarRules.thrust(List.of(PORT, STBD), 0.5, 0.0, P).force(), 1e-9);
        assertEquals(OarRules.MIN_WORK_SPEED * fed, OarRules.thrust(List.of(PORT, STBD), 0.0, 0.0, P).force(), 1e-9);
        assertEquals(fed, OarRules.thrust(List.of(PORT, STBD), 1.7, 0.0, P).force(), 1e-9, "never faster than fed");
        assertEquals(fed, OarRules.thrust(List.of(PORT, STBD), Double.NaN, 0.0, P).force(), 1e-9);
    }

    @Test
    void theForceFadesOutAtMaxSpeed() {
        double full = OarRules.thrust(List.of(PORT, STBD), 1.0, 0.0, P).force();
        assertEquals(0.5 * full, OarRules.thrust(List.of(PORT, STBD), 1.0, 1.5, P).force(), 1e-9, "half way to 3 m/s");
        assertEquals(0.0, OarRules.thrust(List.of(PORT, STBD), 1.0, 3.0, P).force(), 1e-12, "nothing at max_speed");
        assertEquals(0.0, OarRules.thrust(List.of(PORT, STBD), 1.0, 5.0, P).force(), 1e-12, "nothing beyond, never a brake");
        assertEquals(full, OarRules.thrust(List.of(PORT, STBD), 1.0, -1.0, P).force(), 1e-9, "astern: full");
        double last = Double.MAX_VALUE;
        for (double v = 0; v <= 3.0; v += 0.25) {
            double f = OarRules.thrust(List.of(PORT), 1.0, v, P).force();
            assertTrue(f <= last + 1e-12, "falls monotonically, at " + v);
            last = f;
        }
        assertEquals(0.0, OarRules.fade(0.0, 0.0), 1e-12, "max_speed 0: no rowing at all");
    }

    /**
     * Equilibrium with a linear water drag k·v: n·T·(1 − v/vmax) = k·v gives v = n·T / (k + n·T/vmax) below vmax, so
     * more rowers (or benches) are faster but never reach vmax.
     */
    @Test
    void anyCrewStaysBelowMaxSpeedAgainstDrag() {
        double k = 130.0; // the ballasted test hull's drag per m/s, about
        double previous = 0;
        for (int n = 1; n <= 40; n++) {
            double nT = n * P.thrustPerRower();
            double v = nT / (k + nT / P.maxSpeed());
            assertTrue(v < P.maxSpeed() && v > previous, n + " rowers: " + v);
            assertEquals(OarRules.thrust(java.util.Collections.nCopies(n, PORT), 1.0, v, P).force(), k * v, 1e-6);
            previous = v;
        }
    }

    @Test
    void unequalSidesYawTheShipTowardTheWeakSide() {
        assertEquals(0.0, OarRules.thrust(List.of(PORT, STBD), 1.0, 0.0, P).yawTorque(), 1e-12, "balanced");
        assertEquals(0.0, OarRules.thrust(List.of(BENCH_PORT, BENCH_STBD, PORT, STBD), 1.0, 0.0, P).yawTorque(), 1e-12);
        Thrust portHeavy = OarRules.thrust(List.of(PORT, PORT, STBD), 1.0, 0.0, P);
        // r × F of a port rower: (L, 0, 0) × (0, 0, f) = (0, −L·f, 0); −y yaw turns the bow to starboard
        assertEquals(-P.sideTorque() * P.thrustPerRower(), portHeavy.yawTorque(), 1e-9);
        assertEquals(-portHeavy.yawTorque(), OarRules.thrust(List.of(STBD, STBD, PORT), 1.0, 0.0, P).yawTorque(), 1e-9);
        assertEquals(0.0, OarRules.thrust(List.of(new Rower(true, Side.NONE)), 1.0, 0.0, P).yawTorque(), 1e-12);
        Params noTorque = new Params(40, 1.25, 3, 0, 1.1, 40);
        assertEquals(0.0, OarRules.thrust(List.of(PORT, PORT), 1.0, 0.0, noTorque).yawTorque(), 1e-12);
        assertEquals(0.5 * portHeavy.yawTorque(), OarRules.thrust(List.of(PORT, PORT, STBD), 1.0, 1.5, P).yawTorque(), 1e-9, "fades too");
    }

    @Test
    void theCrewShipOarsOnlyWhenTheSailsKeepTheShipFastEnough() {
        double fast = P.maxSpeed() * P.shipOarsSpeedFactor() + 0.01;
        int n = 0;
        for (int i = 0; i < P.shipOarsTicks() - 1; i++) n = OarRules.watch(n, fast, true, P);
        assertFalse(OarRules.shipOars(n, P), "not yet");
        assertTrue(OarRules.shipOars(OarRules.watch(n, fast, true, P), P), "after ship_oars_ticks");
        assertEquals(0, OarRules.watch(n, P.maxSpeed() * P.shipOarsSpeedFactor() - 0.01, true, P), "a lull resets the count");
        assertEquals(0, OarRules.watch(n, fast, false, P), "without a sail set it is not the sails' speed");
        assertEquals(0, OarRules.watch(n, P.maxSpeed() + 0.05, true, P), "just above max_speed is inside the margin");
        assertFalse(OarRules.shipOars(0, P));
    }
}
