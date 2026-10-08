package com.richardsenger.piratesnships.ship.hull.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.ship.hull.net.ShipStatusPayload;
import com.richardsenger.piratesnships.ship.hull.net.ShipStatusThrottle;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** When the client shows a ship status (fresh and aboard) and how the needle turns (HUD1, HUD2). */
class ClientShipStatusTest {

    private static ShipStatusPayload status(UUID ship, float heading) {
        return new ShipStatusPayload(ship, "A", heading, 0, 0, -1, List.of());
    }

    @AfterEach
    void reset() {
        ClientShipStatus.reset();
    }

    @Test
    void shownWhileFreshAndAboard() {
        assertNull(ClientShipStatus.shown(), "nothing received");
        ClientShipStatus.tick(true);
        ShipStatusPayload s = status(UUID.randomUUID(), 90).withFreshTicks(100);
        ClientShipStatus.accept(s);
        assertNotNull(ClientShipStatus.shown());
        for (int i = 1; i < 100; i++) ClientShipStatus.tick(true);
        assertNotNull(ClientShipStatus.shown(), "still fresh just before the payload's 100 ticks");
        ClientShipStatus.tick(true);
        assertNull(ClientShipStatus.shown(), "stale after 100 ticks");
    }

    @Test
    void freshnessHasAFloor() {
        ClientShipStatus.tick(true);
        ClientShipStatus.accept(status(UUID.randomUUID(), 0).withFreshTicks(0));
        for (int i = 1; i < ShipStatusPayload.MIN_FRESH_TICKS; i++) ClientShipStatus.tick(true);
        assertNotNull(ClientShipStatus.shown(), "a bogus freshness of 0 still shows for the minimum");
    }

    @Test
    void aboardGraceAfterLeaving() {
        ClientShipStatus.tick(true);
        ClientShipStatus.accept(status(UUID.randomUUID(), 0));
        for (int i = 0; i < ClientShipStatus.ABOARD_GRACE_TICKS; i++) ClientShipStatus.tick(false, false);
        assertNotNull(ClientShipStatus.shown(), "a moment off the deck keeps the HUD");
        ClientShipStatus.tick(false, false);
        assertNull(ClientShipStatus.shown(), "ashore or swimming it hides after 1.5 s");
        ClientShipStatus.tick(true);
        assertNotNull(ClientShipStatus.shown(), "back aboard while the status is fresh");
    }

    /** HUD2: a jump lasts about 12 ticks, longer than HUD1's 10-tick grace; the panel blinked on every jump. */
    @Test
    void jumpingAndClimbingKeepTheHud() {
        UUID ship = UUID.randomUUID();
        ClientShipStatus.tick(true);
        ClientShipStatus.accept(status(ship, 0));
        for (int jump = 0; jump < 5; jump++) {
            for (int i = 0; i < 14; i++) {
                ClientShipStatus.tick(false, true);
                assertNotNull(ClientShipStatus.shown(), "hidden mid-jump " + jump + " at tick " + i);
            }
            ClientShipStatus.tick(true, false);
        }
        // a 10 s ladder climb up the mast (Sable does not track the player on a ladder)
        for (int i = 0; i < 200; i++) {
            ClientShipStatus.tick(false, true);
            if (i % 20 == 0) ClientShipStatus.accept(status(ship, 0));
            assertNotNull(ClientShipStatus.shown(), "hidden on the ladder at tick " + i);
        }
    }

    @Test
    void aHoldEndsInTheWaterOrAfterTwentySeconds() {
        UUID ship = UUID.randomUUID();
        ClientShipStatus.tick(true);
        ClientShipStatus.accept(status(ship, 0));
        // fall over the rail: in the air (holding), then in the water (not holding)
        for (int i = 0; i < 20; i++) ClientShipStatus.tick(false, true);
        assertNotNull(ClientShipStatus.shown());
        for (int i = 0; i <= ClientShipStatus.ABOARD_GRACE_TICKS; i++) ClientShipStatus.tick(false, false);
        assertNull(ClientShipStatus.shown(), "swimming away hides the HUD");
        // a hold that never touches the ship again runs out
        ClientShipStatus.tick(true);
        int shownFor = 0;
        for (int i = 0; i < ClientShipStatus.MAX_HOLD_TICKS + 100; i++) {
            ClientShipStatus.tick(false, true);
            if (i % 20 == 0) ClientShipStatus.accept(status(ship, 0));
            if (ClientShipStatus.shown() != null) shownFor++;
        }
        assertEquals(ClientShipStatus.MAX_HOLD_TICKS + ClientShipStatus.ABOARD_GRACE_TICKS, shownFor, 1);
        // holding does not bring the HUD back once it has gone
        assertNull(ClientShipStatus.shown());
    }

    /**
     * HUD2's flicker: a ship at rest sends the same status every interval, so only the throttle's keepalive reaches
     * the client. HUD1 kept it back for five intervals (100 ticks) while the client dropped a status after 60: 3 s
     * shown, 2 s hidden. Every interval from 1 to 200 ticks, the server's throttle and the client's freshness together
     * keep the HUD up for a minute of standing still.
     */
    @Test
    void aShipAtRestNeverFlickers() {
        UUID ship = UUID.randomUUID(), player = UUID.randomUUID();
        for (int interval : new int[] {1, 7, 20, 30, 41, 100, 200}) {
            ClientShipStatus.reset();
            ShipStatusThrottle throttle = new ShipStatusThrottle();
            int keepalive = ShipStatusThrottle.keepaliveIntervals(interval);
            ShipStatusPayload rest = status(ship, 45).withFreshTicks(ShipStatusThrottle.freshTicks(interval));
            ClientShipStatus.tick(true);
            for (int t = 0; t < 1200 + 2 * interval; t++) {
                if (t % interval == 0 && throttle.offer(player, rest, keepalive)) ClientShipStatus.accept(rest);
                ClientShipStatus.tick(true);
                assertNotNull(ClientShipStatus.shown(), "the HUD hid at tick " + t + " with interval " + interval);
            }
        }
    }

    @Test
    void freshnessOutlastsTheKeepalive() {
        for (int interval = 1; interval <= 200; interval++) {
            int period = ShipStatusThrottle.keepalivePeriodTicks(interval);
            assertTrue(period >= interval, "keepalive shorter than an interval at " + interval);
            assertTrue(period <= Math.max(interval, ShipStatusThrottle.KEEPALIVE_TICKS), "keepalive too slow at " + interval);
            assertTrue(ShipStatusThrottle.freshTicks(interval) >= 3 * period + 20,
                    "a status must survive two lost keepalives and a second of lag at " + interval);
        }
        assertEquals(40, ShipStatusThrottle.keepalivePeriodTicks(20), "unchanged statuses every 2 s at the default");
        assertEquals(140, ShipStatusThrottle.freshTicks(20), "fresh for 7 s at the default");
        assertEquals(140, ShipStatusPayload.DEFAULT_FRESH_TICKS);
    }

    @Test
    void needleTurnsTheShortWayRound() {
        UUID ship = UUID.randomUUID();
        ClientShipStatus.accept(status(ship, 350));
        assertEquals(350, ClientShipStatus.heading(0f), 1e-9, "the first status sets the needle at once");
        ClientShipStatus.accept(status(ship, 10));
        ClientShipStatus.tick(true);
        double h = ClientShipStatus.heading(1f);
        assertEquals(355, h, 1e-9, "a quarter of the 20° turn, through north");
        for (int i = 0; i < 60; i++) ClientShipStatus.tick(true);
        assertEquals(10, ClientShipStatus.heading(1f), 0.01);
        assertEquals(-20, ClientShipStatus.shortest(10, 350), 1e-9);
        assertEquals(180, ClientShipStatus.shortest(0, 180), 1e-9);
    }
}
