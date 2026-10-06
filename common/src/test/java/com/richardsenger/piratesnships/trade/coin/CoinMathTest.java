package com.richardsenger.piratesnships.trade.coin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CoinMathTest {

    @Test
    void countsAllSlots() {
        assertEquals(0, CoinMath.count(new int[0]));
        assertEquals(103, CoinMath.count(new int[]{64, 0, 39, -5}));
    }

    @Test
    void takeIsAllOrNothing() {
        assertNull(CoinMath.planTake(new int[]{10, 5}, 16));
        assertNull(CoinMath.planTake(new int[]{10}, -1));
        assertArrayEquals(new int[]{0, 0}, CoinMath.planTake(new int[]{10, 5}, 0));
    }

    @Test
    void takeUsesSmallStacksFirst() {
        assertArrayEquals(new int[]{2, 5, 0}, CoinMath.planTake(new int[]{64, 5, 0}, 7));
        assertArrayEquals(new int[]{64, 5, 0}, CoinMath.planTake(new int[]{64, 5, 0}, 69));
        assertArrayEquals(new int[]{3, 0}, CoinMath.planTake(new int[]{10, 10}, 3), "ties by slot order");
    }

    @Test
    void splitFillsFullStacksFirst() {
        assertEquals(List.of(), CoinMath.split(0, 64));
        assertEquals(List.of(64, 64, 2), CoinMath.split(130, 64));
        assertEquals(List.of(1, 1), CoinMath.split(2, 0), "a stack size below 1 counts as 1");
    }
}
