package com.richardsenger.piratesnships.chart.net;

import com.richardsenger.piratesnships.chart.render.ChartStyle;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The server's chart settings a client needs (work package MAP1): sent on login and datapack reload
 * ({@link ChartSettingsPayload}) and with every {@link ChartOpenPayload}. The key binding reads
 * {@link #openWithoutItem}; the screen reads the rest. CHART2: {@code maxMarkers} 0 means no limit,
 * {@code maxZoomOut} is {@code chart.max_zoom_out}; the old {@code showOtherPlayers} is gone (the chart never shows
 * other players). CHART4: {@code style} is {@code chart.style}; a client that is told another style redraws its chart.
 */
public record ChartSettings(boolean enabled, boolean openWithoutItem, int cellBlocks, int maxMarkers, int maxZoomOut, ChartStyle style) {

    /** What a client assumes before the server said anything: the config defaults. */
    public static final ChartSettings DEFAULT = new ChartSettings(true, false, 4, 0, 3, ChartStyle.INK);

    private static final StreamCodec<ByteBuf, ChartStyle> STYLE_CODEC = ByteBufCodecs.VAR_INT.map(ChartStyle::of, ChartStyle::ordinal);

    public static final StreamCodec<ByteBuf, ChartSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ChartSettings::enabled,
            ByteBufCodecs.BOOL, ChartSettings::openWithoutItem,
            ByteBufCodecs.VAR_INT, ChartSettings::cellBlocks,
            ByteBufCodecs.VAR_INT, ChartSettings::maxMarkers,
            ByteBufCodecs.VAR_INT, ChartSettings::maxZoomOut,
            STYLE_CODEC, ChartSettings::style,
            ChartSettings::new);
}
