package com.richardsenger.piratesnships.core.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScrollMathTest {

    @Test
    void shortListsDoNotScroll() {
        assertEquals(0, ScrollMath.maxScroll(3, 5));
        assertEquals(0, ScrollMath.clamp(4, 3, 5));
        assertEquals(100, ScrollMath.knobSize(100, 3, 5));
        assertEquals(0, ScrollMath.knobOffset(100, 3, 5, 2));
        assertEquals(0, ScrollMath.scrollForOffset(100, 3, 5, 50));
    }

    @Test
    void knobShowsTheVisibleShareAndHasAMinimum() {
        assertEquals(50, ScrollMath.knobSize(100, 10, 5));
        assertEquals(ScrollMath.MIN_KNOB, ScrollMath.knobSize(100, 1000, 5));
        assertEquals(4, ScrollMath.knobSize(4, 1000, 5));
    }

    @Test
    void knobRunsFromTopToBottom() {
        // 10 rows, 5 visible: knob 50 px, room 50 px, max scroll 5
        assertEquals(0, ScrollMath.knobOffset(100, 10, 5, 0));
        assertEquals(50, ScrollMath.knobOffset(100, 10, 5, 5));
        assertEquals(20, ScrollMath.knobOffset(100, 10, 5, 2));
        assertEquals(50, ScrollMath.knobOffset(100, 10, 5, 99));
    }

    @Test
    void draggingIsTheInverse() {
        for (int s = 0; s <= 5; s++) {
            assertEquals(s, ScrollMath.scrollForOffset(100, 10, 5, ScrollMath.knobOffset(100, 10, 5, s)));
        }
        assertEquals(0, ScrollMath.scrollForOffset(100, 10, 5, -30));
        assertEquals(5, ScrollMath.scrollForOffset(100, 10, 5, 300));
    }
}
