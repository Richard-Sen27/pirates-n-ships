package com.richardsenger.piratesnships.hazards.waves.client.surface;

import com.richardsenger.piratesnships.hazards.waves.WaveField;
import java.util.List;

/**
 * Fast evaluation of one {@link WaveField} at one anchor and one moment over the mesh's vertex grids (WAV3). Pure.
 *
 * <p>For a fixed anchor {@code a} every train of {@link WaveField#heightAround} is a plane wave in the point:
 * {@code kᵢ dᵢ·(p − a) + kᵢ rᵢ·(a − o) − ωᵢ (t − tₒ) + φᵢ = kᵢ dᵢ·p + cᵢ}, and the group envelope is taken at the
 * anchor, so it is one factor for the whole frame. {@link #prepare} folds everything but {@code p} into
 * {@code (kxᵢ, kzᵢ, cᵢ)}; {@link #fillGrid} then walks each grid row with the angle-addition recurrence (two trig calls
 * per row and train instead of one per vertex and train). The result equals {@code heightAround(a, p, t)} (tested to
 * 1e-9).
 *
 * <p>Why an anchor near the camera (or the ship the player is on) instead of {@code height(x, z, t)}: with the point
 * as its own anchor the field runs along every train's fixed reference bearing, east, whatever the wind; the anchored
 * field runs with the wind like the ships' (see {@link SurfaceAnchor}).
 */
public final class SurfaceWaves {

    private double amplitude;
    private int n;
    private double[] kx = new double[0], kz = new double[0], c = new double[0], w = new double[0];
    private double anchorX, anchorZ, time;

    /** Sets the field, anchor and time of the frame. A flat field gives height 0 everywhere. */
    public SurfaceWaves prepare(WaveField field, double ax, double az, double t) {
        anchorX = ax;
        anchorZ = az;
        time = t;
        if (field.isFlat()) {
            amplitude = 0.0;
            n = 0;
            return this;
        }
        List<WaveField.Component> comps = field.components();
        n = comps.size();
        if (kx.length < n) {
            kx = new double[n];
            kz = new double[n];
            c = new double[n];
            w = new double[n];
        }
        WaveField.Origin o = field.origin();
        double oax = ax - o.x(), oaz = az - o.z(), ot = t - o.ticks();
        for (int i = 0; i < n; i++) {
            WaveField.Component comp = comps.get(i);
            double k = comp.k();
            double dir = Math.toRadians(field.directionDegrees() + comp.offsetDeg());
            double ref = Math.toRadians(comp.refBearingDeg());
            double dx = Math.sin(dir), dz = -Math.cos(dir), rx = Math.sin(ref), rz = -Math.cos(ref);
            kx[i] = k * dx;
            kz[i] = k * dz;
            c[i] = -k * (dx * ax + dz * az) + k * (rx * oax + rz * oaz) - comp.omega() * ot + comp.phase();
            w[i] = comp.weight();
        }
        amplitude = field.amplitude() * field.envelope(ax, az, t);
        return this;
    }

    public double anchorX() {
        return anchorX;
    }

    public double anchorZ() {
        return anchorZ;
    }

    public double time() {
        return time;
    }

    public boolean isFlat() {
        return n == 0 || amplitude == 0.0;
    }

    /** Height of the sea above still water at {@code (x, z)} [blocks]. */
    public double height(double x, double z) {
        if (isFlat()) {
            return 0.0;
        }
        double h = 0.0;
        for (int i = 0; i < n; i++) {
            h += w[i] * Math.sin(kx[i] * x + kz[i] * z + c[i]);
        }
        return amplitude * h;
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
            double cosStep = Math.cos(kx[i] * step), sinStep = Math.sin(kx[i] * step);
            double wi = w[i] * amplitude;
            for (int iz = 0; iz < count; iz++) {
                double phase = kx[i] * x0 + kz[i] * (z0 + iz * step) + c[i];
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
    }
}
