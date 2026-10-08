package com.richardsenger.piratesnships.ship.hull.runtime;

import com.richardsenger.piratesnships.ship.hull.Compartment;
import com.richardsenger.piratesnships.ship.hull.HullAnalysis;
import com.richardsenger.piratesnships.ship.hull.HullVec;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodSimulation;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Server half of the flood surface sync (FLD1, docs/design.md §4.4): which compartments of a ship show a water surface,
 * where, and when the clients need a new {@link FloodSurfacePayload}. One per {@link HullRuntime}; no world access.
 *
 * <p>A compartment shows a surface while it holds at least {@link #MIN_VOLUME} blocks of water and is not full (a full
 * compartment's surface would lie on its ceiling). A payload is due when the surfaces changed against the last one sent
 * (another set of compartments or cells, or a level that moved by more than {@link #LEVEL_EPSILON}), and at most once
 * per {@code interval} ticks; the first call is always due.
 */
public final class FloodSurfaces {

    /** Less water than this (blocks) shows no surface. */
    static final double MIN_VOLUME = 0.02;
    /** A compartment within this much (blocks) of full shows no surface. */
    static final double FULL_MARGIN = 0.02;
    /** Level changes below this (blocks) are not worth a payload. */
    static final float LEVEL_EPSILON = 0.01f;

    private @Nullable HullAnalysis cellsFor;
    private List<CellSet> cells = List.of();
    private @Nullable List<FloodSurfacePayload.Surface> lastSent;
    private long lastSentTick;

    /** The cell sets of the analysis' compartments, by compartment id (cached per analysis). */
    List<CellSet> cellsOf(HullAnalysis analysis) {
        if (analysis != cellsFor) {
            List<CellSet> out = new ArrayList<>(analysis.compartments().size());
            for (Compartment c : analysis.compartments()) {
                out.add(CellSet.fromGrid(analysis.grid(), c.cells()));
            }
            cells = List.copyOf(out);
            cellsFor = analysis;
        }
        return cells;
    }

    /** The surfaces of the simulation's partly flooded compartments right now, in compartment order. */
    public List<FloodSurfacePayload.Surface> build(FloodSimulation sim) {
        HullAnalysis a = sim.analysis();
        List<CellSet> sets = cellsOf(a);
        HullVec up = a.up();
        List<FloodSurfacePayload.Surface> out = new ArrayList<>();
        for (Compartment c : a.compartments()) {
            double v = sim.volume(c.id());
            if (v < MIN_VOLUME || v > c.volume() - FULL_MARGIN) {
                continue;
            }
            CellSet s = sets.get(c.id());
            out.add(new FloodSurfacePayload.Surface(s, (float) relativeLevel(sim.level(c.id()), up, s)));
        }
        return out;
    }

    /** A ship-frame level (along {@code up}, absolute plot coordinates) as a height above the set's min corner. */
    static double relativeLevel(double level, HullVec up, CellSet s) {
        return level - (up.x() * s.minX() + up.y() * s.minY() + up.z() * s.minZ());
    }

    /** Whether {@code current} should be sent at tick {@code now} (see the class comment). */
    public boolean due(long now, int interval, List<FloodSurfacePayload.Surface> current) {
        if (lastSent == null) {
            return true;
        }
        return now - lastSentTick >= Math.max(1, interval) && changed(lastSent, current);
    }

    public void markSent(long now, List<FloodSurfacePayload.Surface> sent) {
        lastSent = List.copyOf(sent);
        lastSentTick = now;
    }

    /** Whether two surface lists differ in their compartments or cells, or a level by more than {@link #LEVEL_EPSILON}. */
    static boolean changed(List<FloodSurfacePayload.Surface> a, List<FloodSurfacePayload.Surface> b) {
        if (a.size() != b.size()) {
            return true;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).cells().equals(b.get(i).cells()) || Math.abs(a.get(i).level() - b.get(i).level()) > LEVEL_EPSILON) {
                return true;
            }
        }
        return false;
    }
}
