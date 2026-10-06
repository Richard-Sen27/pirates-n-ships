package com.richardsenger.piratesnships.ship.hull;

import static com.richardsenger.piratesnships.ship.hull.CellFaces.ALL;
import static com.richardsenger.piratesnships.ship.hull.CellFaces.DOWN;
import static com.richardsenger.piratesnships.ship.hull.CellFaces.EAST;
import static com.richardsenger.piratesnships.ship.hull.CellFaces.NORTH;
import static com.richardsenger.piratesnships.ship.hull.CellFaces.SOUTH;
import static com.richardsenger.piratesnships.ship.hull.CellFaces.UP;
import static com.richardsenger.piratesnships.ship.hull.CellFaces.WEST;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The partial-block region rule (docs/design.md §4.3) on small grids with an air margin as the outside. */
class PartialCellRuleTest {

    private static final int SIDES = NORTH | SOUTH | WEST | EAST;

    /** 7×7×7 grid, closed shell 1..5, interior 2..4 (one compartment), outside air around it. */
    private static HullGrid.Builder room() {
        return TestHulls.closedBox(HullGrid.builder(7, 7, 7), 1, 1, 1, 5, 5, 5);
    }

    /** 7×9×7 grid, shell y 1..7 with a solid deck at y 4: lower room y 2..3, upper room y 5..6. */
    private static HullGrid.Builder twoDecks() {
        return TestHulls.closedBox(HullGrid.builder(7, 9, 7), 1, 1, 1, 5, 7, 5).fill(2, 4, 2, 4, 4, 4, CellKind.SOLID);
    }

    private static List<BitSet> allDry(HullAnalysis a) {
        List<BitSet> dry = new ArrayList<>();
        for (Compartment c : a.compartments()) {
            dry.add((BitSet) c.cells().clone());
        }
        return dry;
    }

    private static boolean joins(HullAnalysis a, List<BitSet> dry, int compartment, int x, int y, int z) {
        return PartialCellRule.of(a).additions(dry)[compartment].get(a.grid().index(x, y, z));
    }

    @Test
    void gridRecordsPartialCellsAndTheirFaces() {
        HullGrid g = room().partial(3, 1, 3, UP | SIDES).partial(2, 2, 2, ALL).build();
        assertTrue(g.isPartial(3, 1, 3));
        assertFalse(g.isPartial(2, 2, 2), "an air cell cannot be partial");
        assertEquals(UP | SIDES, g.uncoveredFaces(g.index(3, 1, 3)));
        assertArrayEquals(new int[] {g.index(3, 1, 3)}, g.partialCells());
        assertEquals(g, g.toBuilder().build());
        assertFalse(g.equals(room().build()), "partial marks are part of the snapshot");
        HullGrid reset = g.toBuilder().set(3, 1, 3, CellKind.SOLID).build();
        assertFalse(reset.isPartial(3, 1, 3), "set() clears the mark");
        assertEquals(room().build(), reset);
    }

    @Test
    void partialCellsDoNotChangeTheAnalysis() {
        HullAnalysis plain = HullAnalyzer.analyze(room().set(2, 2, 2, CellKind.SOLID).build());
        HullAnalysis marked = HullAnalyzer.analyze(room().set(2, 2, 2, CellKind.SOLID).partial(2, 2, 2, UP | SOUTH | EAST)
                .partial(3, 1, 3, UP | SIDES).build());
        assertEquals(plain.compartments().size(), marked.compartments().size());
        assertEquals(plain.compartments().get(0).cells(), marked.compartments().get(0).cells());
        assertEquals(plain.compartments().get(0).ports(), marked.compartments().get(0).ports());
        assertEquals(26, marked.compartments().get(0).volume());
    }

    @Test
    void bottomSlabFloorJoins() {
        HullAnalysis a = HullAnalyzer.analyze(room().partial(3, 1, 3, UP | SIDES).build());
        assertEquals(1, PartialCellRule.of(a).candidateCount());
        assertTrue(joins(a, allDry(a), 0, 3, 1, 3));
    }

    @Test
    void topSlabAsHullBottomNeverJoins() {
        // its empty half is below, in the sea
        HullAnalysis a = HullAnalyzer.analyze(room().partial(3, 1, 3, DOWN | SIDES).build());
        assertEquals(0, PartialCellRule.of(a).candidateCount());
        assertFalse(joins(a, allDry(a), 0, 3, 1, 3));
    }

    @Test
    void cellAtTheGridEdgeIsOutside() {
        HullAnalysis a = HullAnalyzer.analyze(TestHulls.box5().partial(2, 0, 2, UP | DOWN).build());
        assertEquals(0, PartialCellRule.of(a).candidateCount());
    }

    @Test
    void stairInACabinJoins() {
        HullAnalysis a = HullAnalyzer.analyze(room().set(2, 2, 2, CellKind.SOLID).partial(2, 2, 2, UP | SOUTH | EAST).build());
        assertTrue(joins(a, allDry(a), 0, 2, 2, 2));
    }

    @Test
    void stairBesideItsWallIsNeutralThere() {
        // the uncovered west side faces the wall (solid, neutral); up and south face the cabin
        HullAnalysis a = HullAnalyzer.analyze(room().set(2, 2, 2, CellKind.SOLID).partial(2, 2, 2, UP | SOUTH | WEST).build());
        assertTrue(joins(a, allDry(a), 0, 2, 2, 2));
    }

    @Test
    void partialCellFacingOnlyBlocksDoesNotJoin() {
        // a floor slab under a wall block: its uncovered top meets solid, it touches no dry cell
        HullAnalysis a = HullAnalyzer.analyze(room().set(2, 2, 2, CellKind.SOLID).partial(2, 1, 2, UP).build());
        assertEquals(0, PartialCellRule.of(a).candidateCount());
    }

    @Test
    void deckHatchUnderTheSkyNeverJoins() {
        // an opening in the roof: union of both states uncovers all faces, the top one is outside air
        HullAnalysis a = HullAnalyzer.analyze(room().set(3, 5, 3, CellKind.OPENING).partial(3, 5, 3, ALL).build());
        assertEquals(0, PartialCellRule.of(a).candidateCount());
    }

    @Test
    void hatchBetweenTwoDryDecksJoinsTheLowerCompartment() {
        HullAnalysis a = HullAnalyzer.analyze(twoDecks().set(3, 4, 3, CellKind.OPENING).partial(3, 4, 3, ALL).build());
        assertEquals(2, a.compartments().size());
        int lower = a.compartmentAt(3, 3, 3), upper = a.compartmentAt(3, 5, 3);
        BitSet[] add = PartialCellRule.of(a).additions(allDry(a));
        int idx = a.grid().index(3, 4, 3);
        assertTrue(add[Math.min(lower, upper)].get(idx));
        assertFalse(add[Math.max(lower, upper)].get(idx), "joins one region only");
    }

    @Test
    void partialCellBetweenDryAndFloodedRoomDoesNotJoin() {
        HullAnalysis a = HullAnalyzer.analyze(twoDecks().set(3, 4, 3, CellKind.OPENING).partial(3, 4, 3, ALL).build());
        int lower = a.compartmentAt(3, 3, 3);
        List<BitSet> dry = allDry(a);
        dry.get(lower).clear();
        BitSet[] add = PartialCellRule.of(a).additions(dry);
        for (BitSet b : add) {
            assertFalse(b.get(a.grid().index(3, 4, 3)));
        }
    }

    @Test
    void floorSlabLeavesWhenTheLayerAboveFloods() {
        HullAnalysis a = HullAnalyzer.analyze(room().partial(3, 1, 3, UP | SIDES).partial(2, 1, 2, UP | SIDES).build());
        List<BitSet> dry = allDry(a);
        assertTrue(joins(a, dry, 0, 3, 1, 3));
        HullGrid g = a.grid();
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                dry.get(0).clear(g.index(x, 2, z)); // the bottom layer is flooded
            }
        }
        BitSet[] add = PartialCellRule.of(a).additions(dry);
        assertTrue(add[0].isEmpty(), "both floor slabs leave with the flooded layer: " + add[0]);
    }

    @Test
    void halfSlabWallWithItsEmptySideOutsideNeverJoins() {
        // a vertical half block in the west wall whose empty half faces the sea (west), its east side the room
        HullAnalysis a = HullAnalyzer.analyze(room().partial(1, 3, 3, WEST | UP | DOWN | NORTH | SOUTH).build());
        assertEquals(0, PartialCellRule.of(a).candidateCount());
        HullAnalysis in = HullAnalyzer.analyze(room().partial(1, 3, 3, EAST | UP | DOWN | NORTH | SOUTH).build());
        assertTrue(joins(in, allDry(in), 0, 1, 3, 3), "empty half facing the room joins");
    }

    @Test
    void openHullRimSlabIsOutside() {
        // a bottom slab on top of an open hull's wall: its top is outside air
        HullGrid.Builder b = TestHulls.openBox(HullGrid.builder(7, 7, 7), 1, 1, 1, 5, 5, 5);
        HullAnalysis a = HullAnalyzer.analyze(b.partial(1, 5, 3, UP | SIDES).build());
        assertEquals(0, PartialCellRule.of(a).candidateCount());
    }
}
