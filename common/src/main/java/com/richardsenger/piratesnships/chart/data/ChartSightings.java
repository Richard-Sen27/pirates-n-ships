package com.richardsenger.piratesnships.chart.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * What a player's chart knows of the world's structures (work package CHART2), stored per player as the persistent
 * attachment {@code pirates_n_ships:chart_sightings} (kept on death, like the chart): the ids of the ports they came
 * near (used when {@code chart.ports_always_known} is off) and the wrecks and other structures of this mod they came
 * near. Never forgotten. Immutable.
 */
public record ChartSightings(List<ResourceLocation> ports, List<KnownStructure> structures) {

    public static final ChartSightings EMPTY = new ChartSightings(List.of(), List.of());

    public static final Codec<ChartSightings> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("ports", List.of()).forGetter(ChartSightings::ports),
            KnownStructure.CODEC.listOf().optionalFieldOf("structures", List.of()).forGetter(ChartSightings::structures)
    ).apply(i, ChartSightings::new));

    public ChartSightings {
        ports = List.copyOf(ports);
        structures = List.copyOf(structures);
    }

    public boolean knowsPort(ResourceLocation id) {
        return ports.contains(id);
    }

    public boolean knows(KnownStructure s) {
        return structures.contains(s);
    }
}
