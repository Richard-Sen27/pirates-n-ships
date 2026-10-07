package com.richardsenger.piratesnships.chart.net;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.chart.data.ChartMarker;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.Optional;

/**
 * Server to client: open the chart screen with the current settings and the player's markers. The client then sends
 * its viewport ({@link ChartViewPayload}) and the server streams the regions in it ({@link ChartRegionPayload}).
 * With a {@code tile} (work package MAP2) the screen opens in "draw on tile" mode for that map tile.
 */
public record ChartOpenPayload(ChartSettings settings, List<ChartMarker> markers, Optional<TileTarget> tile) implements CustomPacketPayload {

    public static final Type<ChartOpenPayload> TYPE = new Type<>(Constants.id("chart_open"));
    public static final StreamCodec<ByteBuf, ChartOpenPayload> CODEC = StreamCodec.composite(
            ChartSettings.STREAM_CODEC, ChartOpenPayload::settings,
            ChartMarker.STREAM_CODEC.apply(ByteBufCodecs.list(ChartStatePayload.MAX_MARKERS)), ChartOpenPayload::markers,
            ByteBufCodecs.optional(TileTarget.STREAM_CODEC), ChartOpenPayload::tile,
            ChartOpenPayload::new);

    public ChartOpenPayload {
        markers = List.copyOf(markers);
    }

    public ChartOpenPayload(ChartSettings settings, List<ChartMarker> markers) {
        this(settings, markers, Optional.empty());
    }

    @Override
    public Type<ChartOpenPayload> type() {
        return TYPE;
    }
}
