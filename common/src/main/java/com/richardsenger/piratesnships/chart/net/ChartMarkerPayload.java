package com.richardsenger.piratesnships.chart.net;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.chart.data.MarkerIcon;
import com.richardsenger.piratesnships.chart.data.MarkerRules;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: add, edit or remove one of the player's own chart markers. The server checks everything
 * ({@link MarkerRules}, {@code chart.max_markers}) and answers with a {@link ChartStatePayload}. {@code id} is ignored
 * for {@link Action#ADD}; position, icon and name are ignored for {@link Action#REMOVE}.
 */
public record ChartMarkerPayload(Action action, int id, int x, int z, MarkerIcon icon, String name) implements CustomPacketPayload {

    public enum Action {
        ADD, EDIT, REMOVE;

        private static final Action[] VALUES = values();
        static final StreamCodec<ByteBuf, Action> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(i -> VALUES[Math.floorMod(i, VALUES.length)], Enum::ordinal);
    }

    public static final Type<ChartMarkerPayload> TYPE = new Type<>(Constants.id("chart_marker"));
    public static final StreamCodec<ByteBuf, ChartMarkerPayload> CODEC = StreamCodec.composite(
            Action.STREAM_CODEC, ChartMarkerPayload::action,
            ByteBufCodecs.VAR_INT, ChartMarkerPayload::id,
            ByteBufCodecs.VAR_INT, ChartMarkerPayload::x,
            ByteBufCodecs.VAR_INT, ChartMarkerPayload::z,
            MarkerIcon.STREAM_CODEC, ChartMarkerPayload::icon,
            // a little longer than the rule's limit, so an over-long name reaches the rules and is refused politely
            ByteBufCodecs.stringUtf8(MarkerRules.MAX_NAME_LENGTH * 2), ChartMarkerPayload::name,
            ChartMarkerPayload::new);

    public static ChartMarkerPayload add(int x, int z, MarkerIcon icon, String name) {
        return new ChartMarkerPayload(Action.ADD, 0, x, z, icon, name);
    }

    public static ChartMarkerPayload edit(int id, int x, int z, MarkerIcon icon, String name) {
        return new ChartMarkerPayload(Action.EDIT, id, x, z, icon, name);
    }

    public static ChartMarkerPayload remove(int id) {
        return new ChartMarkerPayload(Action.REMOVE, id, 0, 0, MarkerIcon.X, "");
    }

    @Override
    public Type<ChartMarkerPayload> type() {
        return TYPE;
    }
}
