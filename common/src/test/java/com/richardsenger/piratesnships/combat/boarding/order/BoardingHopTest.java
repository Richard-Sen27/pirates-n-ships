package com.richardsenger.piratesnships.combat.boarding.order;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The boarder's jump over a gap (BRD3) under vanilla's mob physics. */
class BoardingHopTest {

    @Test
    void aLevelJumpLandsWhereItAimsAfterAVanillaFlight() {
        for (double d = 1.0; d <= 3.0; d += 0.5) {
            BoardingHop.Launch l = BoardingHop.launch(d, 0);
            assertEquals(BoardingHop.JUMP, l.vertical(), 1e-12);
            assertEquals(12, l.ticks(), "a vanilla jump is back down after 12 ticks");
            assertEquals(d, BoardingHop.distanceAfter(l.horizontal(), l.ticks()), 1e-9);
        }
    }

    @Test
    void aTwoBlockGapNeedsLessThanASprintJump() {
        // the edge cell's centre to the far deck's edge cell's centre over two blocks of water
        BoardingHop.Launch l = BoardingHop.launch(3.0, 0);
        assertTrue(l.horizontal() < 0.45, "speed " + l.horizontal());
    }

    @Test
    void aDeckOneUpIsCleared() {
        BoardingHop.Launch l = BoardingHop.launch(2.0, 1);
        assertEquals(BoardingHop.JUMP_UP, l.vertical(), 1e-12);
        // the feet are above the higher deck for the last third of the flight before landing on it
        int n = l.ticks();
        assertTrue(BoardingHop.heightAfter(l.vertical(), n - 1) > 1.0);
        assertTrue(BoardingHop.heightAfter(l.vertical(), n) <= 1.0);
        double before = BoardingHop.distanceAfter(l.horizontal(), (int) (n * 2 / 3.0));
        assertTrue(BoardingHop.heightAfter(l.vertical(), (int) (n * 2 / 3.0)) > 1.0 && before < 2.0,
                "over the edge before it is above the deck");
    }

    @Test
    void aDeckOneDownTakesALongerFlight() {
        assertTrue(BoardingHop.launch(2.0, -1).ticks() > BoardingHop.launch(2.0, 0).ticks());
    }
}
