package com.richardsenger.piratesnships.ship.hull.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.richardsenger.piratesnships.ship.hull.net.ShipStatusPayload;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** When the client shows a ship status (fresh and aboard) and how the needle turns (HUD1). */
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
        ClientShipStatus.accept(status(UUID.randomUUID(), 90));
        assertNotNull(ClientShipStatus.shown());
        for (int i = 1; i < ClientShipStatus.FRESH_TICKS; i++) ClientShipStatus.tick(true);
        assertNotNull(ClientShipStatus.shown(), "still fresh just before 3 s");
        ClientShipStatus.tick(true);
        assertNull(ClientShipStatus.shown(), "stale after 3 s");
    }

    @Test
    void aboardGraceCoversAJump() {
        ClientShipStatus.tick(true);
        ClientShipStatus.accept(status(UUID.randomUUID(), 0));
        for (int i = 0; i < ClientShipStatus.ABOARD_GRACE_TICKS; i++) ClientShipStatus.tick(false);
        assertNotNull(ClientShipStatus.shown(), "a short jump keeps the HUD");
        ClientShipStatus.tick(false);
        assertNull(ClientShipStatus.shown(), "ashore it hides");
        ClientShipStatus.tick(true);
        assertNotNull(ClientShipStatus.shown(), "back aboard while the status is fresh");
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
