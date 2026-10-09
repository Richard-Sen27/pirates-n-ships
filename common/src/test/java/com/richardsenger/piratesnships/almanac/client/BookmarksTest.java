package com.richardsenger.piratesnships.almanac.client;

import com.richardsenger.piratesnships.almanac.client.Bookmarks.Side;
import com.richardsenger.piratesnships.almanac.client.Bookmarks.Tab;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The chapter bookmarks' layout and hit areas: left edge first, then the right (ALM3, ALM7). */
class BookmarksTest {

    private static final int LEFT = 0;
    private static final int RIGHT = 292;
    private static final int TOP = 8;
    private static final int HEIGHT = 164;
    /** (164 + 2) / (16 + 2) */
    private static final int FIT = 9;
    private static final int STEP = Bookmarks.TAB_H + Bookmarks.GAP;

    private static List<Tab> layout(int count, int selected) {
        return Bookmarks.layout(count, selected, LEFT, RIGHT, TOP, HEIGHT);
    }

    @Test
    void oneEdgeHoldsNineAtTheNormalGap() {
        assertEquals(FIT, Bookmarks.perSide(HEIGHT));
    }

    @Test
    void oneChapterSitsTopLeft() {
        List<Tab> tabs = layout(1, -1);
        assertEquals(1, tabs.size());
        Tab t = tabs.getFirst();
        assertEquals(Side.LEFT, t.side());
        assertEquals(TOP, t.y());
        assertEquals(LEFT + Bookmarks.UNDER_COVER - Bookmarks.TAB_W, t.x());
        assertEquals(LEFT + Bookmarks.UNDER_COVER, t.x() + t.w(), "the inner end lies under the left cover");
        assertEquals(t.x(), t.tipX());
    }

    @Test
    void fiveChaptersRunDownTheLeftEdge() {
        List<Tab> tabs = layout(5, -1);
        for (int i = 0; i < 5; i++) {
            Tab t = tabs.get(i);
            assertEquals(i, t.chapter());
            assertEquals(Side.LEFT, t.side());
            assertEquals(TOP + i * STEP, t.y());
            assertEquals(Bookmarks.TAB_W, t.w());
            assertEquals(Bookmarks.TAB_H, t.h());
        }
        assertSane(tabs, true);
    }

    @Test
    void aFullLeftEdgeLeavesTheRightEmpty() {
        List<Tab> tabs = layout(FIT, -1);
        assertTrue(tabs.stream().allMatch(t -> t.side() == Side.LEFT));
        assertEquals(TOP + (FIT - 1) * STEP, tabs.getLast().y());
        assertSane(tabs, true);
    }

    @Test
    void oneMoreContinuesTopRight() {
        List<Tab> tabs = layout(FIT + 1, -1);
        for (int i = 0; i < FIT; i++) assertEquals(Side.LEFT, tabs.get(i).side());
        Tab t = tabs.get(FIT);
        assertEquals(Side.RIGHT, t.side());
        assertEquals(TOP, t.y());
        assertEquals(RIGHT - Bookmarks.UNDER_COVER, t.x(), "the inner end lies under the right cover");
        assertEquals(t.x() + t.w(), t.tipX());
        assertSane(tabs, true);
    }

    @Test
    void twiceAsManyFillBothEdgesAtTheNormalGap() {
        List<Tab> tabs = layout(2 * FIT, -1);
        for (int i = 0; i < 2 * FIT; i++) {
            Tab t = tabs.get(i);
            assertEquals(i < FIT ? Side.LEFT : Side.RIGHT, t.side());
            assertEquals(TOP + (i % FIT) * STEP, t.y());
        }
        assertSane(tabs, true);
    }

    @Test
    void moreThanBothEdgesHoldShrinkTheGapThenOverlapButStayOnTheEdges() {
        // 20: ten a side, the gap shrinks but nothing overlaps yet
        List<Tab> twenty = layout(20, -1);
        assertEquals(10, twenty.stream().filter(t -> t.side() == Side.LEFT).count());
        assertSane(twenty, true);
        // 21: eleven on the left, they overlap a little, still in chapter order top to bottom
        List<Tab> many = layout(21, -1);
        assertEquals(11, many.stream().filter(t -> t.side() == Side.LEFT).count());
        assertSane(many, false);
        // even 60 stay on the edges
        List<Tab> lots = layout(60, -1);
        assertSane(lots, false);
        assertEquals(TOP + HEIGHT, lots.get(29).y() + lots.get(29).h(), "the last left tab ends at the edge's bottom");
        assertEquals(TOP, lots.get(30).y());
    }

    @Test
    void theOpenChapterSticksOutOnItsOwnSide() {
        List<Tab> tabs = layout(FIT + 3, 2);
        Tab left = tabs.get(2);
        assertEquals(Bookmarks.TAB_W + Bookmarks.SELECTED_OUT, left.w());
        assertEquals(LEFT + Bookmarks.UNDER_COVER, left.x() + left.w(), "the inner end stays under the cover");
        assertEquals(tabs.get(1).tipX() - Bookmarks.SELECTED_OUT, left.tipX(), "it sticks out further left");

        tabs = layout(FIT + 3, FIT + 1);
        Tab right = tabs.get(FIT + 1);
        assertEquals(Side.RIGHT, right.side());
        assertEquals(RIGHT - Bookmarks.UNDER_COVER, right.x());
        assertEquals(tabs.get(FIT).tipX() + Bookmarks.SELECTED_OUT, right.tipX(), "it sticks out further right");
        assertEquals(Bookmarks.TAB_W, tabs.get(2).w());
    }

    @Test
    void hitsFindTheTabUnderThePointOnBothSides() {
        List<Tab> tabs = layout(FIT + 3, 0);
        // left: the open chapter's tab reaches 3 pixels further out
        assertEquals(0, Bookmarks.hit(tabs, LEFT - 5, TOP + 1));
        assertEquals(0, Bookmarks.hit(tabs, LEFT - 22, TOP + 1));
        assertEquals(-1, Bookmarks.hit(tabs, LEFT - 22, TOP + STEP + 1));
        assertEquals(1, Bookmarks.hit(tabs, LEFT - 19, TOP + STEP + 1));
        assertEquals(FIT - 1, Bookmarks.hit(tabs, LEFT - 5, TOP + (FIT - 1) * STEP + 15));
        // right: chapters FIT.. from the top
        assertEquals(FIT, Bookmarks.hit(tabs, RIGHT + 5, TOP + 1));
        assertEquals(FIT + 2, Bookmarks.hit(tabs, RIGHT + 19, TOP + 2 * STEP + 15));
        // gaps, past the tips, the pages and below the last right tab are no hits
        assertEquals(-1, Bookmarks.hit(tabs, LEFT - 5, TOP + 16.5));
        assertEquals(-1, Bookmarks.hit(tabs, RIGHT + 5, TOP + 16.5));
        assertEquals(-1, Bookmarks.hit(tabs, RIGHT + 20, TOP + 1));
        assertEquals(-1, Bookmarks.hit(tabs, LEFT + 10, TOP + 1));
        assertEquals(-1, Bookmarks.hit(tabs, RIGHT - 10, TOP + 1));
        assertEquals(-1, Bookmarks.hit(tabs, RIGHT + 5, TOP + 3 * STEP + 1));
    }

    @Test
    void overlappingTabsHitTheOneDrawnOnTop() {
        List<Tab> tabs = layout(40, -1);
        Tab a = tabs.get(3), b = tabs.get(4);
        assertTrue(b.y() < a.y() + a.h(), "they overlap");
        assertEquals(4, Bookmarks.hit(tabs, LEFT - 5, b.y() + 0.5));
    }

    @Test
    void noChaptersNoTabs() {
        assertTrue(layout(0, -1).isEmpty());
    }

    /** Every tab on its edge, in chapter order top to bottom per side; {@code apart}: no two tabs overlap. */
    private static void assertSane(List<Tab> tabs, boolean apart) {
        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            assertEquals(i, t.chapter());
            assertTrue(t.y() >= TOP && t.y() + t.h() <= TOP + HEIGHT, "tab " + i + " stays on the edge");
            if (t.side() == Side.LEFT) assertEquals(LEFT + Bookmarks.UNDER_COVER, t.x() + t.w());
            else assertEquals(RIGHT - Bookmarks.UNDER_COVER, t.x());
            if (i > 0 && tabs.get(i - 1).side() == t.side()) {
                Tab p = tabs.get(i - 1);
                assertTrue(t.y() > p.y(), "tab " + i + " below tab " + (i - 1));
                if (apart) assertTrue(t.y() >= p.y() + p.h(), "tab " + i + " clear of tab " + (i - 1));
            }
            if (i > 0 && tabs.get(i - 1).side() != t.side()) {
                assertEquals(Side.LEFT, tabs.get(i - 1).side(), "left first, then right");
            }
        }
    }
}
