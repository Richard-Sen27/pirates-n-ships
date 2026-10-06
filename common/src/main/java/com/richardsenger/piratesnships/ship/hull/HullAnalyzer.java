package com.richardsenger.piratesnships.ship.hull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Hull analysis (docs/design.md §4.2), a pure function of an immutable {@link HullGrid} and an up vector, so it can
 * run off the server thread.
 *
 * <h2>Spill-height model</h2>
 * The height of a cell is {@code h(c) = up · center(c)}. The outside is unbounded, so every non-solid cell on the
 * border of the grid is directly reachable. The spill height {@code s(c)} of an air cell is the minimum, over all
 * 6-connected air paths from the border to {@code c}, of the highest cell on the path ({@code +∞} without a path). It is
 * computed with a priority flood from the border. Air cells with {@code s(c) > h(c)} lie below their own pour point:
 * these are <b>basin cells</b> (enclosed rooms, the inside of an open hull below its rim, the part of a room below a
 * hole). All other air is outside air. Compartments are the 6-connected components of basin cells.
 *
 * <h2>Openings</h2>
 * Openings (doors, hatches, trapdoors, breaches) are treated as <b>walls</b> for the flood regardless of their state,
 * and become links: between two compartments ({@link CompartmentLink}) or between a compartment and outside air
 * ({@link OutsidePort}). The simulation reads their state live. Consequence: toggling an opening never changes the
 * analysis, and it is always an O(1) update (see {@code FloodSimulation#setOpen}). An open hatch to the sea is
 * therefore rate-limited flooding through a port, not "instant outside air".
 *
 * <h2>Discretization</h2>
 * Basin cells must lie strictly below the pour cell, so the layer of an open hull that is level with the top of its rim
 * counts as outside air. A raft with walls one block high has no basin.
 */
public final class HullAnalyzer {

    private static final double EPS = 1e-9;

    private HullAnalyzer() {
    }

    public static HullAnalysis analyze(HullGrid grid) {
        return analyze(grid, HullVec.UP);
    }

    public static HullAnalysis analyze(HullGrid grid, HullVec upVector) {
        HullVec up = upVector.normalized();
        int sx = grid.sizeX(), sy = grid.sizeY(), sz = grid.sizeZ();
        int n = grid.cellCount();
        double[] h = new double[n];
        boolean[] air = new boolean[n];
        for (int i = 0; i < n; i++) {
            h[i] = grid.height(i, up);
            air[i] = grid.kind(i) == CellKind.AIR;
        }

        // Priority flood from the border.
        double[] spill = new double[n];
        Arrays.fill(spill, Double.NaN);
        boolean[] done = new boolean[n];
        MinHeap heap = new MinHeap(Math.max(16, 2 * (sx * sy + sy * sz + sx * sz)));
        for (int i = 0; i < n; i++) {
            if (!air[i]) continue;
            spill[i] = Double.POSITIVE_INFINITY;
            int x = grid.x(i), y = grid.y(i), z = grid.z(i);
            if (x == 0 || y == 0 || z == 0 || x == sx - 1 || y == sy - 1 || z == sz - 1) {
                heap.push(h[i], i);
            }
        }
        int[] nb = new int[6];
        while (!heap.isEmpty()) {
            double key = heap.peekKey();
            int c = heap.pop();
            if (done[c]) continue;
            done[c] = true;
            spill[c] = key;
            int cnt = neighbors(grid, c, nb);
            for (int k = 0; k < cnt; k++) {
                int m = nb[k];
                if (m >= 0 && air[m] && !done[m]) {
                    heap.push(Math.max(key, h[m]), m);
                }
            }
        }

        // Basin cells and their connected components (deterministic: scan in index order, BFS).
        boolean[] basin = new boolean[n];
        for (int i = 0; i < n; i++) {
            basin[i] = air[i] && spill[i] > h[i] + EPS;
        }
        int[] compOf = new int[n];
        Arrays.fill(compOf, -1);
        List<int[]> members = new ArrayList<>();
        int[] queue = new int[n];
        for (int i = 0; i < n; i++) {
            if (!basin[i] || compOf[i] >= 0) continue;
            int id = members.size();
            int head = 0, tail = 0;
            queue[tail++] = i;
            compOf[i] = id;
            while (head < tail) {
                int c = queue[head++];
                int cnt = neighbors(grid, c, nb);
                for (int k = 0; k < cnt; k++) {
                    int m = nb[k];
                    if (m >= 0 && basin[m] && compOf[m] < 0) {
                        compOf[m] = id;
                        queue[tail++] = m;
                    }
                }
            }
            members.add(Arrays.copyOf(queue, tail));
        }

        int compCount = members.size();
        List<Map<Double, Integer>> pour = new ArrayList<>();
        List<List<OutsidePort>> openingPorts = new ArrayList<>();
        List<List<Integer>> compLinks = new ArrayList<>();
        for (int c = 0; c < compCount; c++) {
            pour.add(new TreeMap<>());
            openingPorts.add(new ArrayList<>());
            compLinks.add(new ArrayList<>());
        }

        // Pour points: outside air cells next to a compartment (one area unit per distinct pair).
        Set<Long> seen = new HashSet<>();
        for (int c = 0; c < compCount; c++) {
            for (int cell : members.get(c)) {
                int cnt = neighbors(grid, cell, nb);
                for (int k = 0; k < cnt; k++) {
                    int m = nb[k];
                    // m < 0 (outside the grid) cannot happen next to a basin cell, border cells are never basin
                    if (m >= 0 && air[m] && !basin[m] && seen.add(((long) c << 32) | m)) {
                        pour.get(c).merge(h[m] - 0.5, 1, Integer::sum);
                    }
                }
            }
        }

        // Openings: links between compartments, or ports to outside air.
        List<CompartmentLink> links = new ArrayList<>();
        for (int o = 0; o < n; o++) {
            if (grid.kind(o) != CellKind.OPENING) continue;
            int cnt = neighbors(grid, o, nb);
            boolean outside = false;
            int[] adj = new int[6];
            int adjCount = 0;
            for (int k = 0; k < cnt; k++) {
                int m = nb[k];
                if (m < 0 || (air[m] && !basin[m])) {
                    outside = true;
                } else if (basin[m]) {
                    int id = compOf[m];
                    boolean dup = false;
                    for (int j = 0; j < adjCount; j++) dup |= adj[j] == id;
                    if (!dup) adj[adjCount++] = id;
                }
            }
            Arrays.sort(adj, 0, adjCount);
            double sill = h[o] - 0.5;
            for (int j = 0; j < adjCount; j++) {
                if (outside) openingPorts.get(adj[j]).add(new OutsidePort(adj[j], sill, 1, o));
                for (int l = j + 1; l < adjCount; l++) {
                    compLinks.get(adj[j]).add(links.size());
                    compLinks.get(adj[l]).add(links.size());
                    links.add(new CompartmentLink(adj[j], adj[l], sill, o));
                }
            }
        }

        List<Compartment> compartments = new ArrayList<>(compCount);
        for (int c = 0; c < compCount; c++) {
            int[] cells = members.get(c);
            int[] sorted = sortByHeight(cells, h);
            double[] hs = new double[cells.length];
            double[] centers = new double[3 * cells.length];
            BitSet bits = new BitSet(n);
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (int i = 0; i < cells.length; i++) {
                int cell = sorted[i];
                hs[i] = h[cell];
                int x = grid.x(cell), y = grid.y(cell), z = grid.z(cell);
                centers[3 * i] = grid.originX() + x + 0.5;
                centers[3 * i + 1] = grid.originY() + y + 0.5;
                centers[3 * i + 2] = grid.originZ() + z + 0.5;
                bits.set(cell);
                minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            }
            List<OutsidePort> ports = new ArrayList<>();
            for (Map.Entry<Double, Integer> e : pour.get(c).entrySet()) {
                ports.add(new OutsidePort(c, e.getKey(), e.getValue(), -1));
            }
            ports.addAll(openingPorts.get(c));
            compartments.add(new Compartment(c, bits, sorted, minX, minY, minZ, maxX, maxY, maxZ,
                    new HeightProfile(hs, centers), List.copyOf(ports), List.copyOf(compLinks.get(c))));
        }
        return new HullAnalysis(grid, up, spill, compOf, compartments, links);
    }

    /** Cells sorted by (height, index): a stable primitive merge sort of the index-ordered input. */
    static int[] sortByHeight(int[] cells, double[] h) {
        int n = cells.length;
        int[] a = cells.clone(), b = new int[n];
        Arrays.sort(a); // index order first, so the stable sort breaks height ties by index
        for (int width = 1; width < n; width *= 2) {
            for (int lo = 0; lo < n; lo += 2 * width) {
                int mid = Math.min(lo + width, n), hi = Math.min(lo + 2 * width, n);
                int i = lo, j = mid, k = lo;
                while (i < mid && j < hi) b[k++] = h[a[j]] < h[a[i]] ? a[j++] : a[i++];
                while (i < mid) b[k++] = a[i++];
                while (j < hi) b[k++] = a[j++];
            }
            int[] t = a; a = b; b = t;
        }
        return a;
    }

    /** Writes the 6 neighbor indices of {@code c} into {@code out} ({@code -1} = outside the grid). Returns 6. */
    static int neighbors(HullGrid g, int c, int[] out) {
        int x = g.x(c), y = g.y(c), z = g.z(c);
        int sx = g.sizeX(), sxz = g.sizeX() * g.sizeZ();
        out[0] = x > 0 ? c - 1 : -1;
        out[1] = x < g.sizeX() - 1 ? c + 1 : -1;
        out[2] = z > 0 ? c - sx : -1;
        out[3] = z < g.sizeZ() - 1 ? c + sx : -1;
        out[4] = y > 0 ? c - sxz : -1;
        out[5] = y < g.sizeY() - 1 ? c + sxz : -1;
        return 6;
    }

    /** Binary min-heap of (double key, int value) on primitive arrays. */
    static final class MinHeap {
        private double[] keys;
        private int[] vals;
        private int size;

        MinHeap(int capacity) {
            keys = new double[capacity];
            vals = new int[capacity];
        }

        boolean isEmpty() {
            return size == 0;
        }

        double peekKey() {
            return keys[0];
        }

        void push(double key, int val) {
            if (size == keys.length) {
                keys = Arrays.copyOf(keys, size * 2);
                vals = Arrays.copyOf(vals, size * 2);
            }
            int i = size++;
            while (i > 0) {
                int p = (i - 1) >>> 1;
                if (keys[p] <= key) break;
                keys[i] = keys[p];
                vals[i] = vals[p];
                i = p;
            }
            keys[i] = key;
            vals[i] = val;
        }

        int pop() {
            int top = vals[0];
            size--;
            if (size > 0) {
                double key = keys[size];
                int val = vals[size];
                int i = 0;
                while (true) {
                    int l = 2 * i + 1;
                    if (l >= size) break;
                    int r = l + 1;
                    int m = r < size && keys[r] < keys[l] ? r : l;
                    if (keys[m] >= key) break;
                    keys[i] = keys[m];
                    vals[i] = vals[m];
                    i = m;
                }
                keys[i] = key;
                vals[i] = val;
            }
            return top;
        }
    }
}
