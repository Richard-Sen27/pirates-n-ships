package com.richardsenger.piratesnships.ship.hull;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * Which partial cells (slabs, stairs, trapdoors, doors: {@link HullGrid#isPartial}) join a compartment's dry region
 * (docs/design.md §4.3). The world keeps real water blocks where the hull stands, and Sable's occlusion regions are
 * whole cells, so the empty part of a partial block shows that water unless its cell is in a region.
 *
 * <p>Rule, per partial cell, looking at the neighbour across each face its shape does not fully cover:
 * <ul>
 *     <li>outside the grid, or air that is in no compartment (outside air, the sea): the empty part is open to the
 *     outside, so the cell <b>never</b> joins (hiding it would cut a hole into the sea);</li>
 *     <li>a compartment cell: the cell joins only if <b>all</b> such neighbours are dry right now, and then joins the
 *     region of the lowest compartment id among them. A flooded neighbour means water fills the empty part too;</li>
 *     <li>solid or opening (another block, possibly partial too): neutral, it neither allows nor blocks.</li>
 * </ul>
 * A partial cell without any compartment neighbour across an uncovered face never joins. The analysis itself is not
 * affected: partial cells stay watertight walls. {@link #of} runs once per analysis (O(partial cells)), and
 * {@link #additions} once per region rebuild (O(candidates)).
 */
public final class PartialCellRule {

    public static final PartialCellRule NONE = new PartialCellRule(new int[0], new int[0][], new int[0][]);

    /** Candidate partial cells (grid index): no uncovered face is open to the outside. */
    private final int[] cells;
    /** Per candidate: the compartment cells across its uncovered faces, and their compartment ids. */
    private final int[][] neighbours;
    private final int[][] compartments;

    private PartialCellRule(int[] cells, int[][] neighbours, int[][] compartments) {
        this.cells = cells;
        this.neighbours = neighbours;
        this.compartments = compartments;
    }

    public static PartialCellRule of(HullAnalysis analysis) {
        HullGrid g = analysis.grid();
        int[] partial = g.partialCells();
        if (partial.length == 0) {
            return NONE;
        }
        int[] cells = new int[partial.length];
        int[][] nb = new int[partial.length][];
        int[][] cp = new int[partial.length][];
        int n = 0;
        int[] tmpN = new int[6];
        int[] tmpC = new int[6];
        for (int cell : partial) {
            int faces = g.uncoveredFaces(cell);
            int x = g.x(cell), y = g.y(cell), z = g.z(cell);
            int k = 0;
            boolean outside = false;
            for (int f = 0; f < 6 && !outside; f++) {
                if ((faces & (1 << f)) == 0) continue;
                int nx = x + CellFaces.DX[f], ny = y + CellFaces.DY[f], nz = z + CellFaces.DZ[f];
                if (!g.inBounds(nx, ny, nz)) {
                    outside = true;
                    continue;
                }
                int ni = g.index(nx, ny, nz);
                if (g.kind(ni) != CellKind.AIR) continue;
                int c = analysis.compartmentOf(ni);
                if (c < 0) {
                    outside = true;
                } else {
                    tmpN[k] = ni;
                    tmpC[k] = c;
                    k++;
                }
            }
            if (outside || k == 0) continue;
            cells[n] = cell;
            nb[n] = Arrays.copyOf(tmpN, k);
            cp[n] = Arrays.copyOf(tmpC, k);
            n++;
        }
        return new PartialCellRule(Arrays.copyOf(cells, n), Arrays.copyOf(nb, n), Arrays.copyOf(cp, n));
    }

    /** Number of partial cells that may join a region (those not open to the outside). */
    public int candidateCount() {
        return cells.length;
    }

    /**
     * The partial cells to add to each compartment's region, given its dry cells right now.
     *
     * @param dry dry cells (grid indices) per compartment id, as {@code FloodSimulation#dryCells} returns them
     * @return one set per compartment id (grid indices), empty where nothing joins
     */
    public BitSet[] additions(List<BitSet> dry) {
        BitSet[] out = new BitSet[dry.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = new BitSet();
        }
        for (int i = 0; i < cells.length; i++) {
            int target = Integer.MAX_VALUE;
            boolean allDry = true;
            for (int k = 0; k < neighbours[i].length; k++) {
                int c = compartments[i][k];
                if (c >= dry.size() || !dry.get(c).get(neighbours[i][k])) {
                    allDry = false;
                    break;
                }
                target = Math.min(target, c);
            }
            if (allDry) {
                out[target].set(cells[i]);
            }
        }
        return out;
    }
}
