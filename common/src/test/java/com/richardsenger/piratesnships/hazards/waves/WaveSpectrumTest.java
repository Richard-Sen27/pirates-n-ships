package com.richardsenger.piratesnships.hazards.waves;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** WAV2: the spectrum, the wave groups and what both keep of WV1's field. */
class WaveSpectrumTest {

    private static final WaveField.Groups GROUPS = new WaveField.Groups(0.35, 1200.0);

    private static WaveField storm(double directionDeg, WaveField.Origin origin) {
        return new WaveField(1.2, directionDeg, WaveSpectrum.components(6, WaveSpectrum.peakWavelength(1.2)), origin, GROUPS);
    }

    @Test
    void weightsSumToOneForEveryCountAndPeak() {
        for (int n = WaveSpectrum.MIN_COMPONENTS; n <= WaveSpectrum.MAX_COMPONENTS; n++) {
            for (double peak : new double[] {16, 20, 24, 29, 34, 50}) {
                double sum = 0;
                for (WaveField.Component c : WaveSpectrum.components(n, peak)) {
                    assertTrue(c.weight() > 0.0, "weight " + c.weight());
                    sum += c.weight();
                }
                assertEquals(1.0, sum, 1e-12, "n " + n + " peak " + peak);
            }
        }
    }

    @Test
    void periodsFollowTheDispersionAndKeepTheSwell() {
        for (int n = WaveSpectrum.MIN_COMPONENTS; n <= WaveSpectrum.MAX_COMPONENTS; n++) {
            boolean swell = false;
            for (WaveSpectrum.Train t : WaveSpectrum.trains(n)) {
                double seconds = t.periodTicks() / 20.0;
                assertEquals(2.0 * Math.PI * t.wavelength() / WaveSpectrum.G_EFF, seconds * seconds, 1e-9);
                if (t.wavelength() == WaveSpectrum.REFERENCE_WAVELENGTH) {
                    swell = true;
                    assertEquals(180.0, t.periodTicks(), 1e-9);
                    assertEquals(0.0, t.offsetDeg());
                }
                assertTrue(t.wavelength() >= WaveSpectrum.MIN_WAVELENGTH && t.wavelength() <= WaveSpectrum.MAX_WAVELENGTH);
                assertTrue(Math.abs(t.offsetDeg()) <= WaveSpectrum.DIRECTION_SPREAD_DEG);
            }
            assertTrue(swell, "no 34-block swell among " + n + " trains");
        }
        // longer waves run faster: c = λ / T grows with λ
        List<WaveSpectrum.Train> six = WaveSpectrum.trains(6);
        for (int i = 1; i < six.size(); i++) {
            assertTrue(six.get(i).wavelength() > six.get(i - 1).wavelength());
            assertTrue(six.get(i).wavelength() / six.get(i).periodTicks() > six.get(i - 1).wavelength() / six.get(i - 1).periodTicks());
        }
    }

    @Test
    void weightsFallOffOnBothSidesOfThePeak() {
        List<WaveSpectrum.Train> trains = WaveSpectrum.trains(12);
        double[] w = WaveSpectrum.weights(trains, 34.0);
        int top = 0;
        for (int i = 1; i < w.length; i++) {
            if (w[i] > w[top]) top = i;
        }
        double peakLength = trains.get(top).wavelength();
        assertTrue(peakLength > 26 && peakLength < 40, "heaviest train " + peakLength);
        for (int i = 1; i <= top; i++) {
            assertTrue(w[i] >= w[i - 1], "not rising toward the peak at " + i);
        }
        for (int i = top + 1; i < w.length; i++) {
            assertTrue(w[i] <= w[i - 1], "not falling past the peak at " + i);
        }
        assertTrue(w[w.length - 1] < 0.5 * w[top] && w[0] < 0.5 * w[top], "the tails are too heavy");
        // a calm sea is a long, low swell; from a moderate sea on, the wind sea's energy grows toward the storm's swell
        double calmMean = 0, moderateMean = 0, stormMean = 0;
        double[] calm = WaveSpectrum.weights(trains, WaveSpectrum.peakWavelength(SeaState.CALM.amplitude()));
        double[] moderate = WaveSpectrum.weights(trains, WaveSpectrum.peakWavelength(SeaState.MODERATE.amplitude()));
        for (int i = 0; i < w.length; i++) {
            calmMean += calm[i] * trains.get(i).wavelength();
            moderateMean += moderate[i] * trains.get(i).wavelength();
            stormMean += w[i] * trains.get(i).wavelength();
        }
        assertTrue(calmMean > stormMean + 1, "calm " + calmMean + " storm " + stormMean);
        assertTrue(moderateMean < stormMean, "moderate " + moderateMean + " storm " + stormMean);
    }

    @Test
    void theSpectrumIsDeterministic() {
        // the same trains on every construction, and fixed numbers (a changed seed or rule changes every sea)
        assertEquals(WaveSpectrum.components(6, 31.0), WaveSpectrum.components(6, 31.0));
        WaveField a = storm(123.0, WaveField.Origin.NONE);
        WaveField b = storm(123.0, WaveField.Origin.NONE);
        for (int i = 0; i < 100; i++) {
            double x = i * 13.7 - 400, z = 200 - i * 5.3, t = 1000 + i * 31.0;
            assertEquals(a.heightAround(x, z, x + 3, z - 2, t), b.heightAround(x, z, x + 3, z - 2, t), 0.0);
        }
        assertNotEquals(WaveSpectrum.trains(6), WaveSpectrum.trains(7));
    }

    @Test
    void theBoundHoldsWithTheEnvelope() {
        WaveField f = storm(37.0, WaveField.Origin.NONE);
        assertEquals(1.2 * 1.35, f.maxHeight(), 1e-12);
        double max = 0, minEnv = 9, maxEnv = 0;
        for (int i = 0; i < 200000; i++) {
            double x = (i * 7.31) % 900 - 450, z = (i * 3.17) % 400 - 200, t = i * 13.7;
            double h = f.height(x, z, t);
            assertTrue(Math.abs(h) <= f.maxHeight() + 1e-9, "height " + h);
            max = Math.max(max, Math.abs(h));
            double e = f.envelope(x, z, t);
            minEnv = Math.min(minEnv, e);
            maxEnv = Math.max(maxEnv, e);
        }
        assertTrue(max > 1.2, "the groups should lift some crests over the plain amplitude: " + max);
        assertTrue(minEnv >= 0.65 - 1e-9 && maxEnv <= 1.35 + 1e-9, "envelope " + minEnv + ".." + maxEnv);
        assertTrue(minEnv < 0.75 && maxEnv > 1.25, "the envelope should swing nearly its full depth: " + minEnv + ".." + maxEnv);
    }

    @Test
    void groupsComeAndGoOverAboutAMinute() {
        // at a fixed point the envelope's sets last tens of seconds, not a wave period
        WaveField f = storm(90.0, WaveField.Origin.NONE);
        int crossings = 0;
        double prev = f.envelope(0, 0, 0) - 1.0;
        for (int t = 1; t <= 20 * 600; t++) {
            double e = f.envelope(0, 0, t) - 1.0;
            if (prev < 0 && e >= 0) crossings++;
            prev = e;
        }
        // 10 minutes, terms of 51 and 78 s: between 5 and 14 sets
        assertTrue(crossings >= 5 && crossings <= 14, "sets in 10 minutes: " + crossings);
    }

    @Test
    void movingTheAnchorIsContinuousForEveryTrainAndTheEnvelope() {
        WaveField f = storm(33.0, WaveField.Origin.NONE);
        for (double ax : new double[] {500, 20000, -31000}) {
            double h1 = f.heightAround(ax, 500, ax + 6, 503, 77);
            double h2 = f.heightAround(ax + 0.01, 500.01, ax + 6.01, 503.01, 77);
            assertTrue(Math.abs(h1 - h2) < 0.01, "jump " + (h1 - h2) + " at " + ax);
        }
        // a slow drift of the direction far out barely moves the surface
        WaveField turned = storm(33.02, WaveField.Origin.NONE);
        double dh = Math.abs(f.heightAround(20000, -15000, 20008, -14997, 100) - turned.heightAround(20000, -15000, 20008, -14997, 100));
        assertTrue(dh < 0.01, "height jumped by " + dh);
        assertEquals(f.height(20000, -15000, 100), turned.height(20000, -15000, 100), 1e-12);
    }

    @Test
    void easingThePeakChangesTheSurfaceContinuously() {
        double h0 = new WaveField(0.7, 90, WaveSpectrum.components(6, 29.0), WaveField.Origin.NONE, GROUPS).height(1234, 567, 8910);
        double h1 = new WaveField(0.7, 90, WaveSpectrum.components(6, 29.001), WaveField.Origin.NONE, GROUPS).height(1234, 567, 8910);
        assertTrue(Math.abs(h0 - h1) < 1e-3);
        assertEquals(30.0, WaveSpectrum.peakWavelength(SeaState.MODERATE.amplitude()), 1e-12);
        assertEquals(31.0, WaveSpectrum.peakWavelength(0.5), 1e-12);
        assertEquals(56.0, WaveSpectrum.peakWavelength(0.0), 1e-12);
        assertEquals(43.0, WaveSpectrum.peakWavelength(0.2), 1e-12);
        assertEquals(34.0, WaveSpectrum.peakWavelength(3.0), 1e-12);
    }

    @Test
    void groupsGrowFromACalmSwellToAModerateSea() {
        assertEquals(0.0, WaveSpectrum.groupShare(SeaState.CALM.amplitude()), 1e-12);
        assertEquals(0.0, WaveSpectrum.groupShare(0.0), 1e-12);
        assertEquals(0.5, WaveSpectrum.groupShare(0.2), 1e-12);
        assertEquals(1.0, WaveSpectrum.groupShare(SeaState.MODERATE.amplitude()), 1e-12);
        assertEquals(1.0, WaveSpectrum.groupShare(SeaState.STORM.amplitude()), 1e-12);
        // a calm swell is regular: no envelope
        WaveField calm = new WaveField(0.1, 90, WaveSpectrum.components(6, WaveSpectrum.peakWavelength(0.1)), WaveField.Origin.NONE,
                new WaveField.Groups(0.35 * WaveSpectrum.groupShare(0.1), 1200.0));
        assertEquals(1.0, calm.envelope(123, 456, 789), 0.0);
        assertEquals(0.1, calm.maxHeight(), 1e-12);
    }

    @Test
    void slopeIsTheGradientWithTheEnvelope() {
        WaveField f = storm(210.0, new WaveField.Origin(400.0, 1000.0, -300.0));
        double ax = 1234.5, az = -987.25, t = 4567, e = 1e-4;
        double[] g = f.slopeAround(ax, az, ax + 3, az - 2, t);
        double gx = (f.heightAround(ax, az, ax + 3 + e, az - 2, t) - f.heightAround(ax, az, ax + 3 - e, az - 2, t)) / (2 * e);
        double gz = (f.heightAround(ax, az, ax + 3, az - 2 + e, t) - f.heightAround(ax, az, ax + 3, az - 2 - e, t)) / (2 * e);
        assertEquals(gx, g[0], 1e-6);
        assertEquals(gz, g[1], 1e-6);
        assertTrue(Math.hypot(g[0], g[1]) <= f.maxSlope() + 1e-12);
    }

    @Test
    void theRecordAtAPointIsNoSingleSine() {
        // successive crest heights at one point differ: the sea is irregular
        WaveField f = storm(90.0, WaveField.Origin.NONE);
        double prev = f.height(0, 0, 0), crest = 0, lastCrest = Double.NaN, diff = 0;
        int crests = 0;
        for (int t = 1; t < 20 * 300; t++) {
            double h = f.height(0, 0, t);
            if (prev < 0 && h >= 0) {
                if (!Double.isNaN(lastCrest)) {
                    diff += Math.abs(crest - lastCrest) / Math.max(crest, lastCrest);
                    crests++;
                }
                lastCrest = crest;
                crest = 0;
            }
            crest = Math.max(crest, h);
            prev = h;
        }
        assertTrue(crests > 20, "crests " + crests);
        assertTrue(diff / crests > 0.15, "mean change of successive crests " + diff / crests);
    }
}
