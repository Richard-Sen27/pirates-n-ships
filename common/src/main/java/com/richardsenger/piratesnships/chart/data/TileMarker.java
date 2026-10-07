package com.richardsenger.piratesnships.chart.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A chart marker stamped onto a map tile's drawing (work package MAP2): its icon, its position in tile pixels (one
 * pixel per chart cell, {@code 0..size-1}) and its name (may be empty).
 */
public record TileMarker(MarkerIcon icon, int px, int py, String name) {

    public static final Codec<TileMarker> CODEC = RecordCodecBuilder.create(i -> i.group(
            MarkerIcon.CODEC.fieldOf("icon").forGetter(TileMarker::icon),
            Codec.INT.fieldOf("px").forGetter(TileMarker::px),
            Codec.INT.fieldOf("py").forGetter(TileMarker::py),
            Codec.STRING.optionalFieldOf("name", "").forGetter(TileMarker::name)
    ).apply(i, TileMarker::new));

    public static final StreamCodec<ByteBuf, TileMarker> STREAM_CODEC = StreamCodec.composite(
            MarkerIcon.STREAM_CODEC, TileMarker::icon,
            ByteBufCodecs.VAR_INT, TileMarker::px,
            ByteBufCodecs.VAR_INT, TileMarker::py,
            ByteBufCodecs.stringUtf8(MarkerRules.MAX_NAME_LENGTH * 4), TileMarker::name,
            TileMarker::new);

    public TileMarker {
        name = name == null ? "" : name;
    }
}
