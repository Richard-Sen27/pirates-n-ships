package com.richardsenger.piratesnships.ship.hull.flooding;

import com.richardsenger.piratesnships.ship.hull.CellKind;
import com.richardsenger.piratesnships.ship.hull.Compartment;
import com.richardsenger.piratesnships.ship.hull.CompartmentLink;
import com.richardsenger.piratesnships.ship.hull.HeightProfile;
import com.richardsenger.piratesnships.ship.hull.HullAnalysis;
import com.richardsenger.piratesnships.ship.hull.HullGrid;
import com.richardsenger.piratesnships.ship.hull.HullVec;
import com.richardsenger.piratesnships.ship.hull.OutsidePort;

import java.util.BitSet;
import java.util.List;

/**
 * Tick-based flooding of one ship (docs/design.md §4.5, §5.4). Deterministic, and volume-conserving except for the
 * explicit inflow, outflow and pump terms it reports. State: water volume per compartment plus the live open state of
 * every opening. Not thread-safe; owned by the server thread.
 *
 * <h2>Flow law</h2>
 * Every passage (outside port or link) uses the orifice law {@code Q = c · A · f · √head} per tick, where
 * {@code c = BASE_FLOW · multiplier}, {@code A} the area in block faces, {@code f = clamp(Hup − sill, 0.1, 1)} the wetted
 * fraction of the passage, and {@code head = Hup − max(Hdown, sill)} (submerged orifice when the downstream side is
 * above the sill, free overfall otherwise). Torricelli gives the √head scaling: deeper holes leak faster, but less
 * than linearly. The transfer is then clamped so that it never overshoots: the receiving side never rises above the
 * giving side (or the outside level), the giving side never drops below the sill, and capacities hold. That makes
 * equalization monotone, so it converges without oscillating.
 *
 * <h2>Heads</h2>
 * The head of a compartment is its surface height, except for a <i>full</i> compartment, which passes pressure on: its
 * head is the highest head of what feeds it (outside water above an open port's sill, or a neighbor through an open
 * link). This lets water rise through a flooded lower deck and an open hatch into the deck above.
 *
 * <h2>Order per tick</h2>
 * Outside ports (per compartment, in id order), then links (in analysis order, Gauss-Seidel on live volumes), then
 * pumps. With flooding disabled no water ever enters from outside; draining, links and pumps still work.
 */
public final class FloodSimulation {

    private static final double FULL_EPS = 1e-9;
    /**
     * Floor of the wetted fraction {@code f}. Without it, flow over a sill falls off as {@code depth^1.5} (weir law) and a
     * compartment never quite empties down to a sill; with it, the last tenth of a block drains in finite time.
     */
    static final double MIN_WETTED = 0.1;

    private HullAnalysis analysis;
    private FloodParams params;
    private double[] volume;
    private BitSet open;
    /** Surface height per compartment, {@code NaN} = recompute (bisection) on next use. */
    private double[] levelCache;

    public FloodSimulation(HullAnalysis analysis, FloodParams params) {
        this.analysis = analysis;
        this.params = params;
        this.volume = new double[analysis.compartments().size()];
        this.levelCache = new double[volume.length];
        java.util.Arrays.fill(levelCache, Double.NaN);
        this.open = openStates(analysis.grid());
    }

    public HullAnalysis analysis() {
        return analysis;
    }

    public FloodParams params() {
        return params;
    }

    public void setParams(FloodParams params) {
        this.params = params;
    }

    // ---- openings (O(1) toggles) ----

    /**
     * Opens or closes an opening at grid coordinates. O(1): the analysis does not depend on open states. Returns
     * {@code false} when the cell is not an opening in the current analysis: then the block change altered the cell
     * kind and the caller must mark the hull dirty instead.
     */
    public boolean setOpen(int x, int y, int z, boolean isOpen) {
        HullGrid g = analysis.grid();
        if (!g.inBounds(x, y, z) || g.kind(x, y, z) != CellKind.OPENING) {
            return false;
        }
        open.set(g.index(x, y, z), isOpen);
        return true;
    }

    public boolean isOpen(int gridIndex) {
        return open.get(gridIndex);
    }

    // ---- water state ----

    public double volume(int compartment) {
        return volume[compartment];
    }

    public void setVolume(int compartment, double v) {
        volume[compartment] = Math.max(0, Math.min(capacity(compartment), v));
        levelCache[compartment] = Double.NaN;
    }

    public double level(int compartment) {
        double l = levelCache[compartment];
        if (Double.isNaN(l)) {
            l = profile(compartment).levelAt(volume[compartment]);
            levelCache[compartment] = l;
        }
        return l;
    }

    public double totalVolume() {
        double t = 0;
        for (double v : volume) t += v;
        return t;
    }

    /** Cells whose center is below the compartment's water surface (grid indices). */
    public BitSet floodedCells(int compartment) {
        Compartment c = analysis.compartments().get(compartment);
        int k = c.profile().countBelow(level(compartment));
        BitSet b = new BitSet();
        for (int i = 0; i < k; i++) b.set(c.cellsByHeight()[i]);
        return b;
    }

    /** Compartment cells the water has not reached (grid indices). Intersect with "below sea level" if needed. */
    public BitSet dryCells(int compartment) {
        BitSet b = (BitSet) analysis.compartments().get(compartment).cells().clone();
        b.andNot(floodedCells(compartment));
        return b;
    }

    // ---- layout changes ----

    /**
     * Switches to a new analysis of the same ship (after a debounced re-analysis), carrying the water over.
     *
     * <p>Rule: if a new compartment has exactly the cells of an old one, it takes its volume unchanged. Otherwise each
     * old compartment's water is split per cell with the same fill fractions the height profile uses
     * ({@code clamp(L + 0.5 − h, 0, 1)}, normalized so they sum to the old volume), and every new compartment receives
     * the water of its cells. So merges add up, splits divide by where the water was, and water in cells that are no
     * longer in any compartment (now outside air, solid or openings) is lost. Matching is by ship-local position, so
     * grids may move or grow. Open states come from the new snapshot; re-apply toggles made after it was captured.
     *
     * @return the volume that was lost
     */
    public double rebind(HullAnalysis next) {
        HullGrid og = analysis.grid(), ng = next.grid();
        double[] nv = new double[next.compartments().size()];
        double lost = 0;
        for (Compartment a : analysis.compartments()) {
            double va = volume[a.id()];
            if (va <= 0) continue;
            int[] cells = a.cellsByHeight();
            int b = mapCompartment(og, ng, next, cells[0]);
            boolean identical = b >= 0 && next.compartments().get(b).volume() == cells.length;
            for (int i = 1; identical && i < cells.length; i++) {
                identical = mapCompartment(og, ng, next, cells[i]) == b;
            }
            if (identical) {
                nv[b] += va;
                continue;
            }
            double level = a.profile().levelAt(va);
            double sum = 0;
            int end = 0;
            for (; end < cells.length; end++) {
                double w = fill(level, analysis.height(cells[end]));
                if (w <= 0) break;
                sum += w;
            }
            double scale = sum > 0 ? va / sum : 0;
            for (int i = 0; i < end; i++) {
                double w = fill(level, analysis.height(cells[i])) * scale;
                int k = mapCompartment(og, ng, next, cells[i]);
                if (k >= 0) nv[k] += w;
                else lost += w;
            }
        }
        this.analysis = next;
        this.volume = nv;
        this.levelCache = new double[nv.length];
        java.util.Arrays.fill(levelCache, Double.NaN);
        this.open = openStates(ng);
        for (int c = 0; c < nv.length; c++) {
            double cap = capacity(c);
            if (nv[c] > cap) {
                lost += nv[c] - cap;
                nv[c] = cap;
            }
        }
        return lost;
    }

    private static int mapCompartment(HullGrid og, HullGrid ng, HullAnalysis next, int oldIndex) {
        int x = og.x(oldIndex) + og.originX() - ng.originX();
        int y = og.y(oldIndex) + og.originY() - ng.originY();
        int z = og.z(oldIndex) + og.originZ() - ng.originZ();
        return next.compartmentAt(x, y, z);
    }

    // ---- simulation ----

    /** Advances one tick and returns the outputs. */
    public FloodReport tick(FloodTickInput in) {
        List<Compartment> comps = analysis.compartments();
        int n = comps.size();
        double c = params.flowCoefficient();
        double[] head = computeHeads(in);
        boolean[] pressurized = new boolean[n];
        for (int i = 0; i < n; i++) pressurized[i] = head[i] > level(i) + 1e-12;

        double inflow = 0, outflow = 0, pumped = 0;
        // 1. outside ports
        for (Compartment comp : comps) {
            int i = comp.id();
            HeightProfile p = comp.profile();
            double h = pressurized[i] ? head[i] : level(i);
            double qIn = 0, capLevel = Double.NEGATIVE_INFINITY, qOut = 0, floor = Double.POSITIVE_INFINITY;
            for (OutsidePort port : comp.ports()) {
                if (!port.isPourPoint() && !open.get(port.openingCell())) continue;
                double o = in.outsideLevel(port), s = port.sill();
                if (o > s && o > h) {
                    if (!params.enabled()) continue;
                    qIn += c * port.area() * wetted(o - s) * Math.sqrt(o - Math.max(h, s));
                    capLevel = Math.max(capLevel, o);
                } else if (h > s && h > o) {
                    double low = Math.max(o, s);
                    qOut += c * port.area() * wetted(h - s) * Math.sqrt(h - low);
                    floor = Math.min(floor, low);
                }
            }
            if (qIn > 0) {
                double room = p.volumeAt(Math.min(capLevel, p.top())) - volume[i];
                double v = Math.max(0, Math.min(qIn, Math.min(room, capacity(i) - volume[i])));
                volume[i] += v;
                levelCache[i] = Double.NaN;
                inflow += v;
            }
            if (qOut > 0) {
                double v = Math.max(0, Math.min(qOut, volume[i] - p.volumeAt(floor)));
                volume[i] -= v;
                levelCache[i] = Double.NaN;
                outflow += v;
            }
        }
        // 2. links between compartments (heads again: a compartment the ports just refilled passes pressure on)
        head = computeHeads(in);
        for (int i = 0; i < n; i++) pressurized[i] = head[i] > level(i) + 1e-12;
        for (CompartmentLink link : analysis.links()) {
            if (!open.get(link.openingCell())) continue;
            flowThroughLink(link, head, pressurized, c);
        }
        // 3. pumps
        for (int i = 0; i < n; i++) {
            int pumps = in.pumpsAt(i);
            if (pumps <= 0) continue;
            double v = Math.min(volume[i], pumps * params.pumpPerTick());
            volume[i] -= v;
            levelCache[i] = Double.NaN;
            pumped += v;
        }
        return buildReport(in.seaLevel(), inflow, outflow, pumped);
    }

    /** The outputs for the current state, without advancing. */
    public FloodReport report(double seaLevel) {
        return buildReport(seaLevel, 0, 0, 0);
    }

    private void flowThroughLink(CompartmentLink link, double[] head, boolean[] pressurized, double c) {
        int a = link.a(), b = link.b();
        double ha = pressurized[a] ? head[a] : level(a);
        double hb = pressurized[b] ? head[b] : level(b);
        if (ha == hb) return;
        int src = ha > hb ? a : b, dst = src == a ? b : a;
        double hs = Math.max(ha, hb), hd = Math.min(ha, hb), sill = link.sill();
        if (hs <= sill) return;
        double q = c * wetted(hs - sill) * Math.sqrt(hs - Math.max(hd, sill));
        double max = Math.min(q, Math.min(volume[src], capacity(dst) - volume[dst]));
        if (!(max > 0)) return;
        HeightProfile ps = profile(src), pd = profile(dst);
        boolean srcPressurized = pressurized[src];
        double vs = volume[src], vd = volume[dst];
        // g(V) = source head after V − max(destination level after V, sill); keep g >= 0 (no overshoot)
        java.util.function.DoubleUnaryOperator g = v -> (srcPressurized ? hs : ps.levelAt(vs - v))
                - Math.max(pd.levelAt(vd + v), sill);
        double v;
        if (g.applyAsDouble(max) >= 0) {
            v = max;
        } else {
            double lo = 0, hi = max;
            for (int k = 0; k < 50; k++) {
                double mid = 0.5 * (lo + hi);
                if (g.applyAsDouble(mid) >= 0) lo = mid;
                else hi = mid;
            }
            v = lo;
        }
        volume[src] -= v;
        volume[dst] += v;
        levelCache[src] = Double.NaN;
        levelCache[dst] = Double.NaN;
    }

    private double[] computeHeads(FloodTickInput in) {
        List<Compartment> comps = analysis.compartments();
        int n = comps.size();
        double[] head = new double[n];
        boolean[] full = new boolean[n];
        for (int i = 0; i < n; i++) {
            head[i] = level(i);
            full[i] = volume[i] >= capacity(i) - FULL_EPS;
        }
        List<CompartmentLink> links = analysis.links();
        for (int iter = 0; iter <= n; iter++) {
            boolean changed = false;
            for (int i = 0; i < n; i++) {
                if (!full[i]) continue;
                double cand = head[i];
                Compartment comp = comps.get(i);
                if (params.enabled()) {
                    for (OutsidePort port : comp.ports()) {
                        if (!port.isPourPoint() && !open.get(port.openingCell())) continue;
                        double o = in.outsideLevel(port);
                        if (o > port.sill()) cand = Math.max(cand, o);
                    }
                }
                for (int id : comp.links()) {
                    CompartmentLink l = links.get(id);
                    if (!open.get(l.openingCell())) continue;
                    int j = l.a() == i ? l.b() : l.a();
                    if (head[j] > l.sill()) cand = Math.max(cand, head[j]);
                }
                if (cand > head[i] + 1e-12) {
                    head[i] = cand;
                    changed = true;
                }
            }
            if (!changed) break;
        }
        return head;
    }

    private FloodReport buildReport(double sea, double inflow, double outflow, double pumped) {
        int n = volume.length;
        double[] levels = new double[n];
        double fv = 0, fx = 0, fy = 0, fz = 0, dv = 0, dx = 0, dy = 0, dz = 0;
        for (int i = 0; i < n; i++) {
            HeightProfile p = profile(i);
            double l = level(i);
            levels[i] = l;
            fv += volume[i];
            double[] m = p.moments(l);
            fx += m[1];
            fy += m[2];
            fz += m[3];
            if (sea > l) {
                double[] s = p.moments(sea);
                dv += s[0] - m[0];
                dx += s[1] - m[1];
                dy += s[2] - m[2];
                dz += s[3] - m[3];
            }
        }
        double fw = 0;
        for (int i = 0; i < n; i++) fw += profile(i).moments(levels[i])[0];
        HullVec floodCentroid = fw > 1e-9 ? new HullVec(fx / fw, fy / fw, fz / fw) : null;
        HullVec dryCentroid = dv > 1e-9 ? new HullVec(dx / dv, dy / dv, dz / dv) : null;
        return new FloodReport(volume.clone(), levels, fv, floodCentroid, Math.max(0, dv), dryCentroid, inflow, outflow, pumped);
    }

    private HeightProfile profile(int compartment) {
        return analysis.compartments().get(compartment).profile();
    }

    private double capacity(int compartment) {
        return analysis.compartments().get(compartment).volume();
    }

    private static double fill(double level, double h) {
        return clamp01(level + 0.5 - h);
    }

    /** Wetted fraction of a passage, with a floor so that a nearly drained or filled compartment finishes in finite time. */
    private static double wetted(double depth) {
        return Math.max(MIN_WETTED, clamp01(depth));
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static BitSet openStates(HullGrid g) {
        BitSet b = new BitSet(g.cellCount());
        for (int i = 0; i < g.cellCount(); i++) {
            if (g.isOpen(i)) b.set(i);
        }
        return b;
    }
}
