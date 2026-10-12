package com.richardsenger.piratesnships.law.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** NB2: the market notice board's load rule. */
class NoticeBoardLoadTest {

    @Test
    void noPostingsIsEmpty() {
        assertEquals(NoticeBoardLoad.EMPTY, NoticeBoardLoad.of(0, 5));
        assertEquals(NoticeBoardLoad.EMPTY, NoticeBoardLoad.of(-3, 5));
    }

    @Test
    void oneUpToTheThresholdIsSome() {
        for (int n = 1; n < 5; n++) assertEquals(NoticeBoardLoad.SOME, NoticeBoardLoad.of(n, 5), n + " postings");
    }

    @Test
    void theThresholdAndMoreIsBusy() {
        assertEquals(NoticeBoardLoad.BUSY, NoticeBoardLoad.of(5, 5));
        assertEquals(NoticeBoardLoad.BUSY, NoticeBoardLoad.of(40, 5));
        assertEquals(NoticeBoardLoad.SOME, NoticeBoardLoad.of(5, 6));
    }

    @Test
    void aThresholdOfOneOrLessMakesAnyPostingBusy() {
        assertEquals(NoticeBoardLoad.BUSY, NoticeBoardLoad.of(1, 1));
        assertEquals(NoticeBoardLoad.BUSY, NoticeBoardLoad.of(1, 0));
        assertEquals(NoticeBoardLoad.EMPTY, NoticeBoardLoad.of(0, 0));
    }

    @Test
    void namesAreTheBlockStateValues() {
        assertEquals("empty", NoticeBoardLoad.EMPTY.getSerializedName());
        assertEquals("some", NoticeBoardLoad.SOME.getSerializedName());
        assertEquals("busy", NoticeBoardLoad.BUSY.getSerializedName());
    }
}
