package com.richardsenger.piratesnships.chart.net;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.chart.data.ChartMarker;
import com.richardsenger.piratesnships.chart.data.MarkerRules;
import com.richardsenger.piratesnships.chart.data.StructureReveal;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.Optional;

/**
 * Server to client while the chart is open (MAP1, reshaped by CHART2): the player's markers when they changed (empty
 * = keep the list the client has; on opening and after every marker request), the ships drawn on the viewer's chart
 * (the ship they stand on and their own loaded ships, never another player's; empty with {@code chart.show_ships}
 * off), the structures on the chart when they changed (empty = unchanged) and, after a marker request, the refusal's
 * translation key (empty = accepted or no request). Other players are never sent (the human, 2026-10-12).
 */
public record ChartStatePayload(Optional<List<ChartMarker>> markers, List<ChartShip> ships, Optional<List<ChartStructure>> structures,
                                Optional<String> refusal) implements CustomPacketPayload {

    /** Upper bound of the marker list on the wire: the markers one chart may hold at most. */
    public static final int MAX_MARKERS = MarkerRules.HARD_LIMIT;
    public static final int MAX_SHIPS = 64;
    public static final int MAX_STRUCTURES = StructureReveal.MAX_SHOWN;

    public static final Type<ChartStatePayload> TYPE = new Type<>(Constants.id("chart_state"));
    public static final StreamCodec<ByteBuf, ChartStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ChartMarker.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_MARKERS))), ChartStatePayload::markers,
            ChartShip.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SHIPS)), ChartStatePayload::ships,
            ByteBufCodecs.optional(ChartStructure.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_STRUCTURES))), ChartStatePayload::structures,
            ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(128)), ChartStatePayload::refusal,
            ChartStatePayload::new);

    public ChartStatePayload {
        markers = markers.map(List::copyOf);
        ships = List.copyOf(ships);
        structures = structures.map(List::copyOf);
    }

    @Override
    public Type<ChartStatePayload> type() {
        return TYPE;
    }
}
