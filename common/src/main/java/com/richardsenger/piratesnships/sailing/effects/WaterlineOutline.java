package com.richardsenger.piratesnships.sailing.effects;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * The outer outline of a hull at the waterline (WAKE1, docs/design.md §5.4 "Wake"), in plot space: given the solid
 * cells of the one block layer the still water cuts, the cells on the outside of the hull, i.e. the solid cells with a
 * side open to the sea. A hollow hull's inner side (the cells facing the hold) is not part of it: the open cells are
 * flood-filled from outside the layer's bounds, so only water the sea can reach counts. Pure; the client reads the plot
 * blocks into a grid once a second per ship and hands it here.
 *
 * <p>Cost: one pass over the layer's bounding rectangle plus a flood fill over its open cells, O(w × d); the starter
 * sloop's 7 × 17 layer is 119 cells, a 32 × 12 hull 384.
 */
public final class WaterlineOutline {

    private WaterlineOutline() {
    }

    /**
     * Outline cells of a layer, as {@code {x, z}} grid indices in row order (x outer, z inner), so the result is
     * deterministic.
     *
     * @param solid {@code solid[x][z]}: whether the hull has a solid block there; all rows the same length
     */
    public static List<int[]> outline(boolean[][] solid) {
        int w = solid.length;
        int d = w == 0 ? 0 : solid[0].length;
        List<int[]> out = new ArrayList<>();
        if (w == 0 || d == 0) {
            return out;
        }
        // outside[x + 1][z + 1]: open and reachable from beyond the layer's bounds (a one-cell margin all round)
        boolean[][] outside = new boolean[w + 2][d + 2];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        outside[0][0] = true;
        queue.add(new int[] {0, 0});
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] s : steps) {
                int nx = c[0] + s[0], nz = c[1] + s[1];
                if (nx < 0 || nz < 0 || nx >= w + 2 || nz >= d + 2 || outside[nx][nz]) {
                    continue;
                }
                boolean inGrid = nx >= 1 && nz >= 1 && nx <= w && nz <= d;
                if (inGrid && solid[nx - 1][nz - 1]) {
                    continue;
                }
                outside[nx][nz] = true;
                queue.add(new int[] {nx, nz});
            }
        }
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                if (!solid[x][z]) {
                    continue;
                }
                for (int[] s : steps) {
                    if (outside[x + 1 + s[0]][z + 1 + s[1]]) {
                        out.add(new int[] {x, z});
                        break;
                    }
                }
            }
        }
        return out;
    }

    /**
     * The layer to read: {@code preferred} (the one the still water cuts) when it has hull blocks, else the nearest one
     * that does, at most {@code below} layers down or {@code above} layers up, down first at equal distance (a hull
     * floating high has its widest row below the waterline mark). {@link Integer#MIN_VALUE} when none has any: the ship
     * is out of the water.
     */
    public static int pickLayer(int preferred, IntPredicate hasCells, int below, int above) {
        if (hasCells.test(preferred)) {
            return preferred;
        }
        for (int i = 1; i <= Math.max(below, above); i++) {
            if (i <= below && hasCells.test(preferred - i)) {
                return preferred - i;
            }
            if (i <= above && hasCells.test(preferred + i)) {
                return preferred + i;
            }
        }
        return Integer.MIN_VALUE;
    }
}
