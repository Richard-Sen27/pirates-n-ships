package com.richardsenger.piratesnships.ship.hull;

import com.richardsenger.piratesnships.ship.hull.flooding.FloodParams;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodSimulation;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodTickInput;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static com.richardsenger.piratesnships.ship.hull.TestHulls.closedBox;
import static org.junit.jupiter.api.Assertions.*;

class HeightProfileAndScaleTest {

    @Test
    void levelAndVolumeAreInverse() {
        HullVec up = new HullVec(0.3, 1, -0.2);
        HullAnalysis a = HullAnalyzer.analyze(closedBox(HullGrid.builder(9, 7, 6), 0, 0, 0, 8, 6, 5).build(), up);
        HeightProfile p = a.compartments().get(0).profile();
        Random r = new Random(1);
        for (int i = 0; i < 1000; i++) {
            double v = r.nextDouble() * p.cellCount();
            assertEquals(v, p.volumeAt(p.levelAt(v)), 1e-9);
            assertEquals(p.volumeAt(p.levelAt(v)), p.moments(p.levelAt(v))[0], 1e-9);
        }
        assertEquals(0, p.volumeAt(p.bottom()), 1e-12);
        assertEquals(p.cellCount(), p.volumeAt(p.top()), 1e-9);
    }

    /** A 64×32×64 hull with three decks, bulkheads, doors, hatches and a few holes. Reports time; never asserts it. */
    @Test
    void largeHullRuns() {
        int sx = 64, sy = 32, sz = 64;
        HullGrid.Builder b = closedBox(HullGrid.builder(sx, sy, sz), 0, 0, 0, sx - 1, sy - 1, sz - 1);
        b.fill(1, sy - 1, 1, sx - 2, sy - 1, sz - 2, CellKind.AIR); // open top deck area
        for (int y : new int[]{8, 16, 24}) {
            b.fill(1, y, 1, sx - 2, y, sz - 2, CellKind.SOLID);
            for (int x = 6; x < sx - 1; x += 12) b.set(x, y, 30, CellKind.OPENING, x % 24 == 6);
        }
        for (int x = 8; x < sx - 1; x += 8) {
            b.fill(x, 1, 1, x, sy - 2, sz - 2, CellKind.SOLID);
            for (int y = 1; y < sy - 1; y += 8) b.set(x, y, 20, CellKind.OPENING, true).set(x, y + 1, 20, CellKind.OPENING, true);
        }
        b.breach(0, 3, 10).set(sx - 1, 12, 40, CellKind.AIR);
        HullGrid grid = b.build();
        HullVec heel = new HullVec(0.1, 1, 0.05);

        HullAnalysis a = null;
        for (int i = 0; i < 2; i++) a = HullAnalyzer.analyze(grid, heel); // warm-up
        int runs = 5;
        long t0 = System.nanoTime();
        for (int i = 0; i < runs; i++) a = HullAnalyzer.analyze(grid, heel);
        double analyzeMs = (System.nanoTime() - t0) / 1e6 / runs;

        FloodSimulation s = new FloodSimulation(a, FloodParams.DEFAULTS);
        int ticks = 1000;
        long t1 = System.nanoTime();
        for (int t = 0; t < ticks; t++) s.tick(FloodTickInput.calm(10));
        double tickUs = (System.nanoTime() - t1) / 1e3 / ticks;

        System.out.printf("[hull perf] 64x32x64: analysis %.1f ms, %d compartments, %d links; flood tick %.1f us; water %.1f%n",
                analyzeMs, a.compartments().size(), a.links().size(), tickUs, s.totalVolume());
        assertTrue(a.compartments().size() > 10);
        assertTrue(s.totalVolume() > 0, "the breach below the sea lets water in");
    }
}
