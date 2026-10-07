package com.richardsenger.piratesnships.chart.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A marker a player put on their own chart: an id unique within that chart, a block position (x, z), an icon and a
 * name (may be empty, at most {@link MarkerRules#MAX_NAME_LENGTH} characters).
 */
public record ChartMarker(int id, int x, int z, MarkerIcon icon, String name) {

    public static final Codec<ChartMarker> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(ChartMarker::id),
            Codec.INT.fieldOf("x").forGetter(ChartMarker::x),
            Codec.INT.fieldOf("z").forGetter(ChartMarker::z),
            MarkerIcon.CODEC.fieldOf("icon").forGetter(ChartMarker::icon),
            Codec.STRING.optionalFieldOf("name", "").forGetter(ChartMarker::name)
    ).apply(i, ChartMarker::new));

    public static final StreamCodec<ByteBuf, ChartMarker> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChartMarker::id,
            ByteBufCodecs.VAR_INT, ChartMarker::x,
            ByteBufCodecs.VAR_INT, ChartMarker::z,
            MarkerIcon.STREAM_CODEC, ChartMarker::icon,
            ByteBufCodecs.stringUtf8(MarkerRules.MAX_NAME_LENGTH * 4), ChartMarker::name,
            ChartMarker::new);

    public ChartMarker {
        name = name == null ? "" : name;
    }
}
