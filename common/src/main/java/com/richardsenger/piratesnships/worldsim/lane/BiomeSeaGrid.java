package com.richardsenger.piratesnships.worldsim.lane;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * A {@link SeaGrid} read from a level's biome source: a cell is sea when the noise biome at its centre (at sea level)
 * is in {@code #minecraft:is_ocean} or {@code #minecraft:is_deep_ocean}; rivers and beaches count as land. Samples the
 * climate noise as {@code world.port.PortService.portOf} does and never loads a chunk, so lanes can cross unexplored
 * sea. Answers are cached per cell. Server thread only.
 */
public final class BiomeSeaGrid implements SeaGrid {

    private final int cellBlocks;
    private final BiomeSource source;
    private final Climate.Sampler sampler;
    private final int quartY;
    private final Long2BooleanOpenHashMap cache = new Long2BooleanOpenHashMap();
    private long samples;

    public BiomeSeaGrid(int cellBlocks, BiomeSource source, Climate.Sampler sampler, int seaLevel) {
        if (cellBlocks < 1) throw new IllegalArgumentException("cellBlocks < 1");
        this.cellBlocks = cellBlocks;
        this.source = source;
        this.sampler = sampler;
        this.quartY = QuartPos.fromBlock(seaLevel);
    }

    /** The grid of {@code level}'s chunk generator. */
    public static BiomeSeaGrid of(ServerLevel level, int cellBlocks) {
        return new BiomeSeaGrid(cellBlocks, level.getChunkSource().getGenerator().getBiomeSource(),
                level.getChunkSource().randomState().sampler(), level.getSeaLevel());
    }

    /** Whether {@code biome} counts as open sea for lanes. */
    public static boolean isSeaBiome(Holder<Biome> biome) {
        return biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN);
    }

    @Override
    public int cellBlocks() {
        return cellBlocks;
    }

    @Override
    public boolean isSea(int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        if (cache.containsKey(key)) return cache.get(key);
        samples++;
        Holder<Biome> biome = source.getNoiseBiome(QuartPos.fromBlock(centreOf(cx)), quartY, QuartPos.fromBlock(centreOf(cz)), sampler);
        boolean sea = isSeaBiome(biome);
        cache.put(key, sea);
        return sea;
    }

    /** Biome samples taken so far (cache misses). */
    public long samples() {
        return samples;
    }
}
