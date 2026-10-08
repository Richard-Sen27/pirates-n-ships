package com.richardsenger.piratesnships.worldsim.materialize;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckSpotsTest {

    private static final BlockPos RAIL = new BlockPos(0, 4, 0);

    /**
     * A 3×5 hull: hold floor at y=0, deck at y=3, a mast at (1, 4..14, 2) reaching above the deck height limit, and a
     * rail post at (0, 4, 0) that cannot be stood on (a fence: no sturdy top).
     */
    private static Set<BlockPos> hull() {
        Set<BlockPos> b = new HashSet<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 5; z++) {
                b.add(new BlockPos(x, 0, z));
                b.add(new BlockPos(x, 3, z));
            }
        }
        for (int y = 4; y <= 14; y++) b.add(new BlockPos(1, y, 2));
        b.add(new BlockPos(0, 4, 0));
        return b;
    }

    @Test
    void theDeckWinsOverTheHoldAndMastsAndRailsAreLeftOut() {
        List<BlockPos> spots = DeckSpots.candidates(hull(), p -> !p.equals(RAIL), 0, DeckSpots.MAX_DECK_HEIGHT);
        // 15 deck cells, minus the mast column (its top is too high) and the rail post's column (the post is on top)
        assertEquals(13, spots.size(), spots.toString());
        assertTrue(spots.stream().allMatch(p -> p.getY() == 3), spots.toString());
        assertFalse(spots.contains(new BlockPos(1, 3, 2)));
        assertFalse(spots.contains(new BlockPos(0, 3, 0)));
    }

    @Test
    void floorsThatCannotBeStoodOnAndFloorsBelowTheWaterlineDoNotCount() {
        List<BlockPos> spots = DeckSpots.candidates(hull(), p -> p.getZ() != 4 && !p.equals(RAIL), 1, DeckSpots.MAX_DECK_HEIGHT);
        assertTrue(spots.stream().noneMatch(p -> p.getZ() == 4));
        assertTrue(spots.stream().noneMatch(p -> p.getY() == 0));
    }

    @Test
    void sortedAlongTheShip() {
        List<BlockPos> spots = DeckSpots.candidates(hull(), p -> !p.equals(RAIL), 0, 10);
        for (int i = 1; i < spots.size(); i++) {
            BlockPos a = spots.get(i - 1), b = spots.get(i);
            assertTrue(a.getZ() < b.getZ() || (a.getZ() == b.getZ() && a.getX() < b.getX()), a + " before " + b);
        }
    }

    @Test
    void pickSpreadsAndReusesWhenShort() {
        List<BlockPos> c = List.of(new BlockPos(0, 0, 0), new BlockPos(0, 0, 1), new BlockPos(0, 0, 2), new BlockPos(0, 0, 3));
        assertEquals(List.of(c.get(1), c.get(3)), DeckSpots.pick(c, 2));
        assertEquals(6, DeckSpots.pick(c, 6).size());
        assertEquals(c.get(0), DeckSpots.pick(c, 6).get(4));
        assertTrue(DeckSpots.pick(List.of(), 3).isEmpty());
        assertTrue(DeckSpots.pick(c, 0).isEmpty());
    }
}
