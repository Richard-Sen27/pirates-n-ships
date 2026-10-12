package com.richardsenger.piratesnships.chart.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * A structure a player has seen (work package CHART2): the structure's registry id and the centre of its bounds (x, z).
 * A wreck or another structure of this mod; ports are remembered by their port id instead ({@link ChartSightings}).
 */
public record KnownStructure(ResourceLocation structure, int x, int z) {

    public static final Codec<KnownStructure> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("structure").forGetter(KnownStructure::structure),
            Codec.INT.fieldOf("x").forGetter(KnownStructure::x),
            Codec.INT.fieldOf("z").forGetter(KnownStructure::z)
    ).apply(i, KnownStructure::new));
}
