package com.richardsenger.piratesnships.sailing.effects;

import com.richardsenger.piratesnships.hazards.waves.SeaState;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** The pure spawn rules of the wind streaks and the foam (WD1). */
class SeaEffectRulesTest {

    private static final double EPS = 1e-9;

    // ---- SpawnRules ----

    @Test
    void countHasTheRateAsItsMean() {
        Random r = new Random(1);
        for (double rate : new double[] {0.3, 1.0, 2.7, 6.25}) {
            long sum = 0;
            int n = 200_000;
            for (int i = 0; i < n; i++) {
                int c = SpawnRules.count(rate, r.nextDouble());
                assertTrue(c == (int) Math.floor(rate) || c == (int) Math.floor(rate) + 1, "count " + c + " for " + rate);
                sum += c;
            }
            assertEquals(rate, (double) sum / n, 0.01, "mean for rate " + rate);
        }
    }

    @Test
    void countIsZeroForNoRate() {
        assertEquals(0, SpawnRules.count(0.0, 0.0));
        assertEquals(0, SpawnRules.count(-1.0, 0.0));
        assertEquals(0, SpawnRules.count(Double.NaN, 0.0));
    }

    @Test
    void ringPointsStayInTheRingAndSpreadByArea() {
        Random r = new Random(2);
        double inner = 3, outer = 24, cx = 100.5, cz = -40.25;
        double split = Math.sqrt((inner * inner + outer * outer) / 2.0); // half the ring's area inside this radius
        int insideSplit = 0, n = 100_000;
        double sumX = 0, sumZ = 0;
        for (int i = 0; i < n; i++) {
            double[] p = SpawnRules.ringPoint(cx, cz, inner, outer, r.nextDouble(), r.nextDouble());
            double d = Math.hypot(p[0] - cx, p[1] - cz);
            assertTrue(d >= inner - EPS && d <= outer + EPS, "distance " + d);
            if (d < split) insideSplit++;
            sumX += p[0] - cx;
            sumZ += p[1] - cz;
        }
        assertEquals(0.5, (double) insideSplit / n, 0.01);
        assertEquals(0.0, sumX / n, 0.2, "centred in x");
        assertEquals(0.0, sumZ / n, 0.2, "centred in z");
    }

    @Test
    void fadeRisesHoldsAndFalls() {
        double life = 40;
        assertEquals(0.0, SpawnRules.fade(0, life), EPS);
        assertEquals(0.0, SpawnRules.fade(life, life), EPS);
        assertEquals(1.0, SpawnRules.fade(life / 2, life), EPS);
        assertEquals(1.0, SpawnRules.fade(life * SpawnRules.FADE_FRACTION, life), EPS);
        assertEquals(0.0, SpawnRules.fade(-1, life), EPS);
        assertEquals(0.0, SpawnRules.fade(life + 1, life), EPS);
        double last = -1;
        for (double a = 0; a <= life * SpawnRules.FADE_FRACTION; a += 0.5) {
            double f = SpawnRules.fade(a, life);
            assertTrue(f >= last, "rising at " + a);
            assertEquals(f, SpawnRules.fade(life - a, life), 1e-9, "symmetric at " + a);
            last = f;
        }
    }

    @Test
    void bearingMatchesTheWindConvention() {
        for (double deg : new double[] {0, 45, 90, 180, 271.5}) {
            double[] d = SpawnRules.bearing(deg);
            WindSample w = WindSample.of(deg, 5, 1, 0);
            assertEquals(w.dirX(), d[0], 1e-12);
            assertEquals(w.dirZ(), d[1], 1e-12);
        }
        assertArrayEquals(new double[] {0, -1}, SpawnRules.bearing(0), 1e-12);   // north = −Z
        assertArrayEquals(new double[] {1, 0}, SpawnRules.bearing(90), 1e-12);   // east = +X
    }

    @Test
    void particleSettingScalesTheRate() {
        assertEquals(1.0, SpawnRules.particleSetting(0)); // all
        assertEquals(0.5, SpawnRules.particleSetting(1)); // decreased
        assertEquals(0.0, SpawnRules.particleSetting(2)); // minimal
    }

    // ---- WindStreakRules ----

    @Test
    void noStreaksInCalmAirAndMoreWithMoreWind() {
        WindStreakRules w = WindStreakRules.DEFAULTS;
        assertEquals(0.0, w.rate(0.0, 0.0));
        assertEquals(0.0, w.rate(w.minStrength() - 0.01, 0.0), "calm air");
        assertEquals(w.density() * w.minStrength(), w.rate(w.minStrength(), 0.0), EPS);
        double breeze = w.rate(8.0, 0.0), gale = w.rate(26.0, 0.0);
        assertEquals(w.density() * 8.0, breeze, EPS, "density × strength");
        assertTrue(gale > 3 * breeze, "a gale shows far more streaks than a breeze");
    }

    @Test
    void gustsAddStreaks() {
        WindStreakRules w = new WindStreakRules(0.1, 2.0, 1.0, 24, 12, 40);
        assertEquals(2.0 * w.rate(10.0, 0.0), w.rate(10.0, 1.0), EPS, "boost 1 doubles at the gust's peak");
        assertEquals(1.5 * w.rate(10.0, 0.0), w.rate(10.0, 0.5), EPS);
        assertEquals(w.rate(10.0, 1.0), w.rate(10.0, 3.0), EPS, "gust clamped to 1");
    }

    @Test
    void streaksAreCentredOnTheCameraHalfwayThroughTheirLife() {
        WindStreakRules w = WindStreakRules.DEFAULTS;
        WindSample wind = WindSample.of(60.0, 12.0, 1.0, 0.0);
        double travel = w.travel(wind.strength());
        assertEquals(12.0 * w.lifeTicks() / 20.0, travel, EPS);
        assertEquals(travel, WindStreakRules.speedPerTick(wind.strength()) * w.lifeTicks(), 1e-9,
                "speed per tick × life = travel: the streak flies at the wind's speed");
        Random r = new Random(3);
        double camX = 10, camZ = 20, sx = 0, sz = 0, mx = 0, mz = 0;
        int n = 100_000;
        for (int i = 0; i < n; i++) {
            double[] p = w.start(camX, camZ, wind.dirX(), wind.dirZ(), wind.strength(), r.nextDouble(), r.nextDouble());
            sx += p[0];
            sz += p[1];
            mx += p[0] + wind.dirX() * travel / 2;
            mz += p[1] + wind.dirZ() * travel / 2;
        }
        // starts are shifted upwind (against the direction the wind blows toward)
        assertEquals(camX - wind.dirX() * travel / 2, sx / n, 0.2);
        assertEquals(camZ - wind.dirZ() * travel / 2, sz / n, 0.2);
        // mid-life positions are centred on the camera
        assertEquals(camX, mx / n, 0.2);
        assertEquals(camZ, mz / n, 0.2);
    }

    @Test
    void streaksFlyBetweenDeckHeightAndTheRigging() {
        WindStreakRules w = WindStreakRules.DEFAULTS;
        double sea = 63;
        assertEquals(sea + WindStreakRules.BOTTOM, w.y(sea, 3.0, 0.0), EPS);
        assertEquals(sea + w.height(), w.y(sea, 3.0, 1.0), EPS);
        assertTrue(w.inRange(sea + 2, sea), "on deck");
        assertTrue(w.inRange(sea + 20, sea), "in the crow's nest");
        assertFalse(w.inRange(sea + w.height() + w.radius() + 1, sea), "far above the sea");
        assertFalse(w.inRange(sea - 10, sea), "deep under water");
    }

    @Test
    void streakLengthGrowsWithTheWindAndIsClamped() {
        assertEquals(WindStreakRules.MIN_LENGTH, WindStreakRules.length(0.0), EPS);
        assertEquals(WindStreakRules.MAX_LENGTH, WindStreakRules.length(100.0), EPS);
        assertTrue(WindStreakRules.length(20.0) > WindStreakRules.length(8.0));
    }

    @Test
    void streakFacesTheCameraAcrossItsAxis() {
        double ax = Math.sin(Math.toRadians(30)), az = -Math.cos(Math.toRadians(30));
        double[][] views = {{5, 2, -3}, {-4, -1, 7}, {0.5, 10, 0.2}, {3, 0, 1}};
        for (double[] p : views) {
            double[] s = WindStreakRules.facingSide(ax, az, p[0], p[1], p[2]);
            assertEquals(1.0, Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]), 1e-9, "unit");
            assertEquals(0.0, s[0] * ax + s[2] * az, 1e-9, "across the axis");
            assertEquals(0.0, s[0] * p[0] + s[1] * p[1] + s[2] * p[2], 1e-9, "across the line of sight");
        }
        assertArrayEquals(new double[] {0, 1, 0}, WindStreakRules.facingSide(1, 0, 5, 0, 0), EPS, "seen end-on");
    }

    @Test
    void defaultsMatchTheSpec() {
        WindStreakRules w = WindStreakRules.DEFAULTS;
        assertEquals(24.0, w.radius());
        assertEquals(12.0, w.height());
        FoamRules f = FoamRules.DEFAULTS;
        assertEquals(24.0, f.radius());
    }

    // ---- FoamRules ----

    @Test
    void noFoamInACalmSeaAndMoreInAHigherSea() {
        FoamRules f = FoamRules.DEFAULTS;
        assertEquals(0.0, f.rate(0.0));
        assertEquals(0.0, f.rate(SeaState.CALM.amplitude()), "calm sea");
        double moderate = f.rate(SeaState.MODERATE.amplitude());
        double rough = f.rate(SeaState.ROUGH.amplitude());
        double storm = f.rate(SeaState.STORM.amplitude());
        assertTrue(moderate > 0.0, "a moderate sea has some foam");
        assertTrue(rough > moderate && storm > rough);
        assertEquals(f.density() * SeaState.STORM.amplitude(), storm, EPS);
    }

    @Test
    void foamGathersOnTheCrests() {
        double a = 1.2;
        assertEquals(FoamRules.TROUGH_KEEP, FoamRules.crestKeep(-a, a), EPS);
        assertEquals(1.0, FoamRules.crestKeep(a, a), EPS);
        assertEquals(0.0, FoamRules.crestKeep(0.0, 0.0), EPS, "flat sea");
        double last = 0;
        for (double h = -a; h <= a; h += 0.1) {
            double k = FoamRules.crestKeep(h, a);
            assertTrue(k >= last && k <= 1.0);
            last = k;
        }
    }

    @Test
    void foamStreaksGrowAndDriftWithTheSea() {
        assertTrue(FoamRules.length(SeaState.STORM.amplitude()) > FoamRules.length(SeaState.MODERATE.amplitude()));
        assertTrue(FoamRules.length(10) <= FoamRules.MAX_LENGTH);
        assertTrue(FoamRules.driftPerTick(SeaState.STORM.amplitude()) > FoamRules.driftPerTick(SeaState.MODERATE.amplitude()));
        assertTrue(FoamRules.opacity(SeaState.STORM.amplitude()) > FoamRules.opacity(SeaState.MODERATE.amplitude()));
        assertTrue(FoamRules.opacity(100) <= 0.55);
    }

    @Test
    void foamOnlyNearTheWater() {
        FoamRules f = FoamRules.DEFAULTS;
        assertTrue(f.inRange(64.6, 63));
        assertFalse(f.inRange(63 + 2 * f.radius() + 1, 63));
        assertFalse(f.inRange(50, 63));
    }
}
