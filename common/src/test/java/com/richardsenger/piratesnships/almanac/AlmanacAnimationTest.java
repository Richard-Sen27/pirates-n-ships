package com.richardsenger.piratesnships.almanac;

import org.junit.jupiter.api.Test;

import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlmanacAnimationTest {

    /** A random that never starts a page turn by chance. */
    private static final IntUnaryOperator NEVER = bound -> bound - 1;
    /** A random that always starts a turn of one page. */
    private static final IntUnaryOperator ALWAYS = bound -> 0;

    @Test
    void restsClosedWhileNotRead() {
        AlmanacAnimation a = new AlmanacAnimation();
        for (int i = 0; i < 50; i++) a.tick(false, ALWAYS);
        assertEquals(0.0f, a.open());
        assertEquals(0.0f, a.flip(), 1e-6);
        assertTrue(a.resting());
    }

    @Test
    void opensOverTenTicksWhileReadAndTurnsAPageAsItOpens() {
        AlmanacAnimation a = new AlmanacAnimation();
        a.tick(true, NEVER);
        assertEquals(0.1f, a.open(), 1e-6);
        assertEquals(1.0f, a.flipTarget(), 1e-6, "a page turns as the reader opens it");
        for (int i = 0; i < 9; i++) a.tick(true, NEVER);
        assertEquals(1.0f, a.open(), 1e-6);
        for (int i = 0; i < 30; i++) a.tick(true, NEVER);
        assertEquals(1.0f, a.flip(), 0.02, "the first page has turned");
        assertEquals(1.0f, a.flipTarget(), 1e-6, "no further page without a chance hit");
    }

    @Test
    void turnsFurtherPagesForwardOnlyWhileFullyOpen() {
        AlmanacAnimation a = new AlmanacAnimation();
        for (int i = 0; i < 9; i++) a.tick(true, ALWAYS);
        assertEquals(1.0f, a.flipTarget(), 1e-6, "no chance turns while still opening");
        float previous = a.flipTarget();
        a.tick(true, ALWAYS);
        assertEquals(previous + 1.0f, a.flipTarget(), 1e-6, "fully open: a chance hit turns 1 + random(3) pages");
        float flip = a.flip();
        for (int i = 0; i < 20; i++) {
            a.tick(true, ALWAYS);
            assertTrue(a.flip() >= flip - 1e-4, "pages only go forward");
            assertTrue(a.flip() - flip <= AlmanacAnimation.MAX_FLIP_SPEED + 1e-4, "at most MAX_FLIP_SPEED a tick");
            flip = a.flip();
        }
    }

    @Test
    void closesAfterTheReaderIsDoneAndInterpolates() {
        AlmanacAnimation a = new AlmanacAnimation();
        for (int i = 0; i < 10; i++) a.tick(true, NEVER);
        a.tick(false, NEVER);
        assertEquals(0.9f, a.open(), 1e-6);
        assertEquals(0.95f, a.open(0.5f), 1e-6, "halfway between the last two ticks");
        for (int i = 0; i < 9; i++) a.tick(false, NEVER);
        assertEquals(0.0f, a.open(), 1e-6);
        for (int i = 0; i < 40; i++) a.tick(false, NEVER);
        assertTrue(a.resting(), "closed and the turning page has settled");
    }

    @Test
    void reopeningTurnsAnotherPage() {
        AlmanacAnimation a = new AlmanacAnimation();
        for (int i = 0; i < 40; i++) a.tick(true, NEVER);
        for (int i = 0; i < 40; i++) a.tick(false, NEVER);
        a.tick(true, NEVER);
        assertEquals(2.0f, a.flipTarget(), 1e-6);
    }

    @Test
    void theTwoLoosePagesTurnHalfACycleApart() {
        assertEquals(0.0f, AlmanacAnimation.pageTurn(0.0f, 0.75f) - AlmanacAnimation.pageTurn(0.5f, 0.25f), 1e-6);
        for (float flip = 0; flip < 3; flip += 0.05f) {
            float p1 = AlmanacAnimation.pageTurn(flip, 0.25f);
            float p2 = AlmanacAnimation.pageTurn(flip, 0.75f);
            assertTrue(p1 >= 0 && p1 <= 1 && p2 >= 0 && p2 <= 1);
        }
        assertEquals(0.0f, AlmanacAnimation.pageTurn(0.0f, 0.0f), 1e-6, "start of the cycle: resting right");
        assertEquals(1.0f, AlmanacAnimation.pageTurn(0.0f, 0.9f), 1e-6, "end of the cycle: resting left");
    }
}
