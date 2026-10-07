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
 * Server to client while the chart is open: the player's markers, the other players shown on the chart (empty unless
 * {@code chart.show_other_players}) and, after a marker request, the refusal's translation key (empty = accepted or
 * no request).
 */
public record ChartStatePayload(List<ChartMarker> markers, List<OtherPlayer> others, Optional<String> refusal) implements CustomPacketPayload {

    /** Upper bound of list sizes on the wire (the config caps markers at 512). */
    public static final int MAX_MARKERS = 512;
    public static final int MAX_OTHERS = 256;

    /** Another player's position and heading on the chart. */
    public record OtherPlayer(String name, int x, int z, float yaw) {
        public static final StreamCodec<ByteBuf, OtherPlayer> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), OtherPlayer::name,
                ByteBufCodecs.VAR_INT, OtherPlayer::x,
                ByteBufCodecs.VAR_INT, OtherPlayer::z,
                ByteBufCodecs.FLOAT, OtherPlayer::yaw,
                OtherPlayer::new);
    }

    public static final Type<ChartStatePayload> TYPE = new Type<>(Constants.id("chart_state"));
    public static final StreamCodec<ByteBuf, ChartStatePayload> CODEC = StreamCodec.composite(
            ChartMarker.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_MARKERS)), ChartStatePayload::markers,
            OtherPlayer.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OTHERS)), ChartStatePayload::others,
            ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(128)), ChartStatePayload::refusal,
            ChartStatePayload::new);

    public ChartStatePayload {
        markers = List.copyOf(markers);
        others = List.copyOf(others);
    }

    @Override
    public Type<ChartStatePayload> type() {
        return TYPE;
    }
}
