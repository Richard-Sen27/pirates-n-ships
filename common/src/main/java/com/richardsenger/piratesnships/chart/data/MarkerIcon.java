package com.richardsenger.piratesnships.chart.data;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** The icon of a chart marker. The ordinal is the network id: never reorder, add at the end. */
public enum MarkerIcon implements StringRepresentable {
    /** X marks the spot. */
    X,
    ANCHOR,
    SKULL,
    PORT,
    DANGER;

    private static final MarkerIcon[] VALUES = values();

    public static final Codec<MarkerIcon> CODEC = StringRepresentable.fromEnum(MarkerIcon::values);
    public static final StreamCodec<ByteBuf, MarkerIcon> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(MarkerIcon::of, Enum::ordinal);

    public static MarkerIcon of(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : X;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
