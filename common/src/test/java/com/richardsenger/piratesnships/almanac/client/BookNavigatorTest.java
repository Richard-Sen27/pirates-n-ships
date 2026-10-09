package com.richardsenger.piratesnships.almanac.client;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Spreads across chapters (ALM3): chapters of 3, 1 and 4 screen pages. */
class BookNavigatorTest {

    private static final int[] COUNTS = {3, 1, 4};
    private static final BookNavigator NAV = new BookNavigator(COUNTS.length, c -> COUNTS[c]);

    private static BookNavigator.Spread at(int chapter, int page) {
        return new BookNavigator.Spread(chapter, page);
    }

    @Test
    void forwardRunsThroughEveryChapter() {
        assertEquals(Optional.of(at(0, 2)), NAV.next(at(0, 0)));
        assertEquals(Optional.of(at(1, 0)), NAV.next(at(0, 2)));
        assertEquals(Optional.of(at(2, 0)), NAV.next(at(1, 0)));
        assertEquals(Optional.of(at(2, 2)), NAV.next(at(2, 0)));
        assertEquals(Optional.empty(), NAV.next(at(2, 2)));
    }

    @Test
    void backOpensThePreviousChaptersLastSpread() {
        assertEquals(Optional.of(at(2, 0)), NAV.previous(at(2, 2)));
        assertEquals(Optional.of(at(1, 0)), NAV.previous(at(2, 0)));
        assertEquals(Optional.of(at(0, 2)), NAV.previous(at(1, 0)));
        assertEquals(Optional.of(at(0, 0)), NAV.previous(at(0, 2)));
        assertEquals(Optional.empty(), NAV.previous(at(0, 0)));
    }

    @Test
    void storedPagesAreClampedToASpread() {
        assertEquals(at(0, 2), NAV.clamp(0, 5));
        assertEquals(at(0, 0), NAV.clamp(0, 1));
        assertEquals(at(2, 2), NAV.clamp(9, 3));
        assertEquals(at(0, 0), NAV.clamp(-1, -1));
        assertEquals(at(1, 0), NAV.clamp(1, 9999));
        assertEquals(at(0, 0), new BookNavigator(0, c -> 1).clamp(3, 3));
    }

    @Test
    void spreadsAreOrderedByChapterThenPage() {
        assertEquals(-1, Integer.signum(at(0, 2).compareTo(at(1, 0))));
        assertEquals(1, Integer.signum(at(2, 0).compareTo(at(1, 0))));
        assertEquals(-1, Integer.signum(at(2, 0).compareTo(at(2, 2))));
    }
}
