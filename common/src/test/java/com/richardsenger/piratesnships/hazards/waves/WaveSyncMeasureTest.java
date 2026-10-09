package com.richardsenger.piratesnships.hazards.waves;

import com.richardsenger.piratesnships.hazards.waves.client.surface.SurfaceWaves;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * WAV7 step 1: the size of the mismatch between the physics' sample (anchored at the hull centre) and the drawn sea
 * (anchored at the ship's position or the camera) with the anchored field of WV1/WAV3. Writes build/wav7-measure.txt.
 */
class WaveSyncMeasureTest {

    @Test
    void measureTheAnchorMismatch() throws IOException {
        double amp = SeaState.STORM.amplitude();
        List<WaveField.Component> comps = WaveSpectrum.components(6, WaveSpectrum.peakWavelength(amp));
        WaveField f = new WaveField(amp, 110.0, comps, WaveField.Origin.NONE, new WaveField.Groups(0.35, 1200.0));
        double cx = 3000, cz = -2000;          // hull centre (WaveForces' anchor)
        double bx = cx, bz = cz - 8;           // bow, ship heading north
        double sx = cx + 2.0, sz = cz + 1.5;   // the ship's position (centre of mass) the drawn sea anchors at
        double kx = cx + 40.0, kz = cz;        // a free camera anchor 40 blocks away
        StringBuilder out = new StringBuilder();
        for (double[] a : new double[][] {{sx, sz}, {kx, kz}}) {
            double max = 0, sum2 = 0, ref2 = 0;
            int n = 0;
            for (int t = 0; t < 3600; t++) {
                double phys = f.heightAround(cx, cz, bx, bz, t);
                double drawn = new SurfaceWaves().prepare(f, a[0], a[1], t).height(bx, bz);
                max = Math.max(max, Math.abs(phys - drawn));
                sum2 += (phys - drawn) * (phys - drawn);
                ref2 += phys * phys;
                n++;
            }
            out.append(String.format("anchor (%.1f, %.1f), %.1f blocks from the hull centre: max |dh| %.3f, rms dh %.3f, rms h %.3f%n",
                    a[0], a[1], Math.hypot(a[0] - cx, a[1] - cz), max, Math.sqrt(sum2 / n), Math.sqrt(ref2 / n)));
            for (WaveField.Component c : comps) {
                double d = Math.toRadians(110.0 + c.offsetDeg()), r = Math.toRadians(c.refBearingDeg());
                double ddx = Math.sin(r) - Math.sin(d), ddz = -Math.cos(r) + Math.cos(d);
                double dphi = c.k() * (ddx * (a[0] - cx) + ddz * (a[1] - cz));
                double deg = Math.toDegrees(dphi);
                deg = ((deg % 360) + 540) % 360 - 180;
                out.append(String.format("  train %.1f blocks (w %.3f): phase offset %.1f deg (%.1f ticks of its %.0f-tick period)%n",
                        c.wavelength(), c.weight(), deg, deg / 360.0 * c.periodTicks(), c.periodTicks()));
            }
        }
        Path p = Path.of("build", "wav7-measure.txt");
        Files.createDirectories(p.getParent());
        Files.writeString(p, out.toString());
        System.out.println(out);
    }
}
