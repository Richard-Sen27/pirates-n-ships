package com.richardsenger.piratesnships.ship.hull.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.ship.hull.runtime.CellSet;
import com.richardsenger.piratesnships.ship.hull.runtime.FloodSurfacePayload;
import java.util.BitSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FLD1: the client's surfaces ease between synced levels, snap for new compartments, and clear. */
class FloodSurfaceStoreTest {

    private static final double EPS = 1e-6;
    private static final UUID SHIP = UUID.randomUUID();

    private static CellSet cells(int minX) {
        BitSet b = new BitSet();
        b.set(0, 18);
        return new CellSet(minX, 60, 0, 3, 2, 3, b);
    }

    private static FloodSurfacePayload payload(float level, CellSet... sets) {
        return new FloodSurfacePayload(SHIP, 0, 2, 0, 10,
                java.util.Arrays.stream(sets).map(s -> new FloodSurfacePayload.Surface(s, level)).toList());
    }

    @Test
    void easesFromTheShownLevelToTheNewOne() {
        FloodSurfaceStore store = new FloodSurfaceStore();
        store.accept(payload(0.5f, cells(0)), 100);
        FloodSurfaceStore.Ship ship = store.ships().iterator().next();
        assertEquals(1.0f, ship.upY(), "up is normalised");
        assertEquals(0.5, ship.surfaces().get(0).level(100), EPS, "a new compartment starts at its level");

        store.accept(payload(1.5f, cells(0)), 110);
        FloodSurfaceStore.Surface s = store.ships().iterator().next().surfaces().get(0);
        assertEquals(0.5, s.level(110), EPS);
        assertEquals(1.0, s.level(115), EPS, "halfway through the interval");
        assertEquals(1.5, s.level(125), EPS);

        // a payload in the middle of an ease continues from where the surface is shown
        store.accept(payload(0.5f, cells(0)), 115);
        assertEquals(1.0, store.ships().iterator().next().surfaces().get(0).level(115), EPS);
    }

    @Test
    void otherCellsSnapAndEmptyClears() {
        FloodSurfaceStore store = new FloodSurfaceStore();
        store.accept(payload(0.5f, cells(0)), 0);
        store.accept(payload(1.5f, cells(1)), 5);
        assertEquals(1.5, store.ships().iterator().next().surfaces().get(0).level(5), EPS, "another compartment: no ease");
        store.accept(payload(1f), 6);
        assertTrue(store.isEmpty());
        store.accept(payload(1f, cells(0)), 7);
        store.remove(SHIP);
        assertTrue(store.isEmpty());
    }

    @Test
    void anchorIsCachedPerLevel() {
        FloodSurfaceStore store = new FloodSurfaceStore();
        store.accept(payload(0.5f, cells(0)), 0);
        FloodSurfaceStore.Surface s = store.ships().iterator().next().surfaces().get(0);
        double[] a = s.anchor(0.5, 0, 1, 0);
        assertTrue(a == s.anchor(0.5, 0, 1, 0));
        assertEquals(1.5, a[0], EPS);
        assertEquals(0.5, a[1], EPS);
        assertEquals(0.75, s.anchor(0.75, 0, 1, 0)[1], EPS);
    }
}
