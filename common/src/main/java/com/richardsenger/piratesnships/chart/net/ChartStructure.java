package com.richardsenger.piratesnships.chart.net;

import com.richardsenger.piratesnships.chart.data.StructureKind;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A structure drawn on the chart (work package CHART2): its kind (the icon), its centre in blocks and its label: a
 * port's name (its id while ports have no name), empty for a wreck, the structure's id for another structure.
 */
public record ChartStructure(StructureKind kind, int x, int z, String label) {

    public static final int MAX_LABEL = 64;

    public static final StreamCodec<ByteBuf, ChartStructure> STREAM_CODEC = StreamCodec.composite(
            StructureKind.STREAM_CODEC, ChartStructure::kind,
            ByteBufCodecs.VAR_INT, ChartStructure::x,
            ByteBufCodecs.VAR_INT, ChartStructure::z,
            ByteBufCodecs.stringUtf8(MAX_LABEL * 4), ChartStructure::label,
            ChartStructure::new);

    public ChartStructure {
        label = label == null ? "" : label.length() > MAX_LABEL ? label.substring(0, MAX_LABEL) : label;
    }
}
