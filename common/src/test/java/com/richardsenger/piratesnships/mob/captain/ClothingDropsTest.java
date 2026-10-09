package com.richardsenger.piratesnships.mob.captain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ART9: the slain captain's clothing drop rule ({@code mobs.captain.clothing_drop_chance}, per piece). */
class ClothingDropsTest {

    private static final List<String> PIECES = List.of("coat", "breeches", "boots");

    @Test
    void zeroDropsNothingAndOneDropsEverything() {
        assertEquals(List.of(), ClothingDrops.roll(PIECES, 0.0, () -> 0.0));
        assertEquals(PIECES, ClothingDrops.roll(PIECES, 1.0, () -> 0.999));
        assertEquals(List.of(), ClothingDrops.roll(PIECES, -0.5, () -> 0.0), "below 0 counts as 0");
    }

    @Test
    void aPieceDropsWhenItsRollIsBelowTheChance() {
        double[] rolls = {0.1, 0.5, 0.34};
        int[] i = {0};
        assertEquals(List.of("coat", "boots"), ClothingDrops.roll(PIECES, 0.35, () -> rolls[i[0]++]));
        assertEquals(3, i[0], "one roll per piece");
    }

    /** Over many seeded kills each piece drops at about the chance, independently of the others. */
    @Test
    void eachPieceDropsAtTheChanceOnItsOwn() {
        Random random = new Random(20261009L);
        int kills = 20000;
        int[] counts = new int[3];
        int all = 0;
        for (int k = 0; k < kills; k++) {
            List<String> dropped = ClothingDrops.roll(PIECES, 0.35, random::nextDouble);
            for (int p = 0; p < 3; p++) if (dropped.contains(PIECES.get(p))) counts[p]++;
            if (dropped.size() == 3) all++;
        }
        for (int p = 0; p < 3; p++) {
            double rate = counts[p] / (double) kills;
            assertTrue(Math.abs(rate - 0.35) < 0.015, PIECES.get(p) + " rate " + rate);
        }
        double allRate = all / (double) kills;
        assertTrue(Math.abs(allRate - 0.35 * 0.35 * 0.35) < 0.01, "the full set at chance^3, got " + allRate);
    }
}
