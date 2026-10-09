package com.richardsenger.piratesnships.ship.hull;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * Which partial cells (slabs, stairs, trapdoors, doors, hatches: {@link HullGrid#isPartial}, every hull block that is not
 * a full cube) join a compartment's dry region (docs/design.md §4.3, §4.4 "Dry hull view (HV1)"). The world keeps real
 * water blocks where the hull stands, and Sable's occlusion regions are whole cells, so the empty part of a partial block
 * shows that water (and a camera in it sees the underwater fog and overlay) unless its cell is in a region.
 *
 * <p>Rule, per partial cell, looking at the neighbour across each face its shape does not fully cover:
 * <ul>
 *     <li>a compartment cell: the cell joins only if <b>all</b> such neighbours are dry right now, and then joins the
 *     region of the lowest compartment id among them. A flooded neighbour means water fills the empty part too;</li>
 *     <li>outside (beyond the grid, or air that is in no compartment): the empty part is open to the outside. That
 *     vetoes only while the outside cell is <b>under the sea</b> (more than {@link #SUBMERGED_TOLERANCE} of water above
 *     its lowest corner), since hiding the cell would then cut a hole into the sea. Outside air above the sea (the sky
 *     over a deck hatch, the deck beside a cabin door) does not veto, so a camera in a deck opening counts as inside the
 *     ship (HV1). Before HV1 every outside neighbour vetoed, and a camera in an open deck hatch at the waterline saw
 *     the underwater overlay;</li>
 *     <li>solid or opening (another block, possibly partial too): neutral, it neither allows nor blocks.</li>
 * </ul>
 * A partial cell without any compartment neighbour across an uncovered face joins only <b>transitively</b> (HV2): through
 * another partial cell that joined, where the two empty parts meet (each one's face towards the other is uncovered),
 * for example a deck hatch over a top slab whose empty half opens to the hold. The sea veto applies to it the same way.
 * A partial cell that reaches no dry compartment this way never joins (no dry room behind it, so no region to join).
 * The analysis itself is not affected: partial cells stay watertight walls. {@link #of} runs once per analysis
 * (O(partial cells)), {@link #additions} once per region rebuild (O(candidates)) and {@link #submergedCount} once per
 * tick (O(log candidates)).
 */
public final class PartialCellRule {

    /**
     * Water depth (blocks, along the analysis up) an outside neighbour must hold before it vetoes. A sliver of water at
     * the bottom of the air over a deck that a wave washes does not count; a cell that is really in the sea does.
     */
    public static final double SUBMERGED_TOLERANCE = 0.25;

    public static final PartialCellRule NONE = new PartialCellRule(new int[0], new int[0][], new int[0][], new double[0], new int[0][]);

    /**
     * Candidate partial cells (grid index, ascending): at least one uncovered face meets a compartment, or meets the
     * uncovered face of another partial cell (a transitive candidate).
     */
    private final int[] cells;
    /** Per candidate: the compartment cells across its uncovered faces, and their compartment ids. */
    private final int[][] neighbours;
    private final int[][] compartments;
    /**
     * Per candidate: the sea level (ship-frame height) above which an outside neighbour across an uncovered face counts
     * as sea and vetoes; {@code +∞} when no uncovered face is open to the outside.
     */
    private final double[] vetoAbove;
    /** {@link #vetoAbove} sorted, for {@link #submergedCount}. */
    private final double[] sortedVeto;
    /** Per candidate: the candidates whose empty part meets its own (uncovered face against uncovered face). */
    private final int[][] links;

    private PartialCellRule(int[] cells, int[][] neighbours, int[][] compartments, double[] vetoAbove, int[][] links) {
        this.cells = cells;
        this.neighbours = neighbours;
        this.compartments = compartments;
        this.vetoAbove = vetoAbove;
        this.links = links;
        this.sortedVeto = vetoAbove.clone();
        Arrays.sort(sortedVeto);
    }

    public static PartialCellRule of(HullAnalysis analysis) {
        HullGrid g = analysis.grid();
        int[] partial = g.partialCells();
        if (partial.length == 0) {
            return NONE;
        }
        HullVec up = analysis.up();
        // the lowest corner of a cell relative to its center, along up
        double halfExtent = 0.5 * (Math.abs(up.x()) + Math.abs(up.y()) + Math.abs(up.z()));
        int[] cells = new int[partial.length];
        int[][] nb = new int[partial.length][];
        int[][] cp = new int[partial.length][];
        double[] veto = new double[partial.length];
        int[][] partialNb = new int[partial.length][];
        int n = 0;
        int[] tmpN = new int[6];
        int[] tmpC = new int[6];
        int[] tmpP = new int[6];
        for (int cell : partial) {
            int faces = g.uncoveredFaces(cell);
            int x = g.x(cell), y = g.y(cell), z = g.z(cell);
            int k = 0;
            int kp = 0;
            double lowestOutside = Double.POSITIVE_INFINITY;
            for (int f = 0; f < 6; f++) {
                if ((faces & (1 << f)) == 0) continue;
                int nx = x + CellFaces.DX[f], ny = y + CellFaces.DY[f], nz = z + CellFaces.DZ[f];
                int ni = -1;
                int c = -1;
                if (g.inBounds(nx, ny, nz)) {
                    ni = g.index(nx, ny, nz);
                    if (g.kind(ni) != CellKind.AIR) {
                        // the empty parts of two partial blocks meet only where both faces are uncovered (f ^ 1 is the
                        // opposite face in CellFaces order: down/up, north/south, west/east)
                        if ((g.uncoveredFaces(ni) & (1 << (f ^ 1))) != 0) {
                            tmpP[kp++] = ni;
                        }
                        continue;
                    }
                    c = analysis.compartmentOf(ni);
                }
                if (c < 0) {
                    double center = up.x() * (g.originX() + nx + 0.5) + up.y() * (g.originY() + ny + 0.5)
                            + up.z() * (g.originZ() + nz + 0.5);
                    lowestOutside = Math.min(lowestOutside, center - halfExtent);
                } else {
                    tmpN[k] = ni;
                    tmpC[k] = c;
                    k++;
                }
            }
            if (k == 0 && kp == 0) continue;
            cells[n] = cell;
            nb[n] = Arrays.copyOf(tmpN, k);
            cp[n] = Arrays.copyOf(tmpC, k);
            partialNb[n] = Arrays.copyOf(tmpP, kp);
            veto[n] = lowestOutside + SUBMERGED_TOLERANCE;
            n++;
        }
        // grid index -> candidate index (cells is ascending because partialCells() is)
        int[][] links = new int[n][];
        for (int i = 0; i < n; i++) {
            int[] l = new int[partialNb[i].length];
            int m = 0;
            for (int ni : partialNb[i]) {
                int j = Arrays.binarySearch(cells, 0, n, ni);
                if (j >= 0) l[m++] = j;
            }
            links[i] = Arrays.copyOf(l, m);
        }
        return new PartialCellRule(Arrays.copyOf(cells, n), Arrays.copyOf(nb, n), Arrays.copyOf(cp, n), Arrays.copyOf(veto, n), links);
    }

    /** Number of partial cells that may join a region (touching a compartment or another partial cell's empty part). */
    public int candidateCount() {
        return cells.length;
    }

    /**
     * How many candidates the sea at {@code sea} (ship-frame height) vetoes. The region set changes with this number
     * (or with the dry cells) only, so the runtime compares it each tick instead of rebuilding.
     */
    public int submergedCount(double sea) {
        if (!(sea > Double.NEGATIVE_INFINITY)) {
            return 0;
        }
        int lo = 0, hi = sortedVeto.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sortedVeto[mid] < sea) lo = mid + 1; else hi = mid;
        }
        return lo;
    }

    /** {@link #additions(List, double)} with every outside neighbour taken as sea (the strictest case). */
    public BitSet[] additions(List<BitSet> dry) {
        return additions(dry, Double.POSITIVE_INFINITY);
    }

    /**
     * The partial cells to add to each compartment's region, given its dry cells and the sea right now.
     *
     * @param dry dry cells (grid indices) per compartment id, as {@code FloodSimulation#dryCells} returns them
     * @param sea sea level in ship-frame height along the analysis up (very low or {@code -∞}: no sea at the hull)
     * @return one set per compartment id (grid indices), empty where nothing joins
     */
    public BitSet[] additions(List<BitSet> dry, double sea) {
        BitSet[] out = new BitSet[dry.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = new BitSet();
        }
        int[] target = new int[cells.length];
        Arrays.fill(target, -1);
        int[] queue = new int[cells.length];
        int tail = 0;
        for (int i = 0; i < cells.length; i++) {
            if (vetoAbove[i] < sea || neighbours[i].length == 0) {
                continue;
            }
            int t = Integer.MAX_VALUE;
            boolean allDry = true;
            for (int k = 0; k < neighbours[i].length; k++) {
                int c = compartments[i][k];
                if (c >= dry.size() || !dry.get(c).get(neighbours[i][k])) {
                    allDry = false;
                    break;
                }
                t = Math.min(t, c);
            }
            if (allDry) {
                target[i] = t;
                queue[tail++] = i;
            }
        }
        // HV2: a partial cell touching no compartment joins through a joined partial cell whose empty part meets its own
        for (int head = 0; head < tail; head++) {
            int i = queue[head];
            for (int j : links[i]) {
                if (target[j] < 0 && neighbours[j].length == 0 && !(vetoAbove[j] < sea)) {
                    target[j] = target[i];
                    queue[tail++] = j;
                }
            }
        }
        for (int q = 0; q < tail; q++) {
            out[target[queue[q]]].set(cells[queue[q]]);
        }
        return out;
    }
}
