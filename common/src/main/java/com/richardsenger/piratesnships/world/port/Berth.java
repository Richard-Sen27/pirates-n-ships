package com.richardsenger.piratesnships.world.port;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A ship berth of a port (design.md §4.1): {@code pos} is the berth's centre at sea level (the first water column
 * beside the pier), {@code bow} the horizontal direction a moored ship's bow points.
 */
public record Berth(BlockPos pos, Direction bow) {

    public static final Codec<Berth> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(Berth::pos),
            Direction.CODEC.fieldOf("bow").forGetter(Berth::bow)
    ).apply(i, Berth::new));
}
