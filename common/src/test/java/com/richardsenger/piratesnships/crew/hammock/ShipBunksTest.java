package com.richardsenger.piratesnships.crew.hammock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ShipBunksTest {

    @Test
    void noHammocksNoBunks() {
        assertEquals(0, ShipBunks.limit(0, 1.0));
        assertEquals(0, ShipBunks.limit(0, 10.0));
        assertEquals(0, ShipBunks.limit(-1, 1.0));
    }

    @Test
    void oneBunkPerHammockByDefault() {
        for (int n = 1; n <= 20; n++) {
            assertEquals(n, ShipBunks.limit(n, 1.0), n + " hammocks");
        }
    }

    @Test
    void multiplierRoundsDown() {
        assertEquals(3, ShipBunks.limit(2, 1.5));
        assertEquals(4, ShipBunks.limit(3, 1.5));
        assertEquals(1, ShipBunks.limit(3, 0.5));
        assertEquals(20, ShipBunks.limit(2, 10.0));
    }

    @Test
    void atLeastOneWithAnyHammock() {
        assertEquals(1, ShipBunks.limit(1, 0.1));
        assertEquals(1, ShipBunks.limit(3, 0.1), "0.3 rounds down to 0, but a hammock gives one bunk");
    }

    @Test
    void floatingPointProductsCountInFull() {
        assertEquals(29, ShipBunks.limit(100, 0.29), "100 × 0.29 is 28.999… in doubles");
        assertEquals(7, ShipBunks.limit(10, 0.7));
    }
}
