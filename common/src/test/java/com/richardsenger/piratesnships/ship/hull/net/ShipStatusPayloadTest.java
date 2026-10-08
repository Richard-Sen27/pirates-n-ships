package com.richardsenger.piratesnships.ship.hull.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The ship status payload (HUD1): codec round trip, rounding and the send rule (HUD1, HUD2). */
class ShipStatusPayloadTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ShipStatusPayload roundTrip(ShipStatusPayload p) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        ShipStatusPayload.CODEC.encode(buf, p);
        ShipStatusPayload back = ShipStatusPayload.CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(), "all bytes read");
        return back;
    }

    @Test
    void roundTrips() {
        List<ShipStatusPayload.Cell> cells = new ArrayList<>();
        for (int i = 0; i < 16; i++) cells.add(new ShipStatusPayload.Cell(i, 10 + i, i * 0.5f, i % 3, i % 2 == 0));
        ShipStatusPayload p = new ShipStatusPayload(UUID.randomUUID(), "Black Pearl", 271.5f, 4.25f, -12f, 2, cells);
        assertEquals(p, roundTrip(p));
        ShipStatusPayload slow = p.withFreshTicks(620);
        assertEquals(620, roundTrip(slow).freshTicks(), "the freshness travels");
        assertEquals(620, slow.quantize().freshTicks(), "and survives rounding");
        ShipStatusPayload noHelm = new ShipStatusPayload(UUID.randomUUID(), "", 0f, 0f, Float.NaN, -1, List.of());
        ShipStatusPayload back = roundTrip(noHelm);
        assertEquals(noHelm, back);
        assertFalse(back.hasRudder());
        assertEquals(null, back.loadLevel());
        assertEquals(com.richardsenger.piratesnships.trade.cargo.CargoWeight.LoadLevel.HEAVILY_LADEN, roundTrip(p).loadLevel());
    }

    @Test
    void longNamesAndTooManyCellsAreCut() {
        List<ShipStatusPayload.Cell> cells = new ArrayList<>();
        for (int i = 0; i < 20; i++) cells.add(new ShipStatusPayload.Cell(i, 1, 0, 0, false));
        ShipStatusPayload p = new ShipStatusPayload(UUID.randomUUID(), "x".repeat(100), 0, 0, 0, -1, cells);
        assertEquals(ShipStatusPayload.MAX_NAME, p.name().length());
        assertEquals(CompartmentStrip.MAX_CELLS, p.cells().size());
        assertEquals(p, roundTrip(p));
    }

    @Test
    void quantizeRoundsAndClamps() {
        UUID id = UUID.randomUUID();
        ShipStatusPayload p = new ShipStatusPayload(id, "A", 359.7f, 3.0123f, -11.6f, 1,
                List.of(new ShipStatusPayload.Cell(0, 10, 12.3f, 0, false), new ShipStatusPayload.Cell(1, 10, -0.01f, 0, false)));
        ShipStatusPayload q = p.quantize();
        assertEquals(0f, q.heading(), "359.7° rounds round to north");
        assertEquals(3.0f, q.speed(), 1e-6);
        assertEquals(-12f, q.rudder());
        assertEquals(10f, q.cells().get(0).water(), "water is capped at the volume");
        assertEquals(0f, q.cells().get(1).water());
        assertEquals(1f, q.cells().get(0).fraction());
        // tiny jitter rounds away, a real change does not
        ShipStatusPayload jitter = new ShipStatusPayload(id, "A", 359.9f, 3.0101f, -11.7f, 1, p.cells()).quantize();
        assertEquals(q, jitter);
        ShipStatusPayload moved = new ShipStatusPayload(id, "A", 2f, 3.0123f, -11.6f, 1, p.cells()).quantize();
        assertNotEquals(q, moved);
        assertTrue(Float.isNaN(new ShipStatusPayload(id, "", 0, 0, Float.NaN, -1, List.of()).quantize().rudder()));
    }

    @Test
    void throttleSendsChangesAndAKeepalive() {
        ShipStatusThrottle t = new ShipStatusThrottle();
        UUID player = UUID.randomUUID(), other = UUID.randomUUID();
        ShipStatusPayload a = new ShipStatusPayload(UUID.randomUUID(), "A", 10, 1, 0, -1, List.of());
        ShipStatusPayload b = new ShipStatusPayload(a.ship(), "A", 11, 1, 0, -1, List.of());
        assertTrue(t.offer(player, a, 5), "the first status goes out");
        for (int i = 1; i < 5; i++) {
            assertFalse(t.offer(player, a, 5), "unchanged at interval " + i);
        }
        assertTrue(t.offer(player, a, 5), "every fifth interval when asked for five");
        assertFalse(t.offer(player, a, 5));
        assertTrue(t.offer(player, b, 5), "a change goes out at once");
        assertFalse(t.offer(player, b, 5));
        assertTrue(t.offer(other, b, 5), "players are counted apart");
        t.forget(player);
        assertTrue(t.offer(player, b, 5), "back aboard: at once");
        assertTrue(t.offer(player, b, 1), "a keepalive of one interval sends every time");
        assertTrue(t.offer(player, b, 1));
        t.retain(List.of(other));
        assertEquals(1, t.size());
    }
}
