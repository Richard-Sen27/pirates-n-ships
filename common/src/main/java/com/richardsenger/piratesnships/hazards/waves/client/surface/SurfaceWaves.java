package com.richardsenger.piratesnships.hazards.waves.client.surface;

import com.richardsenger.piratesnships.hazards.waves.WaveField;
import java.util.List;

/**
 * Fast evaluation of one {@link WaveField} at one moment over the mesh's vertex grids (WAV3, WAV7). Pure.
 *
 * <p>At a fixed time every train of {@link WaveField#heightAt} is a plane wave in the point:
 * {@code kᵢ dᵢ·(p − o) − ωᵢ (t − tₒ) + φᵢ}, and a crossfading field is simply both seas' trains with
 * their weights scaled by {@code 1 − u} and {@code u}. The group envelope runs east, so it depends on x alone: one
 * factor per grid column. {@link #prepare} computes everything but {@code p} once per frame;
 * {@link #fillGrid} then walks each grid row with the angle-addition recurrence (two trig calls per row and train
 * instead of one per vertex and train). The result equals {@code heightAt(x, z, t)} (tested to 1e-9): the drawn sea is
 * the sea the ships feel, at every point (WAV7; WAV3 evaluated an anchored field at an anchor near the camera, which put
 * the crests the player saw up to half a wave off the ones a passing ship rode).
 */
public final class SurfaceWaves {

    private double amplitude;
    private int n;
    // per train: k, the direction (dx, dz), ω·(t − tₒ), φ, the weight × its share of the crossfade
    private double[] k = new double[0], dx = new double[0], dz = new double[0], wt = new double[0], ph = new double[0],
            w = new double[0];
    // group envelope: E(x) = 1 + depth · ½ Σ sin(gk · (x − oₓ) − gω · (t − tₒ) + gφ)
    private final double[] gk = new double[2], gwt = new double[2], gph = new double[2];
    private int groups;
    private double groupDepth;
    private double ox, oz;
    private double time;

    /** Sets the field and time of the frame. A flat field gives height 0 everywhere. */
    public SurfaceWaves prepare(WaveField field, double t) {
        time = t;
        n = 0;
        groups = 0;
        if (field.isFlat()) {
            amplitude = 0.0;
            return this;
        }
        List<WaveField.Component> comps = field.components();
        WaveField.Heading heading = field.heading();
        double u = heading.fade(t);
        int size = comps.size() * (u < 1.0 && u > 0.0 ? 2 : 1);
        if (k.length < size) {
            k = new double[size];
            dx = new double[size];
            dz = new double[size];
            wt = new double[size];
            ph = new double[size];
            w = new double[size];
        }
        WaveField.Origin o = field.origin();
        ox = o.x();
        oz = o.z();
        double ot = t - o.ticks();
        if (u > 0.0) {
            add(comps, heading.current(), u, ot);
        }
        if (u < 1.0) {
            add(comps, heading.previous(), 1.0 - u, ot);
        }
        WaveField.Groups g = field.groups();
        if (g.depth() > 0.0) {
            groups = 2;
            groupDepth = g.depth();
            for (int i = 0; i < 2; i++) {
                gk[i] = g.k(i);
                gwt[i] = g.omega(i) * ot;
                gph[i] = g.phase(i);
            }
        }
        amplitude = field.amplitude();
        return this;
    }

    private void add(List<WaveField.Component> comps, double directionDeg, double share, double ot) {
        for (WaveField.Component comp : comps) {
            double dir = Math.toRadians(directionDeg + comp.offsetDeg());
            k[n] = comp.k();
            dx[n] = Math.sin(dir);
            dz[n] = -Math.cos(dir);
            wt[n] = comp.omega() * ot;
            ph[n] = comp.phase();
            w[n] = comp.weight() * share;
            n++;
        }
    }

    /**
     * Phase of train {@code i} at {@code (x, z)}, in exactly {@link WaveField}'s arithmetic, so the drawn sea and the
     * ships' agree to the last bit even where the phase runs to millions of radians.
     */
    private double phase(int i, double x, double z) {
        return k[i] * (dx[i] * (x - ox) + dz[i] * (z - oz)) - wt[i] + ph[i];
    }

    public double time() {
        return time;
    }

    public boolean isFlat() {
        return n == 0 || amplitude == 0.0;
    }

    /** The group envelope at world x (1 without groups). */
    private double envelope(double x) {
        if (groups == 0) {
            return 1.0;
        }
        double gx = x - ox;
        double s = 0.0;
        for (int i = 0; i < groups; i++) {
            s += Math.sin(gk[i] * gx - gwt[i] + gph[i]);
        }
        return 1.0 + groupDepth * 0.5 * s;
    }

    /** Height of the sea above still water at {@code (x, z)} [blocks]. */
    public double height(double x, double z) {
        if (isFlat()) {
            return 0.0;
        }
        double h = 0.0;
        for (int i = 0; i < n; i++) {
            h += w[i] * Math.sin(phase(i, x, z));
        }
        return amplitude * envelope(x) * h;
    }

    /**
     * Heights at {@code (x0 + ix·step, z0 + iz·step)} for {@code ix, iz} in {@code 0..count-1} into
     * {@code out[iz · count + ix]}.
     */
    public void fillGrid(double x0, double z0, double step, int count, double[] out) {
        int total = count * count;
        java.util.Arrays.fill(out, 0, total, 0.0);
        if (isFlat()) {
            return;
        }
        for (int i = 0; i < n; i++) {
            double kxStep = k[i] * dx[i] * step;
            double cosStep = Math.cos(kxStep), sinStep = Math.sin(kxStep);
            double wi = w[i];
            for (int iz = 0; iz < count; iz++) {
                double phase = phase(i, x0, z0 + iz * step);
                double s = Math.sin(phase), co = Math.cos(phase);
                int row = iz * count;
                for (int ix = 0; ix < count; ix++) {
                    out[row + ix] += wi * s;
                    double ns = s * cosStep + co * sinStep;
                    co = co * cosStep - s * sinStep;
                    s = ns;
                }
            }
        }
        for (int ix = 0; ix < count; ix++) {
            double a = amplitude * envelope(x0 + ix * step);
            for (int iz = 0; iz < count; iz++) {
                out[iz * count + ix] *= a;
            }
        }
    }
}
