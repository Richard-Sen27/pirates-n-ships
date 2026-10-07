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
}
