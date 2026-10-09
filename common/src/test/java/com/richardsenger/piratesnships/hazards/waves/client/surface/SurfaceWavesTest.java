package com.richardsenger.piratesnships.hazards.waves.client.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.hazards.waves.WaveField;
import com.richardsenger.piratesnships.hazards.waves.WaveSpectrum;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SurfaceWavesTest {

    private static WaveField storm(double direction, WaveField.Origin origin) {
        return new WaveField(1.2, direction, WaveSpectrum.components(8, 34.0), origin, new WaveField.Groups(0.35, 1200.0));
    }

    @Test
    void pointHeightIsTheAnchoredField() {
        Random r = new Random(7);
        for (int k = 0; k < 40; k++) {
            WaveField f = storm(r.nextDouble() * 360, k % 3 == 0 ? new WaveField.Origin(500, 30, -70) : WaveField.Origin.NONE);
            double ax = (r.nextDouble() - 0.5) * 20000, az = (r.nextDouble() - 0.5) * 20000, t = r.nextDouble() * 1e6;
            SurfaceWaves w = new SurfaceWaves().prepare(f, ax, az, t);
            for (int i = 0; i < 50; i++) {
                double x = ax + (r.nextDouble() - 0.5) * 600, z = az + (r.nextDouble() - 0.5) * 600;
                assertEquals(f.heightAround(ax, az, x, z, t), w.height(x, z), 1e-9);
            }
            // at the anchor it is the field's own height there (what a ship there feels)
            assertEquals(f.height(ax, az, t), w.height(ax, az), 1e-9);
        }
    }

    @Test
    void gridMatchesPointEvaluation() {
        WaveField f = storm(211.0, WaveField.Origin.NONE);
        SurfaceWaves w = new SurfaceWaves().prepare(f, 1234.5, -876.25, 98765.4);
        for (int step : new int[] {1, 4, 16, 64}) {
            int n = 65;
            double[] grid = new double[n * n];
            double x0 = 1234.5 - 32 * step, z0 = -876.25 - 32 * step;
            x0 = Math.floor(x0);
            z0 = Math.floor(z0);
            w.fillGrid(x0, z0, step, n, grid);
            for (int iz = 0; iz < n; iz++) {
                for (int ix = 0; ix < n; ix++) {
                    assertEquals(w.height(x0 + ix * step, z0 + iz * step), grid[iz * n + ix], 1e-9, "step " + step);
                }
            }
        }
    }

    @Test
    void flatFieldIsZero() {
        SurfaceWaves w = new SurfaceWaves().prepare(WaveField.FLAT, 0, 0, 0);
        assertTrue(w.isFlat());
        assertEquals(0.0, w.height(3, 4));
        double[] g = new double[9];
        java.util.Arrays.fill(g, 5.0);
        w.fillGrid(0, 0, 1, 3, g);
        for (double v : g) {
            assertEquals(0.0, v);
        }
    }

    /** The visible waves run with the wind (the field's direction), not along the trains' fixed reference bearing. */
    @Test
    void crestsRunWithTheWaveDirection() {
        WaveField one = new WaveField(1.0, 0.0, java.util.List.of(new WaveField.Component(32, 160, 1.0, 0.0, 0.0, 90.0)));
        SurfaceWaves w = new SurfaceWaves().prepare(one, 0, 0, 0);
        // running north (−z): constant along x, periodic along z
        assertEquals(w.height(0, 5), w.height(100, 5), 1e-9);
        assertEquals(w.height(0, 5), w.height(0, 5 + 32), 1e-9);
        assertTrue(Math.abs(w.height(0, 5) - w.height(0, 5 + 16)) > 0.1);
    }
}
