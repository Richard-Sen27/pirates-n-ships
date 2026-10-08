package com.richardsenger.piratesnships.ship.decor.flag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ripple the flag renderer draws (ART3): strips, swing, travel direction, normals. */
class FlagRippleTest {

    private static final int N = FlagRipple.STRIPS + 1;
    private static final float EPS = 1.0e-5f;

    @Test
    void stripsCoverTheWholeClothWithItsTextureColumns() {
        assertEquals(FlagClothModel.HOIST_Z, FlagRipple.z(0), EPS);
        assertEquals(FlagClothModel.TIP_Z, FlagRipple.z(FlagRipple.STRIPS), EPS);
        assertEquals(0f, FlagRipple.u(0), EPS);
        assertEquals(FlagClothModel.CLOTH_U1, FlagRipple.u(FlagRipple.STRIPS), EPS);
        assertEquals(0, FlagClothModel.LENGTH % FlagRipple.STRIPS, "whole texture columns per strip");
        for (int k = 0; k < N; k++) {
            // 1 texel per model pixel along the cloth, as FlagClothModel maps it
            assertEquals(-FlagRipple.z(k) * 16f, FlagRipple.u(k) * FlagClothModel.TEXTURE_WIDTH, 1.0e-4f, "strip " + k);
        }
    }

    @Test
    void theHoistStaysOnThePoleAndTheSwingGrowsToTheTip() {
        float a = FlagRipple.MAX_AMPLITUDE;
        float[] x = new float[N], nx = new float[N], nz = new float[N];
        float maxTip = 0f;
        for (int t = 0; t < 200; t++) {
            FlagRipple.fill(t * 0.37, 1.3f, a, x, nx, nz);
            assertEquals(0f, x[0], EPS, "hoist on the pole");
            for (int k = 0; k < N; k++) {
                assertTrue(Math.abs(x[k]) <= a * FlagRipple.along(k) + EPS, "swing within the envelope at " + k);
            }
            maxTip = Math.max(maxTip, Math.abs(x[N - 1]));
        }
        assertTrue(maxTip > 0.95f * a, "the tip reaches the full swing: " + maxTip);
        assertTrue(a < 2f / 16f, "a slight ripple, within 2 px");
    }

    @Test
    void theWaveTravelsFromThePoleToTheTip() {
        double t0 = 1000.0;
        float phase = 0.4f;
        // on the drawn offsets: the pure wave x / s at strip k, one strip-time later, equals the wave at strip k - 1 now
        float[] x = new float[N], nx = new float[N], nz = new float[N], later = new float[N];
        FlagRipple.fill(t0, phase, 1f, x, nx, nz);
        FlagRipple.fill(t0 + FlagRipple.PERIOD_TICKS * FlagRipple.WAVES / FlagRipple.STRIPS, phase, 1f, later, nx, nz);
        for (int k = 2; k < N; k++) {
            assertEquals(x[k - 1] / FlagRipple.along(k - 1), later[k] / FlagRipple.along(k), 1.0e-4f, "strip " + k);
        }
    }

    @Test
    void normalsAreUnitAndPerpendicularToTheCloth() {
        float[] x = new float[N], nx = new float[N], nz = new float[N];
        for (int t = 0; t < 50; t++) {
            FlagRipple.fill(t * 1.7, 2.1f, FlagRipple.MAX_AMPLITUDE, x, nx, nz);
            for (int k = 0; k < N; k++) {
                assertEquals(1f, nx[k] * nx[k] + nz[k] * nz[k], 1.0e-4f, "unit at " + k);
                assertTrue(nx[k] > 0.8f, "the front still faces +x: " + nx[k]);
            }
            for (int k = 1; k < N - 1; k++) {
                // central difference of the drawn cloth vs. the normal at k
                float dx = x[k + 1] - x[k - 1], dz = FlagRipple.z(k + 1) - FlagRipple.z(k - 1);
                float dot = (dx * nx[k] + dz * nz[k]) / (float) Math.hypot(dx, dz);
                assertEquals(0f, dot, 0.08f, "normal across the cloth at " + k);
            }
        }
    }

    @Test
    void theSwingFollowsTheWindAndNeighboursDoNotWaveInStep() {
        assertEquals(FlagRipple.MIN_AMPLITUDE, FlagRipple.amplitude(0.0), EPS);
        assertEquals(FlagRipple.MIN_AMPLITUDE, FlagRipple.amplitude(Double.NaN), EPS);
        assertEquals(FlagRipple.MAX_AMPLITUDE, FlagRipple.amplitude(FlagRipple.FULL_WIND), EPS);
        assertEquals(FlagRipple.MAX_AMPLITUDE, FlagRipple.amplitude(40.0), EPS);
        assertTrue(FlagRipple.amplitude(6.0) > FlagRipple.MIN_AMPLITUDE && FlagRipple.amplitude(6.0) < FlagRipple.MAX_AMPLITUDE);
        float p0 = FlagRipple.phase(0L), p1 = FlagRipple.phase(1L), p2 = FlagRipple.phase(4096L);
        for (float p : new float[]{p0, p1, p2}) assertTrue(p >= 0f && p < (float) (2 * Math.PI), "phase " + p);
        assertNotEquals(p1, p2, 1.0e-3f);
        assertEquals(FlagRipple.phase(123456789L), FlagRipple.phase(123456789L), "steady per pole");
    }
}
