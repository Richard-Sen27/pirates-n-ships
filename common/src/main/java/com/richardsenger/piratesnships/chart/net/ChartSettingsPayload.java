package com.richardsenger.piratesnships.chart.net;

import com.richardsenger.piratesnships.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the chart settings (login, datapack reload). */
public record ChartSettingsPayload(ChartSettings settings) implements CustomPacketPayload {

    public static final Type<ChartSettingsPayload> TYPE = new Type<>(Constants.id("chart_settings"));
    public static final StreamCodec<ByteBuf, ChartSettingsPayload> CODEC =
            ChartSettings.STREAM_CODEC.map(ChartSettingsPayload::new, ChartSettingsPayload::settings);

    @Override
    public Type<ChartSettingsPayload> type() {
        return TYPE;
    }
}
