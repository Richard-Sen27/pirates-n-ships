package com.richardsenger.piratesnships.almanac.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The page-turn animation's state (ALM3). */
class PageTurnTest {

    @Test
    void zeroTicksMeansAnInstantTurn() {
        PageTurn turn = new PageTurn();
        assertFalse(turn.start(PageTurn.Direction.FORWARD, 0));
        assertFalse(turn.turning());
        assertEquals(1f, turn.progress(0f));
    }

    @Test
    void aTurnLastsItsTicksAndEndsOnce() {
        PageTurn turn = new PageTurn();
        assertTrue(turn.start(PageTurn.Direction.BACK, 8));
        assertEquals(PageTurn.Direction.BACK, turn.direction());
        assertEquals(0f, turn.progress(0f));
        for (int i = 1; i < 8; i++) {
            assertFalse(turn.tick(), "still turning at tick " + i);
            assertEquals(i / 8f, turn.progress(0f), 1e-6);
        }
        assertEquals(7.5f / 8f, turn.progress(0.5f), 1e-6);
        assertTrue(turn.tick());
        assertFalse(turn.turning());
        assertFalse(turn.tick());
    }

    @Test
    void aSecondTurnWaitsForTheFirst() {
        PageTurn turn = new PageTurn();
        assertTrue(turn.start(PageTurn.Direction.FORWARD, 4));
        assertFalse(turn.start(PageTurn.Direction.BACK, 4));
        assertEquals(PageTurn.Direction.FORWARD, turn.direction());
    }

    @Test
    void finishNowEndsARunningTurn() {
        PageTurn turn = new PageTurn();
        assertFalse(turn.finishNow());
        turn.start(PageTurn.Direction.FORWARD, 4);
        assertTrue(turn.finishNow());
        assertFalse(turn.turning());
    }

    @Test
    void thePageClosesThenTheNewOneOpens() {
        assertEquals(1f, PageTurn.pageWidth(0f), 1e-6);
        assertEquals(0f, PageTurn.pageWidth(0.5f), 1e-6);
        assertEquals(1f, PageTurn.pageWidth(1f), 1e-6);
        float last = 2f;
        for (int i = 0; i < 50; i++) {
            float w = PageTurn.pageWidth(i / 100f);
            assertTrue(w <= last, "closing is monotonic");
            last = w;
        }
        last = -1f;
        for (int i = 50; i <= 100; i++) {
            float w = PageTurn.pageWidth(i / 100f);
            assertTrue(w >= last, "opening is monotonic");
            last = w;
        }
        assertFalse(PageTurn.secondHalf(0.49f));
        assertTrue(PageTurn.secondHalf(0.5f));
    }

    @Test
    void theFoldIsDarkestWhenThePageStandsUpright() {
        assertEquals(0f, PageTurn.shade(0f), 1e-6);
        assertEquals(PageTurn.MAX_SHADE, PageTurn.shade(0.5f), 1e-6);
        assertEquals(0f, PageTurn.shade(1f), 1e-6);
        assertTrue(PageTurn.shade(0.25f) > 0f && PageTurn.shade(0.25f) < PageTurn.MAX_SHADE);
    }
}
