package com.richardsenger.piratesnships.hazards.waves;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WaveFieldTest {

    private static final WaveField.Component SWELL = new WaveField.Component(30.0, 160.0, 1.0, 0.3, 0.0);

    @Test
    void flatSeaHasNoHeightOrSlope() {
        assertEquals(0.0, WaveField.FLAT.heightAt(12.3, -40.0, 1234));
        double[] s = WaveField.FLAT.slopeAt(1, 2, 3);
        assertEquals(0.0, s[0]);
        assertEquals(0.0, s[1]);
        assertTrue(new WaveField(-1.0, 0.0).isFlat());
    }

    @Test
    void heightNeverExceedsTheAmplitude() {
        WaveField f = new WaveField(1.2, 37.0);
        double max = 0;
        for (int i = 0; i < 20000; i++) {
            double x = (i * 7.31) % 500 - 250, z = (i * 3.17) % 400 - 200, t = i * 13.7;
            double h = f.heightAt(x, z, t);
            assertTrue(Math.abs(h) <= 1.2 + 1e-9, "height " + h);
            max = Math.max(max, Math.abs(h));
        }
        assertTrue(max > 0.7, "the trains should nearly line up somewhere: " + max);
    }

    @Test
    void oneTrainRepeatsWithItsPeriodAndWavelength() {
        WaveField f = new WaveField(0.7, 90.0, List.of(SWELL)); // runs toward +X
        for (double t : new double[] {0, 55.5, 1.0e6}) {
            double h = f.heightAt(104, 50, t);
            assertEquals(h, f.heightAt(104, 50, t + SWELL.periodTicks()), 1e-9);
            assertEquals(h, f.heightAt(104 + SWELL.wavelength(), 50, t), 1e-9);
            // across the running direction nothing changes
            assertEquals(h, f.heightAt(104, 61, t), 1e-9);
        }
    }

    @Test
    void crestsRunInTheirDirection() {
        // the phase k·x − ω·t is constant along x = c·t with c = λ / T
        double c = SWELL.wavelength() / SWELL.periodTicks();
        WaveField east = new WaveField(1.0, 90.0, List.of(SWELL));
        assertEquals(east.heightAt(3, 0, 10), east.heightAt(3 + c * 20, 0, 30), 1e-9);
        // and with the wind in any direction (WV1's anchor made the far field run along a fixed bearing; WAV7 has none)
        WaveField north = new WaveField(1.0, 0.0, List.of(SWELL));
        assertEquals(north.heightAt(5000, -7000, 10), north.heightAt(5000, -7000 - c * 20, 30), 1e-9);
        assertEquals(north.heightAt(5000, -7000, 10), north.heightAt(5100, -7000, 10), 1e-9);
    }

    @Test
    void slopeIsTheGradientOfTheHeight() {
        WaveField f = new WaveField(1.2, 210.0, WaveField.COMPONENTS, WaveField.Origin.NONE, new WaveField.Groups(0.35, 1200));
        double x = 1234.5, z = -987.25, t = 4567;
        double e = 1e-4;
        double[] g = f.slopeAt(x, z, t);
        double gx = (f.heightAt(x + e, z, t) - f.heightAt(x - e, z, t)) / (2 * e);
        double gz = (f.heightAt(x, z + e, t) - f.heightAt(x, z - e, t)) / (2 * e);
        assertEquals(gx, g[0], 1e-6);
        assertEquals(gz, g[1], 1e-6);
        assertTrue(Math.hypot(g[0], g[1]) <= f.maxSlope() + 1e-12);
    }

    @Test
    void componentsLieInTheDesignRanges() {
        double weights = 0;
        for (WaveField.Component c : WaveField.COMPONENTS) {
            assertTrue(c.wavelength() >= WaveSpectrum.MIN_WAVELENGTH && c.wavelength() <= WaveSpectrum.MAX_WAVELENGTH,
                    "wavelength " + c.wavelength());
            assertTrue(c.periodTicks() >= 5 * 20 && c.periodTicks() <= 14 * 20, "period " + c.periodTicks());
            assertTrue(Math.abs(c.offsetDeg()) <= WaveSpectrum.DIRECTION_SPREAD_DEG, "offset " + c.offsetDeg());
            weights += c.weight();
        }
        assertEquals(WaveSpectrum.DEFAULT_COMPONENTS, WaveField.COMPONENTS.size());
        assertEquals(1.0, weights, 1e-12);
    }

    @Test
    void anOriginShiftsTheFieldInTimeAndSpace() {
        WaveField.Origin o = new WaveField.Origin(1000.0, 3000.0, -1200.0);
        WaveField.Groups groups = new WaveField.Groups(0.35, 1200);
        WaveField plain = new WaveField(1.2, 70.0, WaveField.COMPONENTS, WaveField.Origin.NONE, groups);
        WaveField pinned = new WaveField(1.2, 70.0, WaveField.COMPONENTS, o, groups);
        assertEquals(WaveField.Origin.NONE, plain.origin());
        for (int i = 0; i < 50; i++) {
            double x = 3002 + i * 1.7, z = -1201 - i * 0.9, t = 1000 + i * 11;
            assertEquals(plain.heightAt(x - 3000, z + 1200, t - 1000), pinned.heightAt(x, z, t), 1e-9);
            double[] a = plain.slopeAt(x - 3000, z + 1200, t - 1000);
            double[] b = pinned.slopeAt(x, z, t);
            assertEquals(a[0], b[0], 1e-9);
            assertEquals(a[1], b[1], 1e-9);
        }
    }

    // ------------------------------------------------------------------ WAV7: the direction's crossfade

    private static final WaveField.Groups GROUPS = new WaveField.Groups(0.35, 1200);

    private static WaveField fading(WaveField.Heading heading) {
        return new WaveField(2.0, heading, WaveSpectrum.components(6, 42.0), WaveField.Origin.NONE, GROUPS);
    }

    @Test
    void aCrossfadeStartsAtTheOldSeaAndEndsAtTheNewOne() {
        WaveField.Heading heading = new WaveField.Heading(100.0, 88.0, 5000.0, 300.0);
        WaveField f = fading(heading);
        WaveField old = fading(WaveField.Heading.steady(88.0));
        WaveField now = fading(WaveField.Heading.steady(100.0));
        for (double x : new double[] {0, 3000, -2.9e7}) {
            for (double t : new double[] {4000, 4999.5, 5000}) {
                assertEquals(old.heightAt(x, 77, t), f.heightAt(x, 77, t), 0.0, "before the fade");
            }
            for (double t : new double[] {5300, 5300.5, 9000}) {
                assertEquals(now.heightAt(x, 77, t), f.heightAt(x, 77, t), 0.0, "after the fade");
            }
            double t = 5150, u = 0.5;
            assertEquals((1 - u) * old.heightAt(x, 77, t) + u * now.heightAt(x, 77, t), f.heightAt(x, 77, t), 1e-12, "half way");
        }
        assertEquals(0.0, heading.fade(5000), 0.0);
        assertEquals(0.5, heading.fade(5150), 1e-12);
        assertEquals(1.0, heading.fade(5300), 0.0);
        assertEquals(old.maxHeight(), f.maxHeight(), 0.0);
    }

    /** A crossfade never jumps: the height is continuous in time and its rate stays within the waves' own plus the fade's. */
    @Test
    void aCrossfadeNeverJumps() {
        WaveField.Heading heading = new WaveField.Heading(-5.0, 12.0, 1000.0, 300.0);
        WaveField f = fading(heading);
        // the fastest a steady sea changes: A (1 + g) Σ wᵢ ωᵢ plus the envelope's own; the fade adds at most 1.5/T × 2 × max
        double rate = 0;
        for (WaveField.Component c : f.components()) {
            rate += c.weight() * c.omega();
        }
        rate = f.maxHeight() * rate + f.amplitude() * GROUPS.depth() * 0.5 * (GROUPS.omega(0) + GROUPS.omega(1));
        double bound = rate + 1.5 / 300.0 * 2.0 * f.maxHeight();
        for (double x : new double[] {12.5, 3000, -2000, 2.9e7}) {
            double prev = f.heightAt(x, -2000, 950);
            for (double t = 950.1; t <= 1350; t += 0.1) {
                double h = f.heightAt(x, -2000, t);
                assertTrue(Math.abs(h - prev) <= bound * 0.1 + 1e-9, "jump of " + (h - prev) + " at t=" + t + ", x=" + x);
                assertTrue(Math.abs(h) <= f.maxHeight() + 1e-9, "height " + h);
                double[] g = f.slopeAt(x, -2000, t);
                assertTrue(Math.hypot(g[0], g[1]) <= f.maxSlope() + 1e-9, "slope " + Math.hypot(g[0], g[1]));
                prev = h;
            }
        }
    }

    @Test
    void theSlopeIsTheGradientDuringACrossfade() {
        WaveField f = fading(new WaveField.Heading(200.0, 185.0, 0.0, 300.0));
        double e = 1e-4;
        for (double t : new double[] {50, 150, 299}) {
            double x = 2999.5, z = -2003.25;
            double[] g = f.slopeAt(x, z, t);
            assertEquals((f.heightAt(x + e, z, t) - f.heightAt(x - e, z, t)) / (2 * e), g[0], 1e-6);
            assertEquals((f.heightAt(x, z + e, t) - f.heightAt(x, z - e, t)) / (2 * e), g[1], 1e-6);
        }
    }

    /**
     * WAV7: the phase {@code k·p} in double stays exact at the world border: the 16.7-block train's crest moves by its
     * wavelength in one period there as at the origin.
     */
    @Test
    void thePhaseStaysExactAtTheWorldBorder() {
        WaveField one = new WaveField(1.0, 90.0, List.of(SWELL));
        double far = 2.9999e7;
        for (double t : new double[] {0, 1234.5, 3.0e6}) {
            assertEquals(one.heightAt(far, 0, t), one.heightAt(far + SWELL.wavelength(), 0, t), 1e-6);
            assertEquals(one.heightAt(far, 0, t), one.heightAt(far + SWELL.wavelength() / SWELL.periodTicks() * 7, 0, t + 7), 1e-6);
            // a hundredth of a block is a hundredth of a block of phase, not a random jump
            double d = one.heightAt(far + 0.01, 0, t) - one.heightAt(far, 0, t);
            assertTrue(Math.abs(d) <= SWELL.k() * 0.01 + 1e-9, "step " + d);
        }
    }
}
