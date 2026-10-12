package com.richardsenger.piratesnships.world.village;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoreFacingTest {

    private static final int PROBE = 24;
    private static final int MAX = 12;

    /** Sea toward {@code sea} from {@code distance} blocks out (a straight coast). */
    private static ShoreFacing.Probe coast(Direction sea, int distance) {
        return (dx, dz) -> dx * sea.getStepX() + dz * sea.getStepZ() >= distance;
    }

    @Test
    void picksEachOfTheFourDirections() {
        for (Direction sea : ShoreFacing.ORDER) {
            Optional<ShoreFacing.Shore> shore = ShoreFacing.choose(coast(sea, 5), PROBE, MAX);
            assertTrue(shore.isPresent(), sea.toString());
            assertEquals(sea, shore.get().sea());
            assertEquals(5, shore.get().distance());
            assertEquals(PROBE - 4, shore.get().waterColumns());
        }
    }

    @Test
    void noWaterOrTooFarGivesNothing() {
        assertTrue(ShoreFacing.choose((dx, dz) -> false, PROBE, MAX).isEmpty(), "dry land");
        assertTrue(ShoreFacing.choose(coast(Direction.NORTH, MAX + 1), PROBE, MAX).isEmpty(), "sea beyond the limit");
        assertTrue(ShoreFacing.choose(coast(Direction.NORTH, MAX), PROBE, MAX).isPresent(), "sea at the limit");
    }

    @Test
    void candidateInTheWaterGivesNothing() {
        assertTrue(ShoreFacing.choose((dx, dz) -> true, PROBE, MAX).isEmpty());
    }

    @Test
    void openSeaBeatsANearPond() {
        // a 2-block pond 2 blocks west, the open sea 8 blocks east
        ShoreFacing.Probe probe = (dx, dz) -> dz == 0 && (dx == -2 || dx == -3) || dx >= 8;
        assertEquals(Direction.EAST, ShoreFacing.choose(probe, PROBE, MAX).orElseThrow().sea());
    }

    @Test
    void tiesGoToTheNearerShoreThenTheFixedOrder() {
        // a headland with ten water columns each way: north from 6, south from 3
        ShoreFacing.Probe nearSouth = (dx, dz) -> dx == 0 && (dz <= -6 && dz >= -6 - 9 || dz >= 3 && dz <= 3 + 9);
        assertEquals(Direction.SOUTH, ShoreFacing.choose(nearSouth, PROBE, MAX).orElseThrow().sea(), "same water, nearer shore");
        ShoreFacing.Probe both = (dx, dz) -> dx == 0 && Math.abs(dz) >= 4;
        assertEquals(Direction.NORTH, ShoreFacing.choose(both, PROBE, MAX).orElseThrow().sea(), "full tie: north first");
    }

    @Test
    void rotationTurnsTheNorthSideTowardTheSea() {
        for (Direction sea : ShoreFacing.ORDER) {
            Rotation r = ShoreFacing.rotationFacing(sea);
            assertEquals(sea, r.rotate(Direction.NORTH), sea.toString());
        }
        assertEquals(Rotation.NONE, ShoreFacing.rotationFacing(Direction.NORTH));
    }

    /** STR8: the ranked sides start with {@link ShoreFacing#choose}'s pick and hold every side with sea in reach. */
    @Test
    void rankedListsEverySideBestFirst() {
        // a spit: sea to the south from 10 out (15 columns), to the north from 3 out (22), to the east from 6 out (19)
        ShoreFacing.Probe spit = (dx, dz) -> dz <= -3 || dx >= 6 || dz >= 10;
        java.util.List<ShoreFacing.Shore> ranked = ShoreFacing.ranked(spit, PROBE, MAX);
        assertEquals(java.util.List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH),
                ranked.stream().map(ShoreFacing.Shore::sea).toList());
        assertEquals(ShoreFacing.choose(spit, PROBE, MAX).orElseThrow(), ranked.get(0));
        // equal sides keep the order north, east, south, west
        ShoreFacing.Probe island = (dx, dz) -> Math.abs(dx) >= 5 || Math.abs(dz) >= 5;
        assertEquals(java.util.List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST),
                ShoreFacing.ranked(island, PROBE, MAX).stream().map(ShoreFacing.Shore::sea).toList());
        assertTrue(ShoreFacing.ranked((dx, dz) -> true, PROBE, MAX).isEmpty(), "a candidate in the water");
    }
}
