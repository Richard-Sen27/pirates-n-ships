package com.richardsenger.piratesnships.hazards;

import com.richardsenger.piratesnships.sailing.force.SailTrim;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnRulesTest {

    private static final double EPS = 1e-9;

    @Test
    void checksRunOnTheInterval() {
        assertTrue(SpawnRules.isCheckTick(400, 200));
        assertFalse(SpawnRules.isCheckTick(401, 200));
        assertFalse(SpawnRules.isCheckTick(400, 0));
    }

    @Test
    void waterspoutsNeedThunderAndTheSea() {
        assertTrue(SpawnRules.rollWaterspout(true, true, true, 0, 1, 0.02, 0.01));
        assertFalse(SpawnRules.rollWaterspout(true, false, true, 0, 1, 0.02, 0.01), "clear or rain only");
        assertFalse(SpawnRules.rollWaterspout(true, true, false, 0, 1, 0.02, 0.01), "not at sea");
        assertFalse(SpawnRules.rollWaterspout(false, true, true, 0, 1, 1.0, 0.0), "disabled");
        assertFalse(SpawnRules.rollWaterspout(true, true, true, 0, 1, 0.02, 0.02), "roll at the chance fails");
    }

    @Test
    void whirlpoolsNeedTheDeepOceanButNoThunder() {
        assertTrue(SpawnRules.rollWhirlpool(true, true, 0, 1, 0.005, 0.001));
        assertFalse(SpawnRules.rollWhirlpool(true, false, 0, 1, 0.005, 0.001), "shallow ocean");
        assertFalse(SpawnRules.rollWhirlpool(false, true, 0, 1, 1.0, 0.0), "disabled");
    }

    @Test
    void perPlayerCapStopsNewHazards() {
        assertFalse(SpawnRules.rollWaterspout(true, true, true, 1, 1, 1.0, 0.0));
        assertTrue(SpawnRules.rollWaterspout(true, true, true, 1, 2, 1.0, 0.0));
        assertFalse(SpawnRules.rollWhirlpool(true, true, 1, 1, 1.0, 0.0));
        assertFalse(SpawnRules.rollWhirlpool(true, true, 0, 0, 1.0, 0.0), "a cap of 0 means none");
    }

    @Test
    void chanceZeroNeverSpawns() {
        Random r = new Random(7);
        for (int i = 0; i < 10_000; i++) {
            double roll = r.nextDouble();
            assertFalse(SpawnRules.rollWaterspout(true, true, true, 0, 1, 0.0, roll));
            assertFalse(SpawnRules.rollWhirlpool(true, true, 0, 1, 0.0, roll));
        }
        assertFalse(SpawnRules.rollWaterspout(true, true, true, 0, 1, 0.0, 0.0), "even a zero roll");
    }

    @Test
    void offsetsStayInTheDistanceBand() {
        Random r = new Random(11);
        for (int i = 0; i < 10_000; i++) {
            double[] o = SpawnRules.offset(r.nextDouble(), r.nextDouble(), 48, 96);
            double d = Math.hypot(o[0], o[1]);
            assertTrue(d >= 48 - EPS && d <= 96 + EPS, "distance " + d);
        }
        assertEquals(48, Math.hypot(SpawnRules.offset(0.3, 0.0, 48, 96)[0], SpawnRules.offset(0.3, 0.0, 48, 96)[1]), EPS);
        double[] east = SpawnRules.offset(0.0, 1.0, 48, 96);
        assertEquals(96, east[0], EPS);
        assertEquals(0, east[1], EPS);
        double[] swapped = SpawnRules.offset(0.25, 0.0, 96, 48);
        assertEquals(48, Math.hypot(swapped[0], swapped[1]), EPS, "a reversed band is swapped");
        assertEquals(104, SpawnRules.capRange(96, 8), EPS);
    }

    @Test
    void driftMovesAlongTheHeading() {
        double[] p = Drift.step(10, 20, 0, 0.02);
        assertEquals(10.02, p[0], EPS);
        assertEquals(20, p[1], EPS);
        double[] q = Drift.step(0, 0, Math.PI / 2, 0.5);
        assertEquals(0, q[0], EPS);
        assertEquals(0.5, q[1], EPS);
        double[] still = Drift.step(3, 4, 1.0, 0);
        assertEquals(3, still[0], EPS);
        assertEquals(4, still[1], EPS);
        double x = 0, z = 0;
        for (int i = 0; i < 100; i++) {
            double[] s = Drift.step(x, z, 0.7, 0.02);
            x = s[0];
            z = s[1];
        }
        assertEquals(2.0, Math.hypot(x, z), 1e-9, "100 ticks at 0.02 blocks/tick");
    }

    @Test
    void driftTurnsAwayFromLand() {
        assertEquals(Math.PI, Drift.turnAway(0, 0.5), EPS, "straight back with a middle roll");
        double h = Drift.turnAway(0, 0.0);
        assertEquals(Math.PI - Math.PI / 4, h, EPS);
        double wrapped = Drift.turnAway(Math.PI * 1.9, 0.99);
        assertTrue(wrapped >= 0 && wrapped < Math.PI * 2, "normalized: " + wrapped);
        for (double roll = 0; roll < 1; roll += 0.1) {
            double turned = Drift.turnAway(1.0, roll);
            double diff = Math.abs(Math.atan2(Math.sin(turned - 1.0), Math.cos(turned - 1.0)));
            assertTrue(diff >= Math.PI * 0.75 - EPS, "turned at least 135°: " + diff);
        }
    }

    @Test
    void sailsTearOneStepAtATime() {
        assertEquals(SailTrim.HALF, SailTear.torn(SailTrim.FULL));
        assertEquals(SailTrim.FURLED, SailTear.torn(SailTrim.HALF));
        assertNull(SailTear.torn(SailTrim.FURLED));
        assertTrue(SailTear.tears(SailTrim.FULL, 0.15, 0.1));
        assertFalse(SailTear.tears(SailTrim.FULL, 0.15, 0.2));
        assertFalse(SailTear.tears(SailTrim.FURLED, 1.0, 0.0), "furled sails are safe");
        assertFalse(SailTear.tears(SailTrim.HALF, 0.0, 0.0));
    }

    @Test
    void hazardIdsRoundTrip() {
        for (HazardKind k : HazardKind.values()) {
            assertEquals(k, HazardKind.byId(k.id()).orElseThrow());
        }
        assertTrue(HazardKind.byId("kraken").isEmpty());
    }
}
