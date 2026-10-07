package com.richardsenger.piratesnships.hazards.waves;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WaveFieldTest {

    private static final WaveField.Component SWELL = new WaveField.Component(30.0, 160.0, 1.0, 0.3, 0.0, 90.0);

    @Test
    void flatSeaHasNoHeightOrSlope() {
        assertEquals(0.0, WaveField.FLAT.height(12.3, -40.0, 1234));
        double[] s = WaveField.FLAT.slope(1, 2, 3);
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
            double h = f.height(x, z, t);
            assertTrue(Math.abs(h) <= 1.2 + 1e-9, "height " + h);
            max = Math.max(max, Math.abs(h));
        }
        assertTrue(max > 1.0, "the two trains should nearly line up somewhere: " + max);
    }

    @Test
    void oneTrainRepeatsWithItsPeriodAndWavelength() {
        WaveField f = new WaveField(0.7, 90.0, List.of(SWELL)); // runs toward +X
        for (double t : new double[] {0, 55.5, 1.0e6}) {
            double h = f.heightAround(100, 50, 104, 50, t);
            assertEquals(h, f.heightAround(100, 50, 104, 50, t + SWELL.periodTicks()), 1e-9);
            assertEquals(h, f.heightAround(100, 50, 104 + SWELL.wavelength(), 50, t), 1e-9);
            // across the running direction nothing changes
            assertEquals(h, f.heightAround(100, 50, 104, 61, t), 1e-9);
        }
    }

    @Test
    void crestsRunInTheirDirection() {
        WaveField f = new WaveField(1.0, 90.0, List.of(SWELL)); // toward +X (east)
        // the phase k·x − ω·t is constant along x = c·t with c = λ / T
        double c = SWELL.wavelength() / SWELL.periodTicks();
        double h0 = f.heightAround(0, 0, 3, 0, 10);
        assertEquals(h0, f.heightAround(0, 0, 3 + c * 20, 0, 30), 1e-9);
    }

    @Test
    void slopeIsTheGradientOfTheHeight() {
        WaveField f = new WaveField(1.2, 210.0);
        double ax = 1234.5, az = -987.25, t = 4567;
        double e = 1e-4;
        double[] g = f.slopeAround(ax, az, ax + 3, az - 2, t);
        double gx = (f.heightAround(ax, az, ax + 3 + e, az - 2, t) - f.heightAround(ax, az, ax + 3 - e, az - 2, t)) / (2 * e);
        double gz = (f.heightAround(ax, az, ax + 3, az - 2 + e, t) - f.heightAround(ax, az, ax + 3, az - 2 - e, t)) / (2 * e);
        assertEquals(gx, g[0], 1e-6);
        assertEquals(gz, g[1], 1e-6);
        double[] at = f.slope(ax, az, t);
        double[] around = f.slopeAround(ax, az, ax, az, t);
        assertEquals(at[0], around[0], 1e-12);
        assertEquals(at[1], around[1], 1e-12);
        assertTrue(Math.hypot(g[0], g[1]) <= f.maxSlope() + 1e-12);
    }

    @Test
    void driftingDirectionDoesNotSweepThePhaseFarFromTheOrigin() {
        // a ship 20 000 blocks out: turning the waves by 0.02° (a few ticks of wind drift) barely moves the surface
        double ax = 20000, az = -15000;
        WaveField a = new WaveField(1.2, 90.0);
        WaveField b = new WaveField(1.2, 90.02);
        double dh = Math.abs(a.heightAround(ax, az, ax + 8, az + 3, 100) - b.heightAround(ax, az, ax + 8, az + 3, 100));
        assertTrue(dh < 0.01, "height jumped by " + dh);
        // in the middle (the anchor) nothing changes at all
        assertEquals(a.height(ax, az, 100), b.height(ax, az, 100), 1e-12);
    }

    @Test
    void movingTheAnchorIsContinuous() {
        WaveField f = new WaveField(1.2, 33.0);
        double h1 = f.heightAround(500, 500, 506, 500, 77);
        double h2 = f.heightAround(500.01, 500, 506.01, 500, 77);
        assertTrue(Math.abs(h1 - h2) < 0.01);
    }

    @Test
    void componentsLieInTheDesignRanges() {
        double weights = 0;
        for (WaveField.Component c : WaveField.COMPONENTS) {
            assertTrue(c.wavelength() >= 20 && c.wavelength() <= 40, "wavelength " + c.wavelength());
            assertTrue(c.periodTicks() >= 6 * 20 && c.periodTicks() <= 10 * 20, "period " + c.periodTicks());
            weights += c.weight();
        }
        assertEquals(1.0, weights, 1e-12);
    }
}
