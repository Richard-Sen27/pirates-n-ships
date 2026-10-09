package com.richardsenger.piratesnships.almanac.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The chapter bookmarks' layout and hit areas (ALM3). */
class BookmarksTest {

    private static final int EDGE = 292;
    private static final int TOP = 8;
    private static final int HEIGHT = 164;

    @Test
    void fewChaptersGetFullSizeTabsTopToBottom() {
        List<Bookmarks.Tab> tabs = Bookmarks.layout(3, 1, EDGE, TOP, HEIGHT);
        assertEquals(3, tabs.size());
        for (int i = 0; i < 3; i++) {
            Bookmarks.Tab t = tabs.get(i);
            assertEquals(i, t.chapter());
            assertEquals(EDGE - Bookmarks.UNDER_COVER, t.x());
            assertEquals(TOP + i * (Bookmarks.MAX_H + Bookmarks.GAP), t.y());
            assertEquals(Bookmarks.MAX_H, t.h());
        }
        assertEquals(Bookmarks.TAB_W + Bookmarks.SELECTED_OUT, tabs.get(1).w());
        assertEquals(Bookmarks.TAB_W, tabs.get(0).w());
    }

    @Test
    void tabsShrinkToFitTheEdge() {
        List<Bookmarks.Tab> tabs = Bookmarks.layout(12, -1, EDGE, TOP, HEIGHT);
        int h = tabs.getFirst().h();
        assertTrue(h < Bookmarks.MAX_H && h >= Bookmarks.MIN_H);
        Bookmarks.Tab last = tabs.getLast();
        assertTrue(last.y() + last.h() <= TOP + HEIGHT, "the last tab stays on the edge");
        assertEquals(EDGE - Bookmarks.UNDER_COVER, last.x());
    }

    @Test
    void tooManyChaptersOpenASecondColumn() {
        List<Bookmarks.Tab> tabs = Bookmarks.layout(30, -1, EDGE, TOP, HEIGHT);
        int perColumn = (HEIGHT + Bookmarks.GAP) / (Bookmarks.MIN_H + Bookmarks.GAP);
        assertEquals(Bookmarks.MIN_H, tabs.getFirst().h());
        assertEquals(EDGE - Bookmarks.UNDER_COVER + Bookmarks.TAB_W, tabs.get(perColumn).x());
        assertEquals(TOP, tabs.get(perColumn).y());
        for (Bookmarks.Tab t : tabs) assertTrue(t.y() + t.h() <= TOP + HEIGHT);
    }

    @Test
    void hitsFindTheTabUnderThePoint() {
        List<Bookmarks.Tab> tabs = Bookmarks.layout(3, 0, EDGE, TOP, HEIGHT);
        assertEquals(0, Bookmarks.hit(tabs, EDGE + 5, TOP + 1));
        assertEquals(2, Bookmarks.hit(tabs, EDGE + 19, TOP + 2 * 18 + 15));
        // the gap between tabs, past the tip, and the page itself are no hits
        assertEquals(-1, Bookmarks.hit(tabs, EDGE + 5, TOP + 16.5));
        assertEquals(-1, Bookmarks.hit(tabs, EDGE + 20, TOP + 18 + 4));
        assertEquals(-1, Bookmarks.hit(tabs, EDGE - 10, TOP + 1));
        assertEquals(-1, Bookmarks.hit(tabs, EDGE + 5, TOP + 3 * 18 + 1));
        // the open chapter's tab sticks out further
        assertEquals(0, Bookmarks.hit(tabs, EDGE + 21, TOP + 1));
    }

    @Test
    void noChaptersNoTabs() {
        assertTrue(Bookmarks.layout(0, -1, EDGE, TOP, HEIGHT).isEmpty());
    }
}
