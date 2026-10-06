package com.richardsenger.piratesnships.sailing.wind;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WindFieldTest {

    private static final WindParams P = WindParams.DEFAULTS;
    private static final String OW = "minecraft:overworld";
    private static final long[] SEEDS = {0L, 1L, 42L, -8_234_234_123L, 0x5EED_5EEDL};

    private static WindSample clear(WindParams p, long seed, double t) {
        return WindField.sample(p, seed, OW, t, 0.0, 0.0, 0.0, 0.0);
    }

    private static double angleDelta(double a, double b) {
        double d = Math.abs(a - b) % 360.0;
        return d > 180.0 ? 360.0 - d : d;
    }

    @Test
    void sameInputsGiveSameWind() {
        for (long seed : SEEDS) {
            assertEquals(WindField.sample(P, seed, OW, 123_456, 0.3, 0.1, 50, -70),
                    WindField.sample(P, seed, OW, 123_456, 0.3, 0.1, 50, -70));
        }
    }

    @Test
    void seedAndDimensionChangeTheWind() {
        WindSample a = clear(P, 1L, 50_000);
        WindSample b = clear(P, 2L, 50_000);
        WindSample c = WindField.sample(P, 1L, "minecraft:the_nether", 50_000, 0, 0, 0, 0);
        assertNotEquals(a.towardDegrees(), b.towardDegrees(), 1e-6);
        assertNotEquals(a.towardDegrees(), c.towardDegrees(), 1e-6);
    }

    @Test
    void directionIsUnitVectorMatchingCompassBearing() {
        for (int i = 0; i < 1000; i++) {
            WindSample s = clear(P, 42L, i * 977.0);
            assertEquals(1.0, Math.hypot(s.dirX(), s.dirZ()), 1e-9);
            assertTrue(s.towardDegrees() >= 0.0 && s.towardDegrees() < 360.0);
            double rad = Math.toRadians(s.towardDegrees());
            assertEquals(Math.sin(rad), s.dirX(), 1e-9);
            assertEquals(-Math.cos(rad), s.dirZ(), 1e-9);
        }
    }

    @Test
    void changePerTickIsBounded() {
        double dirBound = WindField.maxDirectionChangePerTick(P);
        double strBound = WindField.maxStrengthChangePerTick(P);
        assertTrue(dirBound < 0.25, "direction drift bound should be well under 0.25 deg/tick, was " + dirBound);
        assertTrue(strBound < 0.01, "strength drift bound should be under 0.01 blocks/s per tick, was " + strBound);
        for (long seed : SEEDS) {
            WindSample prev = clear(P, seed, 0);
            double maxDir = 0;
            double maxStr = 0;
            for (int t = 1; t <= 100_000; t++) {
                WindSample s = clear(P, seed, t);
                maxDir = Math.max(maxDir, angleDelta(s.towardDegrees(), prev.towardDegrees()));
                maxStr = Math.max(maxStr, Math.abs(s.strength() - prev.strength()));
                prev = s;
            }
            assertTrue(maxDir <= dirBound + 1e-9, "seed " + seed + ": direction jumped " + maxDir + " deg in a tick");
            assertTrue(maxStr <= strBound + 1e-9, "seed " + seed + ": strength jumped " + maxStr + " in a tick");
            assertTrue(maxDir > 0.0 && maxStr > 0.0, "wind should drift at all");
        }
    }

    @Test
    void overLongTimesDirectionCoversCompassAndStrengthStaysInRange() {
        double range = P.maxStrength() - P.minStrength();
        for (long seed : SEEDS) {
            int[] sectors = new int[8];
            double lo = Double.MAX_VALUE;
            double hi = -Double.MAX_VALUE;
            int n = 0;
            for (double t = 0; t < 24_000.0 * 1000; t += 600) {
                WindSample s = clear(P, seed, t);
                sectors[(int) (s.towardDegrees() / 45.0) % 8]++;
                lo = Math.min(lo, s.strength());
                hi = Math.max(hi, s.strength());
                assertTrue(s.strength() >= P.minStrength() - 1e-9 && s.strength() <= P.maxStrength() + 1e-9);
                n++;
            }
            for (int i = 0; i < 8; i++) {
                assertTrue(sectors[i] > n * 0.05, "seed " + seed + ": sector " + i + " got only " + sectors[i] + " of " + n);
            }
            assertTrue(lo < P.minStrength() + 0.25 * range, "seed " + seed + ": calm winds never happen, min " + lo);
            assertTrue(hi > P.maxStrength() - 0.25 * range, "seed " + seed + ": strong winds never happen, max " + hi);
        }
    }

    @Test
    void zeroVariabilityFreezesTheWind() {
        WindParams frozen = P.withVariability(0.0);
        assertEquals(clear(frozen, 7L, 0), clear(frozen, 7L, 5_000_000));
    }

    @Test
    void weatherMultipliers() {
        assertEquals(1.0, WindField.weatherMultiplier(P, 0, 0), 1e-12);
        assertEquals(1.5, WindField.weatherMultiplier(P, 1, 0), 1e-12);
        assertEquals(2.2, WindField.weatherMultiplier(P, 1, 1), 1e-12);
        WindSample clear = WindField.sample(P, 3L, OW, 1000, 0, 0, 0, 0);
        WindSample rain = WindField.sample(P, 3L, OW, 1000, 1, 0, 0, 0);
        assertEquals(clear.strength() * 1.5, rain.strength(), 1e-9);
        assertEquals(clear.towardDegrees(), rain.towardDegrees(), 1e-9);
        assertEquals(0.0, rain.gust());
    }

    @Test
    void weatherChangeIsSmoothWithVanillaRamps() {
        // Vanilla moves rain and thunder levels by 0.01 per tick; thunder never exceeds rain.
        double prev = WindField.weatherMultiplier(P, 0, 0);
        double maxStep = 0;
        for (int i = 1; i <= 200; i++) {
            double rain = Math.min(1.0, i * 0.01);
            double thunder = Math.max(0.0, Math.min(rain, (i - 100) * 0.01));
            double m = WindField.weatherMultiplier(P, rain, thunder);
            assertTrue(m >= prev - 1e-12, "multiplier should rise monotonically while weather worsens");
            maxStep = Math.max(maxStep, m - prev);
            prev = m;
        }
        assertEquals(2.2, prev, 1e-9);
        assertTrue(maxStep <= 0.012 + 1e-9, "weather step per tick too large: " + maxStep);
    }

    @Test
    void weatherCanBeSwitchedOff() {
        WindParams off = P.withWeatherAffectsWind(false);
        WindSample storm = WindField.sample(off, 3L, OW, 1000, 1, 1, 0, 0);
        assertEquals(1.0, storm.weatherMultiplier());
        assertEquals(clear(off, 3L, 1000).strength(), storm.strength(), 1e-9);
    }

    @Test
    void noGustsWithoutThunder() {
        for (double t = 0; t < 200_000; t += 3) {
            assertEquals(0.0, WindField.sample(P, 11L, OW, t, 0, 0, 0, 0).gust());
            assertEquals(0.0, WindField.sample(P, 11L, OW, t, 1, 0, 0, 0).gust());
        }
    }

    @Test
    void stormsHaveShortBoundedContinuousGusts() {
        int gusts = 0;
        boolean inGust = false;
        double prevGust = 0;
        double prevDir = WindField.sample(P, 11L, OW, 0, 1, 1, 0, 0).towardDegrees();
        double maxGustStep = 0;
        double minutes = 200;
        int gustTicks = 0;
        int longest = 0;
        int current = 0;
        for (int t = 1; t <= 1200 * minutes; t++) {
            WindSample s = WindField.sample(P, 11L, OW, t, 1, 1, 0, 0);
            assertTrue(s.gust() >= 0.0 && s.gust() <= 1.0);
            maxGustStep = Math.max(maxGustStep, Math.abs(s.gust() - prevGust));
            assertTrue(angleDelta(s.towardDegrees(), prevDir) < 2.0, "gust direction shift must not jump");
            if (s.gusting()) {
                gustTicks++;
                current++;
                longest = Math.max(longest, current);
                if (!inGust) {
                    gusts++;
                }
            } else {
                current = 0;
            }
            inGust = s.gusting();
            prevGust = s.gust();
            prevDir = s.towardDegrees();
        }
        double expected = minutes * P.gustsPerMinute() * WindField.GUST_CHANCE;
        assertTrue(gusts > expected * 0.7 && gusts < expected * 1.3, "gust count " + gusts + ", expected about " + expected);
        assertTrue(longest <= WindField.GUST_MAX_TICKS, "gusts must be short bursts, longest " + longest);
        assertTrue(gustTicks < 1200 * minutes * 0.5, "gusts must not blow most of the time");
        assertTrue(maxGustStep <= Math.PI / WindField.GUST_MIN_TICKS + 1e-9, "gust intensity jumped " + maxGustStep);
    }

    @Test
    void gustsRaiseStrengthAndShiftDirectionWithinLimits() {
        double maxShift = 0;
        boolean stronger = false;
        for (int t = 0; t < 1200 * 30; t++) {
            WindSample storm = WindField.sample(P, 5L, OW, t, 1, 1, 0, 0);
            WindParams noGust = P.withGustsEnabled(false);
            WindSample calm = WindField.sample(noGust, 5L, OW, t, 1, 1, 0, 0);
            assertEquals(0.0, calm.gust());
            assertTrue(storm.strength() >= calm.strength() - 1e-9);
            assertTrue(storm.strength() <= calm.strength() * (1 + P.gustStrength()) + 1e-9);
            maxShift = Math.max(maxShift, angleDelta(storm.towardDegrees(), calm.towardDegrees()));
            stronger |= storm.strength() > calm.strength() * 1.1;
        }
        assertTrue(stronger, "some gust should add at least 10%");
        assertTrue(maxShift <= P.gustDirectionShiftDeg() + 1e-9);
        assertTrue(maxShift > 1.0, "gusts should shift the direction a little");
    }

    @Test
    void regionalVariationOffMeansSameWindEverywhere() {
        WindSample a = WindField.sample(P, 9L, OW, 77_000, 0, 0, 0, 0);
        WindSample b = WindField.sample(P, 9L, OW, 77_000, 0, 0, 123_456, -98_765);
        assertEquals(a, b);
    }

    @Test
    void regionalVariationIsSmoothAndBounded() {
        WindParams on = P.withRegionalVariation(true);
        WindSample base = WindField.sample(P, 9L, OW, 77_000, 0, 0, 0, 0);
        double maxNeighbour = 0;
        boolean differs = false;
        for (int i = 0; i < 2000; i++) {
            double x = i * 517.0 - 400_000;
            double z = i * -311.0 + 90_000;
            WindSample s = WindField.sample(on, 9L, OW, 77_000, 0, 0, x, z);
            WindSample n = WindField.sample(on, 9L, OW, 77_000, 0, 0, x + 1, z);
            maxNeighbour = Math.max(maxNeighbour, angleDelta(s.towardDegrees(), n.towardDegrees()));
            assertTrue(angleDelta(s.towardDegrees(), base.towardDegrees()) <= on.regionalDirectionDeg() + 1e-9);
            double ratio = s.strength() / base.strength();
            assertTrue(ratio >= 1 - on.regionalStrengthFraction() - 1e-9 && ratio <= 1 + on.regionalStrengthFraction() + 1e-9);
            differs |= angleDelta(s.towardDegrees(), base.towardDegrees()) > 10.0;
        }
        assertTrue(differs, "regions should differ noticeably");
        assertTrue(maxNeighbour < 0.5, "neighbouring blocks should have nearly the same wind, max " + maxNeighbour);
    }

    @Test
    void sampleHelpers() {
        WindSample east = WindSample.of(90, 5, 1, 0);
        assertEquals(270.0, east.fromDegrees(), 1e-9);
        Vector3d v = east.velocity(new Vector3d());
        assertEquals(5.0, v.x, 1e-9);
        assertEquals(0.0, v.z, 1e-9);
        assertEquals(350.0, WindSample.normalizeDegrees(-10), 1e-9);
        assertEquals(10.0, WindSample.normalizeDegrees(730), 1e-9);
        assertFalse(east.gusting());
    }

    @Test
    void noiseStaysInRangeAndIsContinuous() {
        double prev = WindNoise.fbm1(1L, 1, 0);
        for (int i = 1; i < 100_000; i++) {
            double x = i * 0.001;
            double v = WindNoise.fbm1(1L, 1, x);
            assertTrue(v >= -1 && v <= 1);
            assertTrue(Math.abs(v - prev) <= WindNoise.FBM_MAX_SLOPE * 0.001 + 1e-12);
            prev = v;
            double w = WindNoise.fbm2(1L, 1, x * 3, -x * 7);
            assertTrue(w >= -1 && w <= 1);
        }
    }
}
