package com.richardsenger.piratesnships.ship.hull.flooding;

import com.richardsenger.piratesnships.ship.hull.CellKind;
import com.richardsenger.piratesnships.ship.hull.Compartment;
import com.richardsenger.piratesnships.ship.hull.HullAnalysis;
import com.richardsenger.piratesnships.ship.hull.HullAnalyzer;
import com.richardsenger.piratesnships.ship.hull.HullGrid;
import com.richardsenger.piratesnships.ship.hull.HullVec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.richardsenger.piratesnships.ship.hull.TestHulls.closedBox;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The only incremental shortcut is the O(1) opening toggle. This proves, over many seeded random hulls and edit
 * sequences, that it gives bit-identical results to a full re-analysis plus carry-over after every toggle.
 */
class IncrementalEquivalenceTest {

    private static final int SEQUENCES = 150;
    private static final int STEPS = 40;

    @Test
    void openingTogglesMatchFullRecompute() {
        int toggles = 0, edits = 0;
        for (int seed = 0; seed < SEQUENCES; seed++) {
            Random r = new Random(seed);
            HullGrid.Builder b = randomHull(r);
            HullVec up = r.nextBoolean() ? HullVec.UP : new HullVec(r.nextGaussian() * 0.2, 1, r.nextGaussian() * 0.2);
            FloodParams params = new FloodParams(r.nextInt(5) > 0, 0.5 + r.nextDouble() * 2, r.nextDouble() * 0.05);
            HullAnalysis initial = HullAnalyzer.analyze(b.build(), up);
            FloodSimulation inc = new FloodSimulation(initial, params);
            FloodSimulation full = new FloodSimulation(initial, params);
            for (int c = 0; c < initial.compartments().size(); c++) {
                double v = r.nextDouble() * initial.compartments().get(c).volume();
                inc.setVolume(c, v);
                full.setVolume(c, v);
            }
            for (int step = 0; step < STEPS; step++) {
                List<int[]> openings = cellsOfKind(b, CellKind.OPENING);
                if (!openings.isEmpty() && r.nextInt(4) > 0) {
                    int[] o = openings.get(r.nextInt(openings.size()));
                    boolean open = r.nextBoolean();
                    b.set(o[0], o[1], o[2], CellKind.OPENING, open);
                    assertTrue(inc.setOpen(o[0], o[1], o[2], open));
                    full.rebind(HullAnalyzer.analyze(b.build(), up));
                    toggles++;
                } else {
                    // A cell edit: both sides must re-analyze; they must still agree afterwards.
                    int x = r.nextInt(b.sizeX()), y = r.nextInt(b.sizeY()), z = r.nextInt(b.sizeZ());
                    CellKind k = CellKind.values()[r.nextInt(3)];
                    b.set(x, y, z, k, r.nextBoolean());
                    HullAnalysis a = HullAnalyzer.analyze(b.build(), up);
                    assertEquals(inc.rebind(a), full.rebind(a));
                    edits++;
                }
                assertSameLayout(inc.analysis(), full.analysis());
                int ticks = 1 + r.nextInt(30);
                double sea = r.nextDouble() * b.sizeY();
                FloodTickInput in = new FloodTickInput(sea, r.nextInt(3) == 0 ? r.nextDouble() : 0, null,
                        new int[]{r.nextInt(2), r.nextInt(2)});
                for (int t = 0; t < ticks; t++) {
                    FloodReport ri = inc.tick(in), rf = full.tick(in);
                    assertArrayEquals(rf.volumes(), ri.volumes(), 0.0, "seed " + seed + " step " + step);
                    assertEquals(rf.submergedDryVolume(), ri.submergedDryVolume(), 0.0);
                }
                for (int c = 0; c < inc.analysis().compartments().size(); c++) {
                    assertEquals(full.dryCells(c), inc.dryCells(c));
                }
            }
        }
        assertTrue(toggles > SEQUENCES * STEPS / 2, "most steps should be toggles, got " + toggles);
        assertTrue(edits > 0);
    }

    private static void assertSameLayout(HullAnalysis a, HullAnalysis b) {
        assertEquals(b.links(), a.links());
        assertEquals(b.compartments().size(), a.compartments().size());
        for (int i = 0; i < a.compartments().size(); i++) {
            Compartment ca = a.compartments().get(i), cb = b.compartments().get(i);
            assertEquals(cb.cells(), ca.cells());
            assertEquals(cb.ports(), ca.ports());
        }
    }

    private static List<int[]> cellsOfKind(HullGrid.Builder b, CellKind kind) {
        List<int[]> out = new ArrayList<>();
        for (int y = 0; y < b.sizeY(); y++)
            for (int z = 0; z < b.sizeZ(); z++)
                for (int x = 0; x < b.sizeX(); x++)
                    if (b.kind(x, y, z) == kind) out.add(new int[]{x, y, z});
        return out;
    }

    /** A closed or open hull with random bulkheads, decks, doors, hatches and holes. */
    static HullGrid.Builder randomHull(Random r) {
        int sx = 8 + r.nextInt(8), sy = 6 + r.nextInt(5), sz = 6 + r.nextInt(6);
        HullGrid.Builder b = closedBox(HullGrid.builder(sx, sy, sz), 0, 0, 0, sx - 1, sy - 1, sz - 1);
        if (r.nextBoolean()) b.fill(1, sy - 1, 1, sx - 2, sy - 1, sz - 2, CellKind.AIR); // open top
        for (int i = r.nextInt(3); i > 0; i--) {
            int x = 2 + r.nextInt(sx - 4);
            b.fill(x, 1, 1, x, sy - 2, sz - 2, CellKind.SOLID);
        }
        if (r.nextBoolean()) {
            int y = 2 + r.nextInt(sy - 4);
            b.fill(1, y, 1, sx - 2, y, sz - 2, CellKind.SOLID);
        }
        for (int i = 2 + r.nextInt(8); i > 0; i--) {
            int x = r.nextInt(sx), y = r.nextInt(sy), z = r.nextInt(sz);
            if (b.kind(x, y, z) == CellKind.SOLID) b.set(x, y, z, CellKind.OPENING, r.nextBoolean());
        }
        for (int i = r.nextInt(3); i > 0; i--) {
            int x = r.nextInt(sx), y = r.nextInt(sy), z = r.nextInt(sz);
            if (r.nextBoolean()) b.set(x, y, z, CellKind.AIR);
            else b.breach(x, y, z);
        }
        return b;
    }
}
