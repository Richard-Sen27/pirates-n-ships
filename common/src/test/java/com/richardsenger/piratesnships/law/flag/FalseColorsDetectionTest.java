package com.richardsenger.piratesnships.law.flag;

import com.richardsenger.piratesnships.law.flag.FalseColorsDetection.Params;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FalseColorsDetectionTest {

    static final Params P = Params.defaults(); // strength 1, 0.05/s, close 16, max 96, nest x1.5 / x1.5, scale 100

    static double chance(double d, boolean nest, double score, long ticks) {
        return FalseColorsDetection.chance(P, true, d, nest, score, ticks);
    }

    @Test
    void legitimateFlagIsNeverDetected() {
        assertEquals(0.0, FalseColorsDetection.chance(P, false, 0, true, 1000, 10_000));
    }

    @Test
    void boundedToUnitInterval() {
        double[] distances = {-5, 0, 10, 16, 50, 95, 96, 143, 144, 1e9};
        double[] scores = {-10, 0, 50, 1000, 1e12};
        long[] intervals = {-20, 0, 1, 20, 1000, Long.MAX_VALUE / 4};
        for (double d : distances) for (double s : scores) for (long t : intervals) for (boolean n : new boolean[]{false, true}) {
            double c = chance(d, n, s, t);
            assertTrue(c >= 0 && c <= 1, "chance " + c);
            assertFalse(Double.isNaN(c));
        }
        assertEquals(0.0, chance(10, false, 0, 0), "no time, no detection");
        assertEquals(1.0, FalseColorsDetection.chance(P.withStrength(100), true, 0, true, 1e6, 20 * 3600), 1e-12);
    }

    @Test
    void zeroStrengthNeverDetects() {
        assertEquals(0.0, FalseColorsDetection.chance(P.withStrength(0), true, 0, true, 1000, 1000));
    }

    @Test
    void monotonicInDistance() {
        double last = 2;
        for (double d = 0; d <= 200; d += 1) {
            double c = chance(d, false, 50, 20);
            assertTrue(c <= last + 1e-15, "closer is more likely at " + d);
            last = c;
        }
        assertEquals(0.0, chance(96, false, 50, 20));
        assertTrue(chance(95, false, 50, 20) > 0);
        assertEquals(chance(0, false, 50, 20), chance(16, false, 50, 20), 1e-15, "flat inside close range");
    }

    @Test
    void crowsNestHelpsEverywhere() {
        for (double d = 0; d <= 200; d += 4) {
            assertTrue(chance(d, true, 50, 20) >= chance(d, false, 50, 20), "at " + d);
        }
        assertEquals(0.0, chance(120, false, 0, 20));
        assertTrue(chance(120, true, 0, 20) > 0, "crow's nest extends the range");
        assertTrue(chance(10, true, 0, 20) > chance(10, false, 0, 20));
    }

    @Test
    void monotonicInScore() {
        double last = -1;
        for (double s = 0; s <= 1000; s += 25) {
            double c = chance(30, false, s, 20);
            assertTrue(c > last, "higher score is more likely at " + s);
            last = c;
        }
        assertEquals(2 * FalseColorsDetection.ratePerSecond(P, 0, false, 0),
                FalseColorsDetection.ratePerSecond(P, 0, false, 100), 1e-12, "scoreScale doubles the rate");
    }

    @Test
    void monotonicInInterval() {
        double last = 0;
        for (long t = 1; t <= 2000; t += 13) {
            double c = chance(30, false, 20, t);
            assertTrue(c > last);
            last = c;
        }
    }

    @Test
    void independentOfCheckInterval() {
        // Probability of staying undetected for 60 s must not depend on how the 60 s are split into checks
        double one = 1 - chance(40, true, 70, 1200);
        double[] splits = {1, 2, 5, 20, 100, 1200};
        for (double ticks : splits) {
            long t = (long) ticks;
            double survive = Math.pow(1 - chance(40, true, 70, t), 1200.0 / t);
            assertEquals(one, survive, 1e-9, "check every " + t + " ticks");
        }
    }

    @Test
    void defaultsAreReasonable() {
        // Close range, clean record, no crow's nest: ~63 % within 20 s; at 60 points it's quicker
        assertEquals(1 - Math.exp(-1), chance(10, false, 0, 400), 1e-9);
        assertTrue(chance(10, false, 60, 400) > 0.75);
        assertTrue(chance(80, false, 0, 20) < 0.01, "far away a single second is almost safe");
    }

    @Test
    void seededRollsAreDeterministic() {
        double c = 0.3;
        int hitsA = 0, hitsB = 0;
        RandomSource a = RandomSource.create(1234), b = RandomSource.create(1234);
        for (int i = 0; i < 1000; i++) {
            boolean x = FalseColorsDetection.roll(c, a), y = FalseColorsDetection.roll(c, b);
            assertEquals(x, y);
            if (x) hitsA++;
            if (y) hitsB++;
        }
        assertEquals(hitsA, hitsB);
        assertTrue(hitsA > 250 && hitsA < 350, "about 30 %: " + hitsA);
        RandomSource r = RandomSource.create(1);
        for (int i = 0; i < 100; i++) {
            assertFalse(FalseColorsDetection.roll(0, r));
            assertTrue(FalseColorsDetection.roll(1, r));
        }
    }

    @Test
    void invalidParamsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Params(-1, 0.05, 16, 96, 1.5, 1.5, 100));
        assertThrows(IllegalArgumentException.class, () -> new Params(1, 0.05, 16, 96, 0.5, 1.5, 100));
        assertThrows(IllegalArgumentException.class, () -> new Params(1, 0.05, 16, 96, 1.5, 1.5, 0));
    }
}
