package com.richardsenger.piratesnships.worldsim.lane;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * A {@link SeaGrid} read from a level's biome source: a cell is open sea when the noise biome at its centre (at sea
 * level) is in {@code #minecraft:is_ocean} or {@code #minecraft:is_deep_ocean}. With {@code shores} a cell whose biome
 * is in {@code #minecraft:is_beach} is shore (passable at a cost, {@link #isShore}), otherwise land; rivers are always
 * land. Shores exist because the biome map is not the terrain: in 1.18+ worldgen the beach biome follows a band of the
 * continentalness noise while the terrain height follows others, so a "beach" cell often lies over water ten blocks
 * deep, and a port's bay can be an ocean pocket ringed by beach cells (LANE1, seed 2718281828: the seafarer village's
 * own cell was beach and its nearest ocean cell a 6-cell pocket, cut off from the sea the village really lies on).
 * Samples the climate noise as {@code world.port.PortService.portOf} does and never loads a chunk, so lanes can cross
 * unexplored sea. Answers are cached per cell. Server thread only.
 */
public final class BiomeSeaGrid implements SeaGrid {

    private static final byte LAND = 0, SEA = 1, SHORE = 2;

    private final int cellBlocks;
    private final BiomeSource source;
    private final Climate.Sampler sampler;
    private final int quartY;
    private final boolean shores;
    /** Per cell: {@link #LAND}, {@link #SEA} or {@link #SHORE}; -1 when not sampled yet. */
    private final Long2ByteOpenHashMap cache = new Long2ByteOpenHashMap();
    private long samples;

    /** Ocean biomes only (beaches are land). */
    public BiomeSeaGrid(int cellBlocks, BiomeSource source, Climate.Sampler sampler, int seaLevel) {
        this(cellBlocks, source, sampler, seaLevel, false);
    }

    /** With {@code shores}, beach cells are passable shore. */
    public BiomeSeaGrid(int cellBlocks, BiomeSource source, Climate.Sampler sampler, int seaLevel, boolean shores) {
        if (cellBlocks < 1) throw new IllegalArgumentException("cellBlocks < 1");
        this.cellBlocks = cellBlocks;
        this.source = source;
        this.sampler = sampler;
        this.quartY = QuartPos.fromBlock(seaLevel);
        this.shores = shores;
        cache.defaultReturnValue((byte) -1);
    }

    /** The grid of {@code level}'s chunk generator, ocean biomes only. */
    public static BiomeSeaGrid of(ServerLevel level, int cellBlocks) {
        return of(level, cellBlocks, false);
    }

    /** The grid of {@code level}'s chunk generator; with {@code shores} beach cells are passable shore. */
    public static BiomeSeaGrid of(ServerLevel level, int cellBlocks, boolean shores) {
        return new BiomeSeaGrid(cellBlocks, level.getChunkSource().getGenerator().getBiomeSource(),
                level.getChunkSource().randomState().sampler(), level.getSeaLevel(), shores);
    }

    /** Whether {@code biome} counts as open sea for lanes. */
    public static boolean isSeaBiome(Holder<Biome> biome) {
        return biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN);
    }

    /** Whether beach cells count as passable shore. */
    public boolean shores() {
        return shores;
    }

    @Override
    public int cellBlocks() {
        return cellBlocks;
    }

    @Override
    public boolean isSea(int cx, int cz) {
        return kind(cx, cz) != LAND;
    }

    @Override
    public boolean isShore(int cx, int cz) {
        return kind(cx, cz) == SHORE;
    }

    private byte kind(int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        byte k = cache.get(key);
        if (k >= 0) return k;
        samples++;
        Holder<Biome> biome = source.getNoiseBiome(QuartPos.fromBlock(centreOf(cx)), quartY, QuartPos.fromBlock(centreOf(cz)), sampler);
        k = isSeaBiome(biome) ? SEA : shores && biome.is(BiomeTags.IS_BEACH) ? SHORE : LAND;
        cache.put(key, k);
        return k;
    }

    /** Biome samples taken so far (cache misses). */
    public long samples() {
        return samples;
    }
}
