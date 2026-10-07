package com.richardsenger.piratesnships.trade.net;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * The shipwright orders protocol of the harbor master's desk (design.md §4.1, SW1). Server → client: {@link Orders}
 * (the Orders tab's content and the last order's result), sent when a desk of a seafarer village opens and after every
 * {@link PlaceOrder}. Client → server: {@link PlaceOrder}; the server checks the desk session (reach, binding), the
 * port's kind, {@code ships.shipwright_orders}, the order limit, coins and materials.
 */
public final class OrderPayloads {

    private OrderPayloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(Constants.id(path));
    }

    /** A ship the shipwright builds: its template, name key, price in doubloons, build days and material list. */
    public record OrderLine(ResourceLocation template, String name, long price, double buildDays, int logs, int wool) {
        public static final Codec<OrderLine> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("template").forGetter(OrderLine::template),
                Codec.STRING.fieldOf("name").forGetter(OrderLine::name),
                Codec.LONG.fieldOf("price").forGetter(OrderLine::price),
                Codec.DOUBLE.fieldOf("build_days").forGetter(OrderLine::buildDays),
                Codec.INT.fieldOf("logs").forGetter(OrderLine::logs),
                Codec.INT.fieldOf("wool").forGetter(OrderLine::wool)
        ).apply(i, OrderLine::new));
    }

    /** One of the viewer's orders at this port: the ship's name key and its finish day (world days). */
    public record MyOrder(String name, double finishDay) {
        public static final Codec<MyOrder> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(MyOrder::name),
                Codec.DOUBLE.fieldOf("finish_day").forGetter(MyOrder::finishDay)
        ).apply(i, MyOrder::new));
    }

    /**
     * The Orders tab of {@code port}: {@code enabled} = the server takes orders ({@code ships.shipwright_orders});
     * {@code open} of {@code max} order slots of the port are taken; the ships on offer; the viewer's orders here.
     */
    public record OrdersView(ResourceLocation port, boolean enabled, int open, int max, List<OrderLine> lines, List<MyOrder> mine) {
        public static final Codec<OrdersView> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("port").forGetter(OrdersView::port),
                Codec.BOOL.fieldOf("enabled").forGetter(OrdersView::enabled),
                Codec.INT.fieldOf("open").forGetter(OrdersView::open),
                Codec.INT.fieldOf("max").forGetter(OrdersView::max),
                OrderLine.CODEC.listOf().fieldOf("lines").forGetter(OrdersView::lines),
                MyOrder.CODEC.listOf().fieldOf("mine").forGetter(OrdersView::mine)
        ).apply(i, OrdersView::new));

        public OrdersView {
            lines = List.copyOf(lines);
            mine = List.copyOf(mine);
        }
    }

    /** The answer to a {@link PlaceOrder}: done or not, a translation key and its (plain text) arguments. */
    public record OrderResult(boolean done, String key, List<String> args) {
        public static final Codec<OrderResult> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("done").forGetter(OrderResult::done),
                Codec.STRING.fieldOf("key").forGetter(OrderResult::key),
                Codec.STRING.listOf().fieldOf("args").forGetter(OrderResult::args)
        ).apply(i, OrderResult::new));

        public OrderResult {
            args = List.copyOf(args);
        }
    }

    /** Server → client: the Orders tab (empty = none at this desk) and the result of the last order, if any. */
    public record Orders(Optional<OrdersView> view, Optional<OrderResult> result) implements CustomPacketPayload {
        public static final Type<Orders> TYPE = payloadType("ship_orders");
        static final Codec<Orders> C = RecordCodecBuilder.create(i -> i.group(
                OrdersView.CODEC.optionalFieldOf("view").forGetter(Orders::view),
                OrderResult.CODEC.optionalFieldOf("result").forGetter(Orders::result)
        ).apply(i, Orders::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Orders> CODEC = ByteBufCodecs.fromCodec(C).cast();

        @Override
        public Type<Orders> type() {
            return TYPE;
        }
    }

    /** Client → server: order a ship of {@code template} from {@code port}'s shipwright. */
    public record PlaceOrder(ResourceLocation port, ResourceLocation template) implements CustomPacketPayload {
        public static final Type<PlaceOrder> TYPE = payloadType("ship_order_place");
        static final Codec<PlaceOrder> C = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("port").forGetter(PlaceOrder::port),
                ResourceLocation.CODEC.fieldOf("template").forGetter(PlaceOrder::template)
        ).apply(i, PlaceOrder::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, PlaceOrder> CODEC = ByteBufCodecs.fromCodec(C).cast();

        @Override
        public Type<PlaceOrder> type() {
            return TYPE;
        }
    }
}
