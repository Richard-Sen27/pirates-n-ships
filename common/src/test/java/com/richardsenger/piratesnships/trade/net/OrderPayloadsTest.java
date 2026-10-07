package com.richardsenger.piratesnships.trade.net;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** SW1: the shipwright order payloads survive an encode and decode. */
class OrderPayloadsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        codec.encode(buf, value);
        T back = codec.decode(buf);
        assertEquals(0, buf.readableBytes(), "all bytes read");
        return back;
    }

    @Test
    void placeOrderRoundTrips() {
        var p = new OrderPayloads.PlaceOrder(Constants.id("village_1_-2"), Constants.id("starter_sloop"));
        assertEquals(p, roundTrip(OrderPayloads.PlaceOrder.CODEC, p));
    }

    @Test
    void ordersRoundTrip() {
        var view = new OrderPayloads.OrdersView(Constants.id("village_1_-2"), true, 1, 3,
                List.of(new OrderPayloads.OrderLine(Constants.id("starter_sloop"), "ship_template.pirates_n_ships.starter_sloop", 400, 1.36, 34, 16)),
                List.of(new OrderPayloads.MyOrder("ship_template.pirates_n_ships.starter_sloop", 4.25)));
        var result = new OrderPayloads.OrderResult(false, "message.pirates_n_ships.ship_order.no_logs", List.of("34"));
        var full = new OrderPayloads.Orders(Optional.of(view), Optional.of(result));
        assertEquals(full, roundTrip(OrderPayloads.Orders.CODEC, full));
        var empty = new OrderPayloads.Orders(Optional.empty(), Optional.empty());
        assertEquals(empty, roundTrip(OrderPayloads.Orders.CODEC, empty));
    }
}
