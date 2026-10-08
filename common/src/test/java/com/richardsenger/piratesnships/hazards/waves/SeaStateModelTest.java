package com.richardsenger.piratesnships.hazards.waves;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SeaStateModelTest {

    @Test
    void weatherPicksTheState() {
        assertEquals(SeaState.STORM, SeaStateModel.target(1.0, 1.0, -1));
        assertEquals(SeaState.ROUGH, SeaStateModel.target(1.0, 0.0, 1));
        assertEquals(SeaState.ROUGH, SeaStateModel.target(0.6, 0.4, 0));
        assertEquals(SeaState.MODERATE, SeaStateModel.target(0.0, 0.0, 0.5));
        assertEquals(SeaState.CALM, SeaStateModel.target(0.2, 0.0, -0.5));
    }

    @Test
    void clearWeatherHasCalmAndModerateStretches() {
        int moderate = 0, changes = 0;
        SeaState last = null;
        for (long t = 0; t < 24000L * 20; t += 200) {
            SeaState s = SeaStateModel.target(0, 0, SeaStateModel.clearNoise(42L, t));
            if (s == SeaState.MODERATE) moderate++;
            if (last != null && s != last) changes++;
            last = s;
        }
        int samples = 24000 * 20 / 200;
        assertTrue(moderate > samples / 10 && moderate < samples * 9 / 10, "moderate share " + moderate + " of " + samples);
        assertTrue(changes >= 10 && changes < 200, "changes " + changes);
    }

    @Test
    void easingTakesTheConfiguredTimeFromCalmToStorm() {
        double rate = SeaStateModel.ratePerTick(60);
        double a = SeaState.CALM.amplitude();
        int ticks = 0;
        while (a < SeaState.STORM.amplitude()) {
            a = SeaStateModel.ease(a, SeaState.STORM.amplitude(), rate);
            ticks++;
        }
        assertEquals(1200, ticks, 1);
        assertEquals(SeaState.STORM.amplitude(), a);
        assertEquals(0.5, SeaStateModel.ease(0.7, 0.5, 1.0));
        assertEquals(0.69, SeaStateModel.ease(0.7, 0.5, 0.01), 1e-12);
    }

    @Test
    void trackerStartsAtTheWeatherThenEases() {
        SeaStateModel.Tracker t = new SeaStateModel.Tracker();
        t.tick(SeaState.ROUGH, 0.01, 0);
        assertEquals(SeaState.ROUGH.amplitude(), t.amplitude());
        t.tick(SeaState.STORM, 0.01, 1);
        assertEquals(SeaState.ROUGH.amplitude() + 0.01, t.amplitude(), 1e-12);
        assertEquals(SeaState.STORM, t.target());
        assertEquals(SeaState.ROUGH, t.current());
        for (int i = 0; i < 100; i++) t.tick(SeaState.STORM, 0.01, 2 + i);
        assertEquals(SeaState.STORM, t.current());
    }

    @Test
    void overrideJumpsHoldsAndExpires() {
        SeaStateModel.Tracker t = new SeaStateModel.Tracker();
        t.tick(SeaState.CALM, 0.001, 0);
        t.setOverride(SeaState.STORM, 90.0, 100);
        assertEquals(SeaState.STORM.amplitude(), t.amplitude());
        t.tick(SeaState.CALM, 0.001, 50);
        assertEquals(SeaState.STORM.amplitude(), t.amplitude());
        assertEquals(90.0, t.overrideDirection());
        t.tick(SeaState.CALM, 0.001, 100);
        assertNull(t.override());
        assertNull(t.overrideDirection());
        assertEquals(SeaState.STORM.amplitude() - 0.001, t.amplitude(), 1e-12);
    }

    @Test
    void anOverrideCanPinTheOriginUntilItEnds() {
        SeaStateModel.Tracker t = new SeaStateModel.Tracker();
        assertEquals(WaveField.Origin.NONE, t.origin());
        WaveField.Origin o = new WaveField.Origin(40.0, 1.0, 2.0);
        t.setOverride(SeaState.STORM, 90.0, 100, o);
        t.tick(SeaState.CALM, 0.001, 50);
        assertEquals(o, t.origin());
        t.tick(SeaState.CALM, 0.001, 100);
        assertEquals(WaveField.Origin.NONE, t.origin());
        t.setOverride(SeaState.ROUGH, null, SeaStateModel.Tracker.FOREVER);
        assertEquals(WaveField.Origin.NONE, t.origin());
    }

    @Test
    void nearestStateAndIds() {
        assertEquals(SeaState.MODERATE, SeaState.nearest(0.35));
        assertEquals(SeaState.ROUGH, SeaState.nearest(0.9));
        assertEquals(SeaState.STORM, SeaState.byId("storm").orElseThrow());
        assertTrue(SeaState.byId("hurricane").isEmpty());
        assertTrue(SeaState.ROUGH.spray() && SeaState.STORM.spray() && !SeaState.MODERATE.spray());
    }

    @Test
    void directionFollowsTheWindWithinTheOffset() {
        for (long t = 0; t < 100000; t += 977) {
            double d = SeaStateModel.directionDegrees(123.0, 7L, t);
            assertTrue(Math.abs(d - 123.0) <= SeaStateModel.DIRECTION_NOISE_DEG + 1e-9);
        }
    }
}
