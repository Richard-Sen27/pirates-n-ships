package com.richardsenger.piratesnships.chart.sample;

import com.richardsenger.piratesnships.chart.data.CellClass;
import com.richardsenger.piratesnships.chart.data.SampleGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Reads the world around a point into a {@link SampleGrid} (work package MAP1): every cell whose centre lies within
 * {@code radius} blocks is sampled at its centre column, but only in chunks that are already loaded (never loads or
 * generates a chunk). The top of the column comes from the {@code MOTION_BLOCKING} heightmap, the water depth from
 * the difference to {@code OCEAN_FLOOR} (kelp and seagrass hold water and are not motion blocking, so they count as
 * water).
 */
public final class ChartSampler {

    private ChartSampler() {
    }

    /** Cell coordinate of block coordinate {@code b}. */
    public static int cellOf(int b, int cellBlocks) {
        return Math.floorDiv(b, cellBlocks);
    }

    public static SampleGrid sample(ServerLevel level, double x, double z, int radius, int cellBlocks, int shallowDepth) {
        int minCx = cellOf((int) Math.floor(x) - radius, cellBlocks);
        int maxCx = cellOf((int) Math.floor(x) + radius, cellBlocks);
        int minCz = cellOf((int) Math.floor(z) - radius, cellBlocks);
        int maxCz = cellOf((int) Math.floor(z) + radius, cellBlocks);
        SampleGrid grid = SampleGrid.empty(minCx, minCz, maxCx - minCx + 1, maxCz - minCz + 1);
        int half = cellBlocks / 2;
        double r2 = (double) radius * radius;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        LevelChunk chunk = null;
        for (int cz = minCz; cz <= maxCz; cz++) {
            int bz = cz * cellBlocks + half;
            for (int cx = minCx; cx <= maxCx; cx++) {
                int bx = cx * cellBlocks + half;
                double dx = bx + 0.5 - x;
                double dz = bz + 0.5 - z;
                if (dx * dx + dz * dz > r2) continue;
                int chunkX = bx >> 4;
                int chunkZ = bz >> 4;
                if (chunk == null || chunk.getPos().x != chunkX || chunk.getPos().z != chunkZ) {
                    chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    if (chunk == null) continue;
                }
                grid.set(cx, cz, sampleColumn(chunk, pos, bx, bz, shallowDepth));
            }
        }
        return grid;
    }

    /** The class of the column at block {@code (bx, bz)} of a loaded chunk. */
    public static CellClass sampleColumn(LevelChunk chunk, BlockPos.MutableBlockPos pos, int bx, int bz, int shallowDepth) {
        int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        if (top < chunk.getMinBuildHeight()) return CellClass.UNKNOWN;
        var topState = chunk.getBlockState(pos.set(bx, top, bz));
        var above = chunk.getBlockState(pos.set(bx, top + 1, bz));
        int floor = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, bx, bz);
        return ChartClassifier.classify(topState, above, top - floor, shallowDepth);
    }
}
