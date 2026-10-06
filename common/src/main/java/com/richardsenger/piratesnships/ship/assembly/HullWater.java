package com.richardsenger.piratesnships.ship.assembly;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The simple water rules of spike 1, in one place so the hull analysis can replace them later. Pure: positions in,
 * positions out, the world is reached only through the predicates the caller passes.
 *
 * <ul>
 *   <li><b>Interior</b>: a non-hull cell is inside the hull if, on its own Y layer, it is fenced in by hull blocks
 *   (a 2D flood fill from outside the layer's bounds cannot reach it) and some hull block lies below it in the same
 *   column. This catches open-deck boats (the walls fence the hold in, the bottom is below) without needing a roof.</li>
 *   <li><b>Sea refill</b> (assembly): among the candidate cells (the vacated hull plus its interior), every cell connected
 *   on its layer to a cell that touches outside water sideways, or that has outside water directly above, becomes water.
 *   Layers above the waterline touch no water and stay air.</li>
 * </ul>
 */
public final class HullWater {

    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private HullWater() {
    }

    /** Cells enclosed by the hull, layer by layer (see class doc). Never contains a hull cell. */
    public static Set<BlockPos> interiorCells(Set<BlockPos> hull) {
        Set<BlockPos> interior = new HashSet<>();
        if (hull.isEmpty()) {
            return interior;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : hull) {
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        for (int y = minY; y <= maxY; y++) {
            // Flood the layer from a ring just outside the bounds.
            Set<Long> outside = new HashSet<>();
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            queue.add(new int[] {minX - 1, minZ - 1});
            outside.add(key(minX - 1, minZ - 1));
            while (!queue.isEmpty()) {
                int[] c = queue.poll();
                for (Direction d : HORIZONTAL) {
                    int nx = c[0] + d.getStepX(), nz = c[1] + d.getStepZ();
                    if (nx < minX - 1 || nx > maxX + 1 || nz < minZ - 1 || nz > maxZ + 1) {
                        continue;
                    }
                    if (hull.contains(new BlockPos(nx, y, nz)) || !outside.add(key(nx, nz))) {
                        continue;
                    }
                    queue.add(new int[] {nx, nz});
                }
            }
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!hull.contains(p) && !outside.contains(key(x, z)) && hasHullBelow(hull, x, y, z, minY)) {
                        interior.add(p);
                    }
                }
            }
        }
        return interior;
    }

    /**
     * Which candidate cells the sea flows back into after assembly (see class doc).
     *
     * @param candidates     vacated hull cells and interior cells that are now empty
     * @param isOutsideWater whether a non-candidate cell holds sea water (a water source)
     */
    public static Set<BlockPos> seaRefill(Set<BlockPos> candidates, Predicate<BlockPos> isOutsideWater) {
        Set<BlockPos> filled = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos p : candidates) {
            boolean seed = isWaterOutside(candidates, p.above(), isOutsideWater);
            for (Direction d : HORIZONTAL) {
                seed |= isWaterOutside(candidates, p.relative(d), isOutsideWater);
            }
            if (seed && filled.add(p)) {
                queue.add(p);
            }
        }
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (Direction d : HORIZONTAL) {
                BlockPos n = p.relative(d);
                if (candidates.contains(n) && filled.add(n)) {
                    queue.add(n);
                }
            }
            BlockPos below = p.below();
            if (candidates.contains(below) && filled.add(below)) {
                queue.add(below);
            }
        }
        return filled;
    }

    private static boolean isWaterOutside(Set<BlockPos> candidates, BlockPos p, Predicate<BlockPos> isOutsideWater) {
        return !candidates.contains(p) && isOutsideWater.test(p);
    }

    private static boolean hasHullBelow(Set<BlockPos> hull, int x, int y, int z, int minY) {
        for (int yy = y - 1; yy >= minY; yy--) {
            if (hull.contains(new BlockPos(x, yy, z))) {
                return true;
            }
        }
        return false;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
