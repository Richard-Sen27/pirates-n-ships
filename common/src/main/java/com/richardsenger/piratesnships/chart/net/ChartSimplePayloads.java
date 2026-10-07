package com.richardsenger.piratesnships.chart.net;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The chart's payloads without content. */
public final class ChartSimplePayloads {

    private ChartSimplePayloads() {
    }

    /** Client to server: the chart key was pressed with no chart in hand (allowed only with {@code chart.open_without_item}). */
    public record RequestOpen() implements CustomPacketPayload {
        public static final RequestOpen INSTANCE = new RequestOpen();
        public static final Type<RequestOpen> TYPE = new Type<>(Constants.id("chart_request_open"));
        public static final StreamCodec<ByteBuf, RequestOpen> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<RequestOpen> type() {
            return TYPE;
        }
    }

    /** Client to server: the chart screen closed; stop streaming regions and player positions. */
    public record Close() implements CustomPacketPayload {
        public static final Close INSTANCE = new Close();
        public static final Type<Close> TYPE = new Type<>(Constants.id("chart_close"));
        public static final StreamCodec<ByteBuf, Close> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<Close> type() {
            return TYPE;
        }
    }
}
