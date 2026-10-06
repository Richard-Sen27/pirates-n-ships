package com.richardsenger.piratesnships.ship.assembly;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HullWaterTest {

    /** 5×5 floor at y=0, a wall ring at y=1 and y=2: an open boat with a 3×3 hold on two layers. */
    private static Set<BlockPos> openBoat() {
        Set<BlockPos> hull = new HashSet<>();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                hull.add(new BlockPos(x, 0, z));
                if (x == 0 || x == 4 || z == 0 || z == 4) {
                    hull.add(new BlockPos(x, 1, z));
                    hull.add(new BlockPos(x, 2, z));
                }
            }
        }
        return hull;
    }

    @Test
    void openBoatHoldIsInterior() {
        Set<BlockPos> interior = HullWater.interiorCells(openBoat());
        assertEquals(18, interior.size());
        assertTrue(interior.contains(new BlockPos(2, 1, 2)));
        assertTrue(interior.contains(new BlockPos(1, 2, 3)));
    }

    @Test
    void wallWithGapIsNotInterior() {
        Set<BlockPos> hull = openBoat();
        hull.remove(new BlockPos(0, 2, 2)); // a gap in the upper wall layer
        Set<BlockPos> interior = HullWater.interiorCells(hull);
        assertEquals(9, interior.size(), "only the lower layer stays enclosed");
    }

    @Test
    void flatRaftHasNoInterior() {
        Set<BlockPos> raft = new HashSet<>();
        BlockPos.betweenClosed(0, 0, 0, 4, 0, 4).forEach(p -> raft.add(p.immutable()));
        assertTrue(HullWater.interiorCells(raft).isEmpty());
    }

    @Test
    void enclosedCellsWithoutFloorAreNotInterior() {
        // A ring with no floor below it: a fence, not a hull.
        Set<BlockPos> ring = new HashSet<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                if (x != 1 || z != 1) {
                    ring.add(new BlockPos(x, 0, z));
                }
            }
        }
        assertTrue(HullWater.interiorCells(ring).isEmpty());
    }

    @Test
    void seaRefillFillsLayersBelowTheWaterline() {
        Set<BlockPos> hull = openBoat();
        Set<BlockPos> candidates = new HashSet<>(hull);
        candidates.addAll(HullWater.interiorCells(hull));
        // Sea surface at y=1: outside water on layers 0 and 1, air above.
        Set<BlockPos> filled = HullWater.seaRefill(candidates, p -> p.getY() <= 1 && !candidates.contains(p));
        for (BlockPos p : candidates) {
            assertEquals(p.getY() <= 1, filled.contains(p), "cell " + p);
        }
    }

    @Test
    void seaRefillLeavesDryLandAlone() {
        Set<BlockPos> hull = openBoat();
        assertTrue(HullWater.seaRefill(hull, p -> false).isEmpty());
    }
}
