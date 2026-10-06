package com.richardsenger.piratesnships.station.order;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: the captain picked {@code order} (a {@link WhistleOrder#id()}) in the whistle's radial menu. The
 * id travels as a string so the server can tell an unknown order (from a newer or modified client) from a known one
 * and ignore it; {@link WhistleOrders#handle} checks everything else.
 */
public record WhistleOrderPayload(String order) implements CustomPacketPayload {

    /** Longest order id accepted on the wire. */
    public static final int MAX_LENGTH = 64;

    public static final Type<WhistleOrderPayload> TYPE = new Type<>(Constants.id("whistle_order"));
    public static final StreamCodec<ByteBuf, WhistleOrderPayload> CODEC =
            ByteBufCodecs.stringUtf8(MAX_LENGTH).map(WhistleOrderPayload::new, WhistleOrderPayload::order);

    public WhistleOrderPayload(WhistleOrder order) {
        this(order.id());
    }

    @Override
    public Type<WhistleOrderPayload> type() {
        return TYPE;
    }
}
