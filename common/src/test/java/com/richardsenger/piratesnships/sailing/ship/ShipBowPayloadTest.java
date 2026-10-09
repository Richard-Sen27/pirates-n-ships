package com.richardsenger.piratesnships.sailing.ship;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** VIS1c: the ship bow payload round-trips every bow, and a client copy takes and forgets it. */
class ShipBowPayloadTest {

    private static ShipBowPayload roundTrip(ShipBowPayload p) {
        ByteBuf buf = Unpooled.buffer();
        ShipBowPayload.CODEC.encode(buf, p);
        ShipBowPayload back = ShipBowPayload.CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(), "everything read");
        return back;
    }

    @Test
    void everyBowRoundTrips() {
        UUID ship = UUID.randomUUID();
        for (String name : new String[] {"north", "east", "south", "west"}) {
            BowFrame bow = BowFrame.byName(name);
            ShipBowPayload back = roundTrip(ShipBowPayload.of(ship, bow));
            assertEquals(ship, back.ship());
            assertEquals(bow, back.bowFrame(), name);
        }
    }

    @Test
    void clientCopyTakesReplacesAndForgets() {
        ClientShipBows client = new ClientShipBows();
        UUID ship = UUID.randomUUID();
        assertNull(client.bow(ship));
        assertNull(client.bow(null));
        client.accept(roundTrip(ShipBowPayload.of(ship, BowFrame.byName("east"))));
        assertEquals(new BowFrame(1, 0), client.bow(ship));
        client.accept(roundTrip(ShipBowPayload.of(ship, BowFrame.byName("north"))));
        assertEquals(new BowFrame(0, -1), client.bow(ship));
        client.accept(new ShipBowPayload(ship, 7));
        assertNull(client.bow(ship), "an unknown bow forgets it");
        client.accept(ShipBowPayload.of(ship, BowFrame.SOUTH));
        client.clear();
        assertNull(client.bow(ship));
    }
}
