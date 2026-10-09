package com.richardsenger.piratesnships.hazards.waves.client.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.hazards.waves.WaveField;
import com.richardsenger.piratesnships.hazards.waves.WaveSpectrum;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SurfaceWavesTest {

    private static WaveField storm(WaveField.Heading heading, WaveField.Origin origin) {
        return new WaveField(1.2, heading, WaveSpectrum.components(8, 34.0), origin, new WaveField.Groups(0.35, 1200.0));
    }

    /** WAV7: the drawn height is the global field's at every point, steady or crossfading. */
    @Test
    void pointHeightIsTheGlobalField() {
        Random r = new Random(7);
        for (int k = 0; k < 40; k++) {
            double t = r.nextDouble() * 1e6;
            WaveField.Heading heading = k % 2 == 0 ? WaveField.Heading.steady(r.nextDouble() * 360)
                    : new WaveField.Heading(r.nextDouble() * 360, r.nextDouble() * 360, t - r.nextDouble() * 300, 300);
            WaveField f = storm(heading, k % 3 == 0 ? new WaveField.Origin(500, 30, -70) : WaveField.Origin.NONE);
            double cx = (r.nextDouble() - 0.5) * 20000, cz = (r.nextDouble() - 0.5) * 20000;
            SurfaceWaves w = new SurfaceWaves().prepare(f, t);
            for (int i = 0; i < 50; i++) {
                double x = cx + (r.nextDouble() - 0.5) * 600, z = cz + (r.nextDouble() - 0.5) * 600;
                assertEquals(f.heightAt(x, z, t), w.height(x, z), 1e-9);
            }
        }
    }

    @Test
    void gridMatchesPointEvaluation() {
        for (WaveField.Heading heading : new WaveField.Heading[] {WaveField.Heading.steady(211.0),
                new WaveField.Heading(211.0, 190.0, 98700.0, 300.0)}) {
            WaveField f = storm(heading, WaveField.Origin.NONE);
            SurfaceWaves w = new SurfaceWaves().prepare(f, 98765.4);
            for (int step : new int[] {1, 4, 16, 64}) {
                int n = 65;
                double[] grid = new double[n * n];
                double x0 = Math.floor(1234.5 - 32 * step), z0 = Math.floor(-876.25 - 32 * step);
                w.fillGrid(x0, z0, step, n, grid);
                for (int iz = 0; iz < n; iz++) {
                    for (int ix = 0; ix < n; ix++) {
                        assertEquals(w.height(x0 + ix * step, z0 + iz * step), grid[iz * n + ix], 1e-9, "step " + step);
                    }
                }
            }
        }
    }

    @Test
    void flatFieldIsZero() {
        SurfaceWaves w = new SurfaceWaves().prepare(WaveField.FLAT, 0);
        assertTrue(w.isFlat());
        assertEquals(0.0, w.height(3, 4));
        double[] g = new double[9];
        java.util.Arrays.fill(g, 5.0);
        w.fillGrid(0, 0, 1, 3, g);
        for (double v : g) {
            assertEquals(0.0, v);
        }
    }

    /** The visible waves run with the wind (the field's direction), everywhere. */
    @Test
    void crestsRunWithTheWaveDirection() {
        WaveField one = new WaveField(1.0, 0.0, java.util.List.of(new WaveField.Component(32, 160, 1.0, 0.0, 0.0)));
        SurfaceWaves w = new SurfaceWaves().prepare(one, 0);
        // running north (−z): constant along x, periodic along z
        assertEquals(w.height(0, 5), w.height(100, 5), 1e-9);
        assertEquals(w.height(5000, 5), w.height(-7000, 5), 1e-9);
        assertEquals(w.height(0, 5), w.height(0, 5 + 32), 1e-9);
        assertTrue(Math.abs(w.height(0, 5) - w.height(0, 5 + 16)) > 0.1);
    }
}
