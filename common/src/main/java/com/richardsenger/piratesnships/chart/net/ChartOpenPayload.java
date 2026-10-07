package com.richardsenger.piratesnships.chart.net;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.chart.data.ChartMarker;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Server to client: open the chart screen with the current settings and the player's markers. The client then sends
 * its viewport ({@link ChartViewPayload}) and the server streams the regions in it ({@link ChartRegionPayload}).
 */
public record ChartOpenPayload(ChartSettings settings, List<ChartMarker> markers) implements CustomPacketPayload {

    public static final Type<ChartOpenPayload> TYPE = new Type<>(Constants.id("chart_open"));
    public static final StreamCodec<ByteBuf, ChartOpenPayload> CODEC = StreamCodec.composite(
            ChartSettings.STREAM_CODEC, ChartOpenPayload::settings,
            ChartMarker.STREAM_CODEC.apply(ByteBufCodecs.list(ChartStatePayload.MAX_MARKERS)), ChartOpenPayload::markers,
            ChartOpenPayload::new);

    public ChartOpenPayload {
        markers = List.copyOf(markers);
    }

    @Override
    public Type<ChartOpenPayload> type() {
        return TYPE;
    }
}
