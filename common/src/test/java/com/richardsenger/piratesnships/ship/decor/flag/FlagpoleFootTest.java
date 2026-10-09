package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleFoot.Pole;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleFoot.Support;
import org.junit.jupiter.api.Test;

import static com.richardsenger.piratesnships.ship.decor.flag.FlagpoleFoot.Support.CROWS_NEST;
import static com.richardsenger.piratesnships.ship.decor.flag.FlagpoleFoot.Support.FLAGPOLE;
import static com.richardsenger.piratesnships.ship.decor.flag.FlagpoleFoot.Support.OTHER;
import static com.richardsenger.piratesnships.ship.decor.flag.FlagpoleFoot.Support.PEDESTAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FLG3's foot rule: what a pole stands on decides its parts and where the cleat sits; height and head are unchanged. */
class FlagpoleFootTest {

    /** A column from offset {@code bottom} upwards; everything outside it is {@code OTHER}. */
    private static Pole read(int bottom, int start, Support... cells) {
        return FlagpoleFoot.read(dy -> {
            int i = start + dy - bottom;
            return i >= 0 && i < cells.length ? cells[i] : OTHER;
        }, 64);
    }

    @Test
    void onAPlainBlockTheLowestPoleBlockCarriesTheCleat() {
        assertEquals(FlagpolePart.SINGLE, FlagpoleFoot.part(OTHER, false));
        assertEquals(FlagpolePart.BOTTOM, FlagpoleFoot.part(OTHER, true));
        assertTrue(FlagpoleFoot.part(OTHER, true).hasAbove());
        Pole pole = read(0, 1, OTHER, FLAGPOLE, FLAGPOLE, FLAGPOLE);
        assertEquals(new Pole(0, 2, 3, 0, OTHER), pole, "walk from the foot");
    }

    @Test
    void onAPedestalThePedestalCarriesTheCleat() {
        assertEquals(FlagpolePart.TOP, FlagpoleFoot.part(PEDESTAL, false), "a one-block pole shows the crown, no cleat");
        assertEquals(FlagpolePart.MIDDLE, FlagpoleFoot.part(PEDESTAL, true), "the lowest of several is a bare middle");
        assertEquals(new Pole(0, 0, 1, -1, PEDESTAL), read(0, 1, PEDESTAL, FLAGPOLE));
        assertEquals(new Pole(-2, 0, 3, -3, PEDESTAL), read(0, 3, PEDESTAL, FLAGPOLE, FLAGPOLE, FLAGPOLE), "walk from the head");
    }

    @Test
    void onACrowsNestTheNestCarriesTheCleatAndThePoleClearsTheHeadroom() {
        assertEquals(FlagpolePart.TOP, FlagpoleFoot.part(CROWS_NEST, false));
        assertEquals(FlagpolePart.MIDDLE, FlagpoleFoot.part(CROWS_NEST, true));
        assertEquals(new Pole(-1, 1, 3, -2, CROWS_NEST), read(0, 3, OTHER, CROWS_NEST, FLAGPOLE, FLAGPOLE, FLAGPOLE),
                "walk from the middle");
        assertTrue(FlagpoleFoot.clearsHeadroom(CROWS_NEST));
        assertFalse(FlagpoleFoot.clearsHeadroom(PEDESTAL));
        assertFalse(FlagpoleFoot.clearsHeadroom(OTHER));
        assertFalse(FlagpoleFoot.clearsHeadroom(FLAGPOLE));
    }

    @Test
    void aFootingCountsNeitherTowardsTheHeightNorAsAHead() {
        // a pedestal on top of a pole is not part of it: the pole's head is the block under the pedestal
        assertEquals(new Pole(0, 1, 2, 0, OTHER), read(0, 0, FLAGPOLE, FLAGPOLE, PEDESTAL, FLAGPOLE));
        // and the pole on that pedestal is its own pole
        assertEquals(new Pole(0, 0, 1, -1, PEDESTAL), read(0, 3, FLAGPOLE, FLAGPOLE, PEDESTAL, FLAGPOLE));
        assertTrue(PEDESTAL.footing() && CROWS_NEST.footing());
        assertFalse(FLAGPOLE.footing() || OTHER.footing());
    }

    @Test
    void aPoleBlockOnAPoleIsUnchanged() {
        assertEquals(FlagpolePart.of(true, false), FlagpoleFoot.part(FLAGPOLE, false));
        assertEquals(FlagpolePart.of(true, true), FlagpoleFoot.part(FLAGPOLE, true));
    }

    @Test
    void theWalkNeedsAPoleAndStopsAtTheLimit() {
        assertThrows(IllegalArgumentException.class, () -> read(0, 0, PEDESTAL));
        Pole capped = FlagpoleFoot.read(dy -> FLAGPOLE, 5);
        assertEquals(-5, capped.foot());
        assertEquals(5, capped.head());
        assertEquals(11, capped.height());
    }
}
