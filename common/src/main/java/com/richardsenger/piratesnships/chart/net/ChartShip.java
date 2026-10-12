package com.richardsenger.piratesnships.chart.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A ship drawn on the viewer's chart (work package CHART2): only the ship the viewer stands on ({@code aboard}) and
 * the viewer's own loaded ships, never another player's. {@code heading} is the bow's compass bearing in degrees
 * (0 north, 90 east); {@code (x, z)} the middle of the ship's blocks.
 */
public record ChartShip(String name, int x, int z, float heading, boolean aboard) {

    public static final StreamCodec<ByteBuf, ChartShip> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), ChartShip::name,
            ByteBufCodecs.VAR_INT, ChartShip::x,
            ByteBufCodecs.VAR_INT, ChartShip::z,
            ByteBufCodecs.FLOAT, ChartShip::heading,
            ByteBufCodecs.BOOL, ChartShip::aboard,
            ChartShip::new);

    public ChartShip {
        name = name == null ? "" : name.length() > 64 ? name.substring(0, 64) : name;
    }
}
