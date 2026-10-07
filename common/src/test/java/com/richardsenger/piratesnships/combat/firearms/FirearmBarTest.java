package com.richardsenger.piratesnships.combat.firearms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirearmBarTest {

    @Test
    void loadingBarFillsFromEmptyToFullOverTheReloadTime() {
        assertEquals(0, FirearmBar.loadingWidth(0, 60));
        assertEquals(1, FirearmBar.loadingWidth(5, 60), "13 * 5/60 = 1.08");
        assertEquals(7, FirearmBar.loadingWidth(30, 60), "half way: 6.5 rounds up");
        assertEquals(12, FirearmBar.loadingWidth(57, 60));
        assertEquals(13, FirearmBar.loadingWidth(60, 60));
    }

    @Test
    void loadingBarGrowsMonotonically() {
        int last = 0;
        for (int t = 0; t <= 100; t++) {
            int w = FirearmBar.loadingWidth(t, 100);
            assertTrue(w >= last && w <= FirearmBar.MAX_WIDTH, "width at " + t + " was " + w);
            last = w;
        }
        assertEquals(FirearmBar.MAX_WIDTH, last);
    }

    @Test
    void loadingBarClamps() {
        assertEquals(0, FirearmBar.loadingWidth(-5, 60));
        assertEquals(13, FirearmBar.loadingWidth(600, 60));
        assertEquals(13, FirearmBar.loadingWidth(0, 0), "no reload time: full at once");
    }

    @Test
    void stateFollowsLoadedThenLoading() {
        assertEquals(FirearmBar.State.LOADED, FirearmBar.state(true, false));
        assertEquals(FirearmBar.State.LOADED, FirearmBar.state(true, true), "holding on after loading shows loaded");
        assertEquals(FirearmBar.State.LOADING, FirearmBar.state(false, true));
        assertEquals(FirearmBar.State.NONE, FirearmBar.state(false, false));
    }

    @Test
    void visibilityWidthAndColourPerState() {
        assertTrue(FirearmBar.visible(FirearmBar.State.LOADED));
        assertTrue(FirearmBar.visible(FirearmBar.State.LOADING));
        assertFalse(FirearmBar.visible(FirearmBar.State.NONE));

        assertEquals(FirearmBar.MAX_WIDTH, FirearmBar.width(FirearmBar.State.LOADED, -1, 60), "loaded: full");
        assertEquals(7, FirearmBar.width(FirearmBar.State.LOADING, 30, 60));
        assertEquals(0, FirearmBar.width(FirearmBar.State.NONE, -1, 60));

        assertEquals(0xFFAA00, FirearmBar.color(FirearmBar.State.LOADED), "loaded: gold");
        assertEquals(0xFFFFFF, FirearmBar.color(FirearmBar.State.LOADING), "loading: white");
    }
}
