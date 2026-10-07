package com.richardsenger.piratesnships.ship.hull.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Bow-to-stern order and the 16-cell cap of the HUD's hull strip (HUD1). */
class CompartmentStripTest {

    private static CompartmentStrip.Entry e(int id, double forward, double height, int volume) {
        return new CompartmentStrip.Entry(id, forward, height, volume, 0, 0, false);
    }

    @Test
    void ordersBowFirstThenLowestFirst() {
        List<CompartmentStrip.Entry> in = List.of(e(0, -5, 0, 10), e(1, 8, 0, 10), e(2, 2, 3, 10), e(3, 2, 1, 10), e(4, 8, 0, 4));
        List<Integer> ids = CompartmentStrip.order(in, 16).stream().map(CompartmentStrip.Entry::id).toList();
        // forward 8 (ids 1 and 4, same height: by id), then forward 2 lowest first (3 below 2), then the stern
        assertEquals(List.of(1, 4, 3, 2, 0), ids);
    }

    @Test
    void fewCompartmentsAreNotMerged() {
        List<CompartmentStrip.Entry> in = new ArrayList<>();
        for (int i = 0; i < 16; i++) in.add(e(i, i, 0, 1 + i));
        assertEquals(16, CompartmentStrip.order(in, 16).size());
        assertEquals(16, CompartmentStrip.cells(in).size());
    }

    @Test
    void capMergesTheSmallestIntoItsSmallerNeighbourAndKeepsTotals() {
        List<CompartmentStrip.Entry> in = new ArrayList<>();
        int volume = 0, breaches = 0;
        double water = 0;
        for (int i = 0; i < 20; i++) {
            int v = 10 + (i * 7) % 13;
            in.add(new CompartmentStrip.Entry(i, -i, 0, v, v / 2.0, i % 3 == 0 ? 1 : 0, i == 5));
            volume += v;
            water += v / 2.0;
            breaches += i % 3 == 0 ? 1 : 0;
        }
        List<CompartmentStrip.Entry> out = CompartmentStrip.order(in, CompartmentStrip.MAX_CELLS);
        assertEquals(16, out.size());
        assertEquals(volume, out.stream().mapToInt(CompartmentStrip.Entry::volume).sum());
        assertEquals(water, out.stream().mapToDouble(CompartmentStrip.Entry::water).sum(), 1e-9);
        assertEquals(breaches, out.stream().mapToInt(CompartmentStrip.Entry::breaches).sum());
        assertEquals(1, out.stream().filter(CompartmentStrip.Entry::pumping).count(), "the pumped compartment still shows");
        for (int i = 1; i < out.size(); i++) {
            assertTrue(out.get(i - 1).forward() > out.get(i).forward(), "the merged strip still runs bow to stern");
        }
    }

    @Test
    void mergeRule() {
        // volumes 5, 1, 3, 9 with a cap of 3: the 1 merges into the 3 (its smaller neighbour), keeping the 3's id
        List<CompartmentStrip.Entry> in = List.of(e(10, 4, 0, 5), e(11, 3, 0, 1), e(12, 2, 0, 3), e(13, 1, 0, 9));
        List<CompartmentStrip.Entry> out = CompartmentStrip.order(in, 3);
        assertEquals(List.of(10, 12, 13), out.stream().map(CompartmentStrip.Entry::id).toList());
        CompartmentStrip.Entry merged = out.get(1);
        assertEquals(4, merged.volume());
        assertEquals((3 * 1 + 2 * 3) / 4.0, merged.forward(), 1e-9, "volume-weighted position");
        // the smallest at the bow end merges into its only neighbour
        List<CompartmentStrip.Entry> end = CompartmentStrip.order(List.of(e(0, 3, 0, 1), e(1, 2, 0, 6), e(2, 1, 0, 2)), 2);
        assertEquals(List.of(1, 2), end.stream().map(CompartmentStrip.Entry::id).toList());
        assertEquals(7, end.get(0).volume());
    }

    @Test
    void mergedCellPumpsWhenEitherPartPumps() {
        CompartmentStrip.Entry a = new CompartmentStrip.Entry(1, 0, 0, 2, 1, 0, true);
        CompartmentStrip.Entry b = new CompartmentStrip.Entry(2, 1, 0, 3, 0, 2, false);
        CompartmentStrip.Entry m = CompartmentStrip.merge(a, b);
        assertTrue(m.pumping());
        assertEquals(2, m.id());
        assertEquals(2, m.breaches());
        assertFalse(CompartmentStrip.merge(b, b).pumping());
    }
}
