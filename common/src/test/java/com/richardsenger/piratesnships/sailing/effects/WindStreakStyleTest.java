package com.richardsenger.piratesnships.sailing.effects;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** The natural look of the wind streaks (WD2): motion variety, puffs, the deck-height bias, the fade and the sprites. */
class WindStreakStyleTest {

    private static final double EPS = 1e-9;
    private static final WindStreakStyle S = WindStreakStyle.DEFAULTS;
    private static final Path PARTICLES = Path.of("src/main/resources/assets/pirates_n_ships/textures/particle");

    // ---- each streak ----

    @Test
    void defaultsMatchTheDecision() {
        assertEquals(0.3, S.speedSpread(), EPS, "0.7 to 1.3 times the wind");
        assertEquals(8.0, S.headingJitterDegrees(), EPS);
        assertEquals(0.25, S.wobble(), EPS);
        assertEquals(0.05, S.wobble() * WindStreakStyle.WOBBLE_MIN_FACTOR, EPS, "wobble 0.05 to 0.25");
        assertTrue(S.heightPeak() >= 2.0 && S.heightPeak() <= 4.0, "deck height");
        assertEquals(3, S.puffMinSize());
        assertEquals(6, S.puffSize());
        assertEquals(2.0, S.puffSpread(), EPS);
        assertEquals(5, S.puffTicks());
        int life = WindStreakRules.DEFAULTS.lifeTicks();
        assertEquals(25.0, WindStreakStyle.LIFE_MIN_FACTOR * life, EPS);
        assertEquals(60.0, WindStreakStyle.LIFE_MAX_FACTOR * life, EPS);
    }

    @Test
    void streaksVaryWithinTheirBounds() {
        Random r = new Random(11);
        double minSpeed = 9, maxSpeed = 0, minHead = 99, maxHead = -99, minAmp = 9, maxAmp = 0;
        int minLife = 999, maxLife = 0;
        for (int i = 0; i < 50_000; i++) {
            WindStreakStyle.Streak s = S.streak(40, r.nextDouble(), r.nextDouble(), r::nextDouble);
            assertTrue(s.speedFactor() >= 0.7 - EPS && s.speedFactor() <= 1.3 + EPS, "speed " + s.speedFactor());
            assertTrue(Math.abs(s.headingOffsetDegrees()) <= 8.0 + EPS, "heading " + s.headingOffsetDegrees());
            assertTrue(s.lifeTicks() >= 25 && s.lifeTicks() <= 60, "life " + s.lifeTicks());
            assertTrue(s.wobbleAmplitude() >= 0.05 - EPS && s.wobbleAmplitude() <= 0.25 + EPS, "wobble " + s.wobbleAmplitude());
            assertTrue(s.wobblePhase() >= 0.0 && s.wobblePhase() < 2 * Math.PI);
            assertTrue(s.variant() >= 0 && s.variant() < WindStreakStyle.VARIANTS);
            minSpeed = Math.min(minSpeed, s.speedFactor());
            maxSpeed = Math.max(maxSpeed, s.speedFactor());
            minHead = Math.min(minHead, s.headingOffsetDegrees());
            maxHead = Math.max(maxHead, s.headingOffsetDegrees());
            minLife = Math.min(minLife, s.lifeTicks());
            maxLife = Math.max(maxLife, s.lifeTicks());
            minAmp = Math.min(minAmp, s.wobbleAmplitude());
            maxAmp = Math.max(maxAmp, s.wobbleAmplitude());
        }
        // the whole range is used, not one value
        assertEquals(0.7, minSpeed, 0.01);
        assertEquals(1.3, maxSpeed, 0.01);
        assertEquals(-8.0, minHead, 0.1);
        assertEquals(8.0, maxHead, 0.1);
        assertEquals(25, minLife);
        assertEquals(60, maxLife);
        assertEquals(0.05, minAmp, 0.01);
        assertEquals(0.25, maxAmp, 0.01);
    }

    @Test
    void outOfRangeInputsStayInBounds() {
        double[] seq = {Double.NaN, -3.0, 7.0, 1.0};
        int[] i = {0};
        WindStreakStyle.Streak s = S.streak(40, 5.0, -2.0, () -> seq[i[0]++ % seq.length]);
        assertTrue(s.speedFactor() <= 1.3 + EPS && s.speedFactor() >= 0.7 - EPS);
        assertTrue(Math.abs(s.headingOffsetDegrees()) <= 8.0 + EPS);
        assertTrue(s.lifeTicks() >= 25 && s.lifeTicks() <= 60);
    }

    @Test
    void streaksAreDeterministicGivenTheRandomInputs() {
        Random a = new Random(5), b = new Random(5);
        for (int i = 0; i < 1000; i++) {
            assertEquals(S.streak(40, a.nextDouble(), a.nextDouble(), a::nextDouble),
                    S.streak(40, b.nextDouble(), b.nextDouble(), b::nextDouble));
        }
    }

    @Test
    void spritesArePickedByTheirWeightsWithTheCurlAnAccent() {
        int[] hits = new int[WindStreakStyle.VARIANTS];
        int n = 100_000;
        Random r = new Random(9);
        for (int i = 0; i < n; i++) {
            hits[WindStreakStyle.variant(r.nextDouble())]++;
        }
        double sum = 0;
        for (int v = 0; v < hits.length; v++) {
            assertEquals(WindStreakStyle.VARIANT_WEIGHTS[v], (double) hits[v] / n, 0.01, "variant " + v);
            sum += WindStreakStyle.VARIANT_WEIGHTS[v];
        }
        assertEquals(1.0, sum, EPS);
        assertEquals(0, WindStreakStyle.variant(0.0));
        assertEquals(WindStreakStyle.VARIANTS - 1, WindStreakStyle.variant(Math.nextDown(1.0)));
        assertTrue(hits[3] < hits[0], "the whoosh is rarer than a plain stroke");
    }

    @Test
    void wobbleIsGentleAndMostlyVertical() {
        double amp = 0.2, phase = 1.1;
        double maxSide = 0, maxUp = 0;
        for (double f = 0; f <= 1.0; f += 0.01) {
            double[] o = WindStreakStyle.wobbleOffset(amp, phase, f);
            maxSide = Math.max(maxSide, Math.abs(o[0]));
            maxUp = Math.max(maxUp, Math.abs(o[1]));
        }
        assertEquals(amp, maxUp, 0.002, "vertical swing = amplitude");
        assertEquals(WindStreakStyle.WOBBLE_SIDEWAYS * amp, maxSide, 0.002);
        assertTrue(maxSide < maxUp);
        assertArrayEquals(WindStreakStyle.wobbleOffset(amp, phase, 0.0), WindStreakStyle.wobbleOffset(amp, phase, 1.0), 1e-9,
                "one whole period over the life");
        assertArrayEquals(new double[] {0, 0}, WindStreakStyle.wobbleOffset(0.0, phase, 0.3), EPS, "no wobble");
    }

    @Test
    void theStrokeStaysUprightFromEitherSide() {
        double ax = Math.sin(Math.toRadians(30)), az = -Math.cos(Math.toRadians(30));
        double[][] views = {{5, 2, -3}, {-5, 2, 3}, {-4, -1, 7}, {4, -1, -7}, {3, 0.5, 1}, {-3, 0.5, -1}};
        for (double[] p : views) {
            double[] s = WindStreakRules.uprightSide(ax, az, p[0], p[1], p[2]);
            double[] f = WindStreakRules.facingSide(ax, az, p[0], p[1], p[2]);
            assertTrue(s[1] >= 0.0, "never points down");
            assertEquals(1.0, Math.abs(s[0] * f[0] + s[1] * f[1] + s[2] * f[2]), 1e-9, "the same line as the facing side");
            assertEquals(0.0, s[0] * p[0] + s[1] * p[1] + s[2] * p[2], 1e-9, "still faces the camera");
        }
    }

    // ---- height ----

    @Test
    void heightPeaksAtDeckHeightWithATailIntoTheRigging() {
        WindStreakRules w = WindStreakRules.DEFAULTS;
        double sea = 63, peak = S.heightPeak();
        int[] bins = new int[12];
        Random r = new Random(4);
        int n = 200_000;
        double sum = 0;
        for (int i = 0; i < n; i++) {
            double h = w.y(sea, peak, r.nextDouble()) - sea;
            assertTrue(h >= WindStreakRules.BOTTOM - EPS && h <= w.height() + EPS, "height " + h);
            bins[(int) Math.min(11, Math.floor(h))]++;
            sum += h;
        }
        int best = 0;
        for (int b = 1; b < bins.length; b++) {
            if (bins[b] > bins[best]) best = b;
        }
        assertTrue(best == 2 || best == 3, "the densest block is around the peak, was " + best);
        for (int b = 4; b < 11; b++) {
            assertTrue(bins[b] > bins[b + 1], "thinning upward at " + b);
        }
        assertTrue(bins[11] > 0, "a few still reach the rigging");
        assertEquals((WindStreakRules.BOTTOM + w.height() + peak) / 3.0, sum / n, 0.03, "triangular mean");
        assertEquals(sea + peak, w.y(sea, peak, (peak - 1.0) / (w.height() - 1.0)), 1e-9, "mode at the peak's quantile");
    }

    @Test
    void triangularClampsThePeakAndIsMonotonic() {
        assertEquals(1.0, SpawnRules.triangular(1, 12, -5, 0.0), EPS);
        assertEquals(12.0, SpawnRules.triangular(1, 12, 50, 1.0), EPS);
        assertEquals(4.0, SpawnRules.triangular(4, 4, 4, 0.7), EPS, "an empty band");
        double last = -1;
        for (double u = 0; u < 1.0; u += 0.001) {
            double v = SpawnRules.triangular(1, 12, 3, u);
            assertTrue(v >= last);
            last = v;
        }
    }

    // ---- fade and opacity ----

    @Test
    void fadesInFastAndOutSlowly() {
        double life = 40;
        assertEquals(0.0, WindStreakStyle.fade(0, life), EPS);
        assertEquals(0.0, WindStreakStyle.fade(life, life), EPS);
        assertEquals(1.0, WindStreakStyle.fade(life * WindStreakStyle.FADE_IN, life), EPS, "in after 15 %");
        assertEquals(1.0, WindStreakStyle.fade(life * (1 - WindStreakStyle.FADE_OUT), life), EPS, "holds until the slow out");
        assertEquals(0.0, WindStreakStyle.fade(-1, life), EPS);
        assertEquals(0.0, WindStreakStyle.fade(life + 1, life), EPS);
        assertEquals(0.5, WindStreakStyle.fade(life * WindStreakStyle.FADE_IN / 2, life), 1e-9);
        assertEquals(0.5, WindStreakStyle.fade(life * (1 - WindStreakStyle.FADE_OUT / 2), life), 1e-9);
        // rising, then falling
        double last = -1, peakAge = life * WindStreakStyle.FADE_IN;
        for (double a = 0; a <= life; a += 0.25) {
            double f = WindStreakStyle.fade(a, life);
            if (a <= peakAge) assertTrue(f >= last, "rising at " + a);
            else assertTrue(f <= last + EPS, "not rising at " + a);
            last = f;
        }
        // the way out is much slower than the way in: the same distance from either end, more is left on the way out
        assertTrue(WindStreakStyle.FADE_OUT > 3 * WindStreakStyle.FADE_IN);
        assertTrue(WindStreakStyle.fade(life - 4, life) < WindStreakStyle.fade(4, life), "in: full after 6 ticks");
        assertTrue(WindStreakStyle.fade(life * 0.7, life) > 0.45 && WindStreakStyle.fade(life * 0.1, life) > 0.7);
    }

    @Test
    void opacityGrowsWithTheWind() {
        assertEquals(S.opacity() * WindStreakStyle.FAINT_SHARE, S.opacity(4.0), EPS, "faint at 4 blocks/s");
        assertEquals(S.opacity() * WindStreakStyle.FAINT_SHARE, S.opacity(1.0), EPS);
        assertEquals(S.opacity(), S.opacity(12.0), EPS, "full at 12 blocks/s");
        assertEquals(S.opacity(), S.opacity(30.0), EPS);
        double last = 0;
        for (double s = 4; s <= 12; s += 0.5) {
            double o = S.opacity(s);
            assertTrue(o >= last);
            last = o;
        }
        assertTrue(S.opacity(6.0) < S.opacity(12.0) * 0.6, "a moderate wind is clearly fainter than a strong one");
    }

    // ---- puffs ----

    @Test
    void puffsKeepTheMeanRate() {
        for (double rate : new double[] {0.3, 0.96, 3.1}) {
            for (double gust : new double[] {0.0, 0.5, 1.0}) {
                WindPuffs puffs = new WindPuffs();
                Random r = new Random(Double.hashCode(rate) * 31 + Double.hashCode(gust));
                long[] count = {0};
                int ticks = 200_000;
                for (int t = 0; t < ticks; t++) {
                    puffs.tick(rate, gust, S, r::nextDouble, () -> new double[] {0, 65, 0}, s -> count[0]++);
                }
                assertEquals(rate, (double) count[0] / ticks, rate * 0.02, "mean for rate " + rate + " gust " + gust);
                assertTrue(puffs.pendingCount() <= S.puffSize() * S.puffTicks() * 10, "nothing piles up");
            }
        }
    }

    @Test
    void puffsAreLooseGroupsOverAFewTicks() {
        WindPuffs puffs = new WindPuffs();
        Random r = new Random(21);
        int[] next = {0};
        Map<Integer, List<double[]>> groups = new HashMap<>(); // centre index -> {dx, dy, dz, tick}
        double[] tick = {0};
        for (int t = 0; t < 20_000; t++) {
            tick[0] = t;
            puffs.tick(0.4, 1.0, S, r::nextDouble, () -> {
                int id = next[0]++;
                return new double[] {id * 1000.0, 65.0, 0.0}; // far apart, so each spawn maps back to its centre
            }, s -> {
                assertTrue(s.inPuff(), "a gust's peak brings only puffs");
                int id = (int) Math.round(s.x() / 1000.0);
                groups.computeIfAbsent(id, k -> new ArrayList<>()).add(new double[] {s.x() - id * 1000.0, s.y() - 65.0, s.z(), tick[0]});
            });
        }
        assertTrue(groups.size() > 1000, "puffs were born");
        int[] sizes = new int[S.puffSize() + 1];
        for (Map.Entry<Integer, List<double[]>> e : groups.entrySet()) {
            List<double[]> g = e.getValue();
            if (e.getKey() >= next[0] - 2) continue; // the last puffs may still be being born
            assertTrue(g.size() >= S.puffMinSize() && g.size() <= S.puffSize(), "puff size " + g.size());
            sizes[g.size()]++;
            double first = Double.MAX_VALUE, last = -1;
            for (double[] m : g) {
                assertTrue(Math.hypot(m[0], m[2]) <= S.puffSpread() + EPS, "within the spread");
                assertTrue(Math.abs(m[1]) <= S.puffSpread() * 0.25 + EPS, "a flat cluster");
                first = Math.min(first, m[3]);
                last = Math.max(last, m[3]);
            }
            assertTrue(last - first < S.puffTicks(), "born within the puff's ticks");
        }
        for (int size = S.puffMinSize(); size <= S.puffSize(); size++) {
            assertTrue(sizes[size] > 0, "puffs of " + size + " occur");
        }
    }

    @Test
    void aSteadyWindTricklesBetweenThePuffs() {
        WindPuffs puffs = new WindPuffs();
        Random r = new Random(8);
        long[] inPuff = {0}, total = {0};
        for (int t = 0; t < 100_000; t++) {
            puffs.tick(1.0, 0.0, S, r::nextDouble, () -> new double[] {0, 65, 0}, s -> {
                total[0]++;
                if (s.inPuff()) inPuff[0]++;
            });
        }
        assertEquals(S.puffShare(), (double) inPuff[0] / total[0], 0.02, "the configured share comes in puffs");
        assertEquals(S.puffShare(), S.puffShare(0.0), EPS);
        assertEquals(1.0, S.puffShare(1.0), EPS);
        assertEquals(1.0, S.puffShare(7.0), EPS, "gust clamped");
    }

    @Test
    void puffMembersFlyCloseTogether() {
        Random r = new Random(3);
        for (int i = 0; i < 10_000; i++) {
            double g = r.nextDouble(), own = r.nextDouble();
            double c = WindStreakStyle.cohere(g, own);
            assertTrue(c >= 0.0 && c < 1.0);
            assertTrue(Math.abs(c - g) <= WindStreakStyle.PUFF_COHESION / 2 + EPS);
        }
    }

    @Test
    void puffsAreDeterministicGivenTheRandomInputs() {
        List<WindPuffs.Spawn> a = run(42), b = run(42);
        assertFalse(a.isEmpty());
        assertEquals(a, b);
    }

    @Test
    void clearForgetsUnbornPuffs() {
        WindPuffs puffs = new WindPuffs();
        Random r = new Random(1);
        puffs.tick(20.0, 1.0, S, r::nextDouble, () -> new double[] {0, 65, 0}, s -> { });
        assertTrue(puffs.pendingCount() > 0);
        puffs.clear();
        assertEquals(0, puffs.pendingCount());
        int[] n = {0};
        puffs.tick(0.0, 0.0, S, r::nextDouble, () -> new double[] {0, 65, 0}, s -> n[0]++);
        assertEquals(0, n[0], "no rate and nothing pending: nothing");
    }

    private static List<WindPuffs.Spawn> run(long seed) {
        WindPuffs puffs = new WindPuffs();
        Random r = new Random(seed);
        List<WindPuffs.Spawn> out = new ArrayList<>();
        for (int t = 0; t < 500; t++) {
            puffs.tick(1.5, (t % 100) / 100.0, S, r::nextDouble, () -> new double[] {r.nextDouble(), 65, r.nextDouble()}, out::add);
        }
        return out;
    }

    // ---- sprites ----

    @Test
    void strokeSpritesExistTaperedAndFainterAtTheTail() throws IOException {
        assertFalse(Files.exists(PARTICLES.resolve("wind_streak.png")), "WD1's single wisp is gone");
        for (int v = 0; v < WindStreakStyle.VARIANTS; v++) {
            Path file = PARTICLES.resolve("wind_streak_" + v + ".png");
            assertTrue(Files.isRegularFile(file), file + " exists (tools/gen_sea_effects_textures.py)");
            BufferedImage img = ImageIO.read(file.toFile());
            assertEquals(64, img.getWidth(), file.toString());
            assertEquals(16, img.getHeight(), file.toString());
            double tail = columnAlpha(img, 4, 20), head = columnAlpha(img, 34, 56);
            assertTrue(tail > 0, "a visible tail in " + file);
            assertTrue(head > 2 * tail, "tail thinner and fainter than the head in " + file);
        }
        // the whoosh curls: its head reaches well above the line of its tail
        BufferedImage curl = ImageIO.read(PARTICLES.resolve("wind_streak_3.png").toFile());
        assertTrue(topRow(curl, 40, 60) < topRow(curl, 2, 20) - 6, "the whoosh curls up at the head");
    }

    private static double columnAlpha(BufferedImage img, int x0, int x1) {
        double sum = 0;
        for (int x = x0; x < x1; x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                sum += (img.getRGB(x, y) >>> 24) / 255.0;
            }
        }
        return sum / (x1 - x0);
    }

    private static int topRow(BufferedImage img, int x0, int x1) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = x0; x < x1; x++) {
                if ((img.getRGB(x, y) >>> 24) > 40) return y;
            }
        }
        return img.getHeight();
    }
}
