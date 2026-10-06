package com.richardsenger.piratesnships.audio.creak;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The creak trigger: rate, minimum interval, silence at rest, randomized output, determinism. */
class CreakTriggerTest {

    private static final CreakTrigger.Params P = CreakTrigger.Params.DEFAULTS;
    private static final int TICKS = 200_000; // about 2.8 hours of game time

    /** A rolling series: rate = amplitude · |sin| of a 3 s roll period (a ship rocking side to side). */
    private static double rolling(int tick, double amplitude) {
        return amplitude * Math.abs(Math.sin(2 * Math.PI * tick / 60.0));
    }

    private static List<Integer> run(double amplitude, CreakTrigger.Params p, long seed) {
        CreakTrigger t = new CreakTrigger();
        Random r = new Random(seed);
        List<Integer> ticks = new ArrayList<>();
        for (int i = 0; i < TICKS; i++) {
            if (t.tick(rolling(i, amplitude), p, r) != null) ticks.add(i);
        }
        return ticks;
    }

    @Test
    void silentWhileStill() {
        CreakTrigger t = new CreakTrigger();
        Random r = new Random(1);
        for (int i = 0; i < TICKS; i++) {
            assertNull(t.tick(0.0, P, r));
            assertNull(t.tick(P.rollRateThreshold() * 0.99, P, r), "creaked below the threshold");
        }
    }

    @Test
    void silentWhenDisabled() {
        CreakTrigger.Params off = new CreakTrigger.Params(false, 5, 1, 0.2, 0.5, 0.5, 0.8, 0.05);
        assertTrue(run(1.0, off, 3).isEmpty());
    }

    @Test
    void rateRisesWithTheRollRate() {
        int gentle = run(0.1, P, 7).size();
        int moderate = run(0.2, P, 7).size();
        int hard = run(0.6, P, 7).size();
        assertTrue(gentle > 0, "a gently rolling ship never creaked");
        assertTrue(moderate > gentle * 1.3, "rate did not rise: " + gentle + " -> " + moderate);
        assertTrue(hard > moderate * 1.3, "rate did not rise: " + moderate + " -> " + hard);
        // occasional, not a loop: even hard rolling stays below one creak per minimum interval
        assertTrue(hard < TICKS / P.minIntervalTicks(), "creaks every interval: " + hard);
        // and a gentle roll is rare: on average more than 5 s between creaks
        assertTrue(gentle < TICKS / 100, "a gentle roll creaks too often: " + gentle);
    }

    @Test
    void minimumIntervalHolds() {
        CreakTrigger.Params eager = new CreakTrigger.Params(true, 20, 25, 0.2, 0.5, 0.5, 0.8, 0.05);
        List<Integer> ticks = run(2.0, eager, 11);
        assertTrue(ticks.size() > 100);
        for (int i = 1; i < ticks.size(); i++) {
            assertTrue(ticks.get(i) - ticks.get(i - 1) >= 25, "interval broken at " + ticks.get(i));
        }
    }

    @Test
    void deterministicWithASeededRandom() {
        assertEquals(run(0.3, P, 42), run(0.3, P, 42));
        assertNotEquals(run(0.3, P, 42), run(0.3, P, 43));
    }

    @Test
    void volumePitchAndPositionAreRandomizedWithinTheirRanges() {
        CreakTrigger.Params eager = new CreakTrigger.Params(true, 20, 1, 0.2, 0.6, 0.5, 0.8, 0.05);
        CreakTrigger t = new CreakTrigger();
        Random r = new Random(5);
        double minV = 9, maxV = 0, minP = 9, maxP = 0, minX = 9, maxX = 0;
        int n = 0;
        for (int i = 0; i < 20_000; i++) {
            CreakTrigger.Creak c = t.tick(1.0, eager, r);
            if (c == null) continue;
            n++;
            minV = Math.min(minV, c.volume()); maxV = Math.max(maxV, c.volume());
            minP = Math.min(minP, c.pitch()); maxP = Math.max(maxP, c.pitch());
            minX = Math.min(minX, c.fx()); maxX = Math.max(maxX, c.fx());
            assertTrue(c.fy() >= 0 && c.fy() <= 0.5, "creak above the lower half of the hull");
            assertTrue(c.fz() >= 0 && c.fz() <= 1);
        }
        assertTrue(n > 1000);
        assertTrue(minV >= 0.2 && maxV <= 0.6 && maxV - minV > 0.3, "volume range " + minV + ".." + maxV);
        assertTrue(minP >= 0.5 && maxP <= 0.8 && maxP - minP > 0.2, "pitch range " + minP + ".." + maxP);
        assertTrue(minX < 0.05 && maxX > 0.95, "position does not spread over the hull");
    }

    @Test
    void gentleRollIsQuieterThanHardRoll() {
        CreakTrigger.Params eager = new CreakTrigger.Params(true, 20, 1, 0.2, 0.6, 0.5, 0.8, 0.05);
        assertTrue(meanVolume(0.05, eager) < meanVolume(0.5, eager) - 0.05);
    }

    private static double meanVolume(double rate, CreakTrigger.Params p) {
        CreakTrigger t = new CreakTrigger();
        Random r = new Random(9);
        double sum = 0;
        int n = 0;
        for (int i = 0; i < 20_000; i++) {
            CreakTrigger.Creak c = t.tick(rate, p, r);
            if (c != null) {
                sum += c.volume();
                n++;
            }
        }
        return sum / n;
    }
}
