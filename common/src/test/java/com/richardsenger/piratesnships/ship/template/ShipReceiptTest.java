package com.richardsenger.piratesnships.ship.template;

import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SW1: the receipt's data survives its codecs, and its tooltip shows the progress. */
class ShipReceiptTest {

    private static final ShipReceipt RECEIPT = new ShipReceipt(ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "village_120_-40"),
            UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0"), ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "starter_sloop"),
            "ship_template.pirates_n_ships.starter_sloop", 3.61);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void roundTrips() {
        Tag nbt = ShipReceipt.CODEC.encodeStart(NbtOps.INSTANCE, RECEIPT).getOrThrow();
        assertEquals(RECEIPT, ShipReceipt.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow());
        var json = ShipReceipt.CODEC.encodeStart(JsonOps.INSTANCE, RECEIPT).getOrThrow();
        assertEquals(RECEIPT, ShipReceipt.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        ByteBuf buf = Unpooled.buffer();
        ShipReceipt.STREAM_CODEC.encode(buf, RECEIPT);
        assertEquals(RECEIPT, ShipReceipt.STREAM_CODEC.decode(buf));
    }

    @Test
    void orderRecordRoundTripsAndMatchesItsShortId() {
        ShipOrder order = new ShipOrder(RECEIPT.order(), RECEIPT.template(), UUID.randomUUID(), 2.25, 3.61);
        Tag nbt = ShipOrder.CODEC.encodeStart(NbtOps.INSTANCE, order).getOrThrow();
        assertEquals(order, ShipOrder.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow());
        assertEquals("0f1e2d3c", order.shortId());
        assertTrue(order.matches("0f1e2d3c"));
        assertTrue(order.matches("0F1E"));
        assertTrue(order.matches(order.id().toString()));
        assertFalse(order.matches("0f1"));
        assertFalse(order.matches("1f1e2d3c"));
        assertEquals(3.0, order.withFinishDay(3.0).finishDay());
        assertEquals(ShipReceipt.of(RECEIPT.port(), order, RECEIPT.name()), RECEIPT);
    }

    @Test
    void tooltipStates() {
        ShipReceiptText.State waiting = ShipReceiptText.state(3.61, 2.11);
        assertFalse(waiting.ready());
        assertEquals("1.5", waiting.daysLeft());
        assertEquals(ShipReceiptText.KEY_READY_IN, waiting.key());
        ShipReceiptText.State ready = ShipReceiptText.state(3.61, 3.61);
        assertTrue(ready.ready());
        assertEquals(ShipReceiptText.KEY_READY, ready.key());
        assertTrue(ShipReceiptText.state(3.61, 9.0).ready());
    }

    @Test
    void tooltipLinesNameTheShipTheVillageAndTheProgress() {
        List<Component> lines = ShipReceiptText.lines(RECEIPT, 2.11);
        assertEquals(3, lines.size());
        TranslatableContents ship = (TranslatableContents) lines.get(0).getContents();
        assertEquals(ShipReceiptText.KEY_SHIP_AT, ship.getKey());
        assertEquals("ship_template.pirates_n_ships.starter_sloop", ((TranslatableContents) ((Component) ship.getArgs()[0]).getContents()).getKey());
        assertEquals("Village 120 40", ship.getArgs()[1]);
        TranslatableContents progress = (TranslatableContents) lines.get(1).getContents();
        assertEquals(ShipReceiptText.KEY_READY_IN, progress.getKey());
        assertArrayEquals(new Object[]{"1.5"}, progress.getArgs());
        TranslatableContents done = (TranslatableContents) ShipReceiptText.lines(RECEIPT, 4.0).get(1).getContents();
        assertEquals(ShipReceiptText.KEY_READY, done.getKey());
    }
}
