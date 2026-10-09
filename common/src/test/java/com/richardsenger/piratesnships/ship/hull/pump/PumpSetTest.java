package com.richardsenger.piratesnships.ship.hull.pump;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PumpSetTest {

    private static final BlockPos A = new BlockPos(1, 2, 3), B = new BlockPos(4, 2, 3), C = new BlockPos(9, 9, 9);

    @Test
    void aUseKeepsThePumpWorkingUntilItExpires() {
        PumpSet s = new PumpSet();
        s.replace(List.of(A, B));
        s.use(A, 108);
        assertTrue(s.usedAt(A, 100));
        assertTrue(s.usedAt(A, 107));
        assertFalse(s.usedAt(A, 108), "stops when the use expires");
        assertArrayEquals(new int[] {1, 0}, s.activeCounts(100, 2, p -> false, p -> 0));
        assertNull(s.activeCounts(108, 2, p -> false, p -> 0));
    }

    @Test
    void repeatedUsesExtendAndNeverShorten() {
        PumpSet s = new PumpSet();
        s.add(A);
        s.use(A, 108);
        s.use(A, 104);
        assertTrue(s.usedAt(A, 106));
        s.use(A, 112);
        assertTrue(s.usedAt(A, 111));
    }

    @Test
    void unknownOrRemovedPumpsDoNotWork() {
        PumpSet s = new PumpSet();
        s.add(A);
        s.use(C, 200); // not a pump of this ship
        assertFalse(s.usedAt(C, 100));
        s.use(A, 200);
        s.remove(A);
        assertNull(s.activeCounts(100, 1, p -> true, p -> 0));
        s.add(A);
        assertFalse(s.usedAt(A, 100), "a pump placed again starts idle");
    }

    @Test
    void countsPerCompartmentWithCrewAndPlayersOncePerPump() {
        PumpSet s = new PumpSet();
        s.replace(List.of(A, B, C));
        s.use(A, 200);
        // A is used by a player and manned by crew: still one pump; B is manned; C idle; B drains compartment 1
        int[] counts = s.activeCounts(100, 3, p -> p.equals(A) || p.equals(B), p -> p.equals(B) ? 1 : 0);
        assertArrayEquals(new int[] {1, 1, 0}, counts);
        // a pump whose intake reaches no compartment counts nowhere
        assertNull(s.activeCounts(100, 3, p -> false, p -> -1));
    }

    @Test
    void replaceKeepsUsesOfPumpsThatStay() {
        PumpSet s = new PumpSet();
        s.replace(List.of(A, B));
        s.use(A, 200);
        s.use(B, 200);
        s.replace(List.of(A));
        assertTrue(s.usedAt(A, 100));
        assertFalse(s.usedAt(B, 100));
        s.clearUses();
        assertFalse(s.usedAt(A, 100));
    }

    /** PMP1: the pumps that drained in the last count, whose handles rock; a pump without an intake does not. */
    @Test
    void theWorkingPumpsAreTheOnesThatDrained() {
        PumpSet s = new PumpSet();
        s.replace(List.of(A, B, C));
        s.use(A, 110);
        s.use(C, 110);
        s.activeCounts(100, 1, p -> p.equals(B), p -> p.equals(C) ? -1 : 0);
        assertEquals(Set.of(A, B), s.working());
        s.activeCounts(110, 1, p -> false, p -> 0);
        assertEquals(Set.of(), s.working(), "uses expired");
        s.use(A, 120);
        s.activeCounts(111, 1, p -> false, p -> 0);
        s.remove(A);
        assertEquals(Set.of(), s.working(), "a removed pump");
        s.use(B, 120);
        s.activeCounts(112, 1, p -> false, p -> 0);
        s.clearUses();
        assertEquals(Set.of(), s.working(), "pumps switched off");
    }
}
