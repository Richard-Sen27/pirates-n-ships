package com.richardsenger.piratesnships.ship.hull.pump;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PMP1: when the pump's pumping flag goes to clients ({@link PumpActivity}). */
class PumpActivityTest {

    @Test
    void anIdlePumpSendsNothing() {
        PumpActivity a = new PumpActivity();
        for (long t = 0; t < 200; t++) {
            assertFalse(a.tick(t));
        }
        assertFalse(a.synced());
        assertEquals(0, a.syncs());
    }

    @Test
    void continuousPumpingSendsOneUpdateToStartAndOneToStop() {
        PumpActivity a = new PumpActivity();
        int sent = 0;
        for (long t = 100; t < 400; t++) {
            a.worked(t);
            if (a.tick(t)) sent++;
            if (t == 100) assertTrue(a.synced(), "on at once");
        }
        assertEquals(1, sent, "nothing more while pumping goes on");
        long stopped = 400;
        for (long t = stopped; t < stopped + 40; t++) {
            if (a.tick(t)) {
                sent++;
                assertEquals(stopped + PumpActivity.HOLD_TICKS, t, "off after the hold");
            }
        }
        assertEquals(2, sent);
        assertFalse(a.synced());
    }

    @Test
    void repeatedUsesEveryFourTicksDoNotFlicker() {
        PumpActivity a = new PumpActivity();
        for (long t = 0; t < 200; t++) {
            if (t % 4 == 0) a.worked(t);
            a.tick(t);
            assertTrue(a.synced(), "flickered off at " + t);
        }
        assertEquals(1, a.syncs());
    }

    @Test
    void quickTapsSendAtMostOneUpdateASecond() {
        PumpActivity a = new PumpActivity();
        long lastSent = Long.MIN_VALUE / 2;
        for (long t = 0; t < 400; t++) {
            if (t % 13 == 0) a.worked(t); // taps with gaps longer than the hold
            if (a.tick(t)) {
                assertTrue(t - lastSent >= PumpActivity.MIN_SYNC_TICKS, "two updates within a second at " + t);
                lastSent = t;
            }
        }
        assertTrue(a.syncs() <= 400 / PumpActivity.MIN_SYNC_TICKS + 1, "syncs " + a.syncs());
    }

    @Test
    void aStopTooSoonAfterTheStartIsSentAfterASecond() {
        PumpActivity a = new PumpActivity();
        a.worked(0);
        assertTrue(a.tick(0));
        for (long t = 1; t < PumpActivity.MIN_SYNC_TICKS; t++) {
            assertFalse(a.tick(t), "sent at " + t);
        }
        assertTrue(a.tick(PumpActivity.MIN_SYNC_TICKS), "the stop goes out once a second has passed");
        assertFalse(a.synced());
    }
}
