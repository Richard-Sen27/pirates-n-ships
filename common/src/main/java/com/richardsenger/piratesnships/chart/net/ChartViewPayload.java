package com.richardsenger.piratesnships.chart.net;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server while the chart is open: the viewport moved. The server sends every region within
 * {@code radiusCells} (a square, clamped to {@link #MAX_RADIUS}) of cell {@code (centerCx, centerCz)} that the client
 * has not seen in its current version, nearest first.
 */
public record ChartViewPayload(int centerCx, int centerCz, int radiusCells) implements CustomPacketPayload {

    /**
     * Largest viewport radius in cells the server honours: 128 regions each way (CHART2: the widest zoom-out, 32 cells
     * per pixel, shows up to about 7,000 cells either side of the centre). Streaming still sends only regions the
     * player has charted, nearest first, a few per tick.
     */
    public static final int MAX_RADIUS = 8192;

    public static final Type<ChartViewPayload> TYPE = new Type<>(Constants.id("chart_view"));
    public static final StreamCodec<ByteBuf, ChartViewPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChartViewPayload::centerCx,
            ByteBufCodecs.VAR_INT, ChartViewPayload::centerCz,
            ByteBufCodecs.VAR_INT, ChartViewPayload::radiusCells,
            ChartViewPayload::new);

    public int clampedRadius() {
        return Math.max(0, Math.min(MAX_RADIUS, radiusCells));
    }

    @Override
    public Type<ChartViewPayload> type() {
        return TYPE;
    }
}
