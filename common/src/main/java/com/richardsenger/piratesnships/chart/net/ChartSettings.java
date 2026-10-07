package com.richardsenger.piratesnships.chart.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The server's chart settings a client needs (work package MAP1): sent on login and datapack reload
 * ({@link ChartSettingsPayload}) and with every {@link ChartOpenPayload}. The key binding reads
 * {@link #openWithoutItem}; the screen reads the rest.
 */
public record ChartSettings(boolean enabled, boolean openWithoutItem, boolean showOtherPlayers, int cellBlocks, int maxMarkers) {

    /** What a client assumes before the server said anything: the config defaults. */
    public static final ChartSettings DEFAULT = new ChartSettings(true, false, false, 4, 64);

    public static final StreamCodec<ByteBuf, ChartSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ChartSettings::enabled,
            ByteBufCodecs.BOOL, ChartSettings::openWithoutItem,
            ByteBufCodecs.BOOL, ChartSettings::showOtherPlayers,
            ByteBufCodecs.VAR_INT, ChartSettings::cellBlocks,
            ByteBufCodecs.VAR_INT, ChartSettings::maxMarkers,
            ChartSettings::new);
}
