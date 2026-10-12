package com.richardsenger.piratesnships.chart.sample;

import com.richardsenger.piratesnships.chart.data.CellClass;
import com.richardsenger.piratesnships.chart.data.SampleGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.Arrays;

/**
 * Reads the world around a point into a {@link SampleGrid} (work package MAP1): every cell whose centre lies within
 * {@code radius} blocks is sampled at its centre column, but only in chunks that are already loaded (never loads or
 * generates a chunk). The top of the column comes from the {@code MOTION_BLOCKING} heightmap, the water depth from
 * the difference to {@code OCEAN_FLOOR} (kelp and seagrass hold water and are not motion blocking, so they count as
 * water).
 *
 * <p>CHART4: each sampled cell also gets vanilla's map colour ({@link MapColours}) from {@code colourColumns} by
 * {@code colourColumns} of its columns, spread evenly over the cell (all of them when it equals the cell size). The
 * brightness step compares the cell's mean height with the cell to its north, so the row north of the circle's cells
 * is read too (heights only matter there, nothing of it is stored). A column is the top block with a map colour below
 * the {@code WORLD_SURFACE} heightmap; the water depth under a fluid comes from the {@code OCEAN_FLOOR} heightmap.
 */
public final class ChartSampler {

    /** How far down a column is searched for a block with a map colour (glass roofs, barriers). */
    static final int MAX_COLOURLESS = 64;

    private ChartSampler() {
    }

    /** Cell coordinate of block coordinate {@code b}. */
    public static int cellOf(int b, int cellBlocks) {
        return Math.floorDiv(b, cellBlocks);
    }

    /** {@link #sample(ServerLevel, double, double, int, int, int, int)} reading every column of a cell for its colour. */
    public static SampleGrid sample(ServerLevel level, double x, double z, int radius, int cellBlocks, int shallowDepth) {
        return sample(level, x, z, radius, cellBlocks, shallowDepth, cellBlocks);
    }

    public static SampleGrid sample(ServerLevel level, double x, double z, int radius, int cellBlocks, int shallowDepth, int colourColumns) {
        int minCx = cellOf((int) Math.floor(x) - radius, cellBlocks);
        int maxCx = cellOf((int) Math.floor(x) + radius, cellBlocks);
        int minCz = cellOf((int) Math.floor(z) - radius, cellBlocks);
        int maxCz = cellOf((int) Math.floor(z) + radius, cellBlocks);
        int w = maxCx - minCx + 1;
        SampleGrid grid = SampleGrid.empty(minCx, minCz, w, maxCz - minCz + 1);
        int half = cellBlocks / 2;
        double r2 = (double) radius * radius;
        int per = Math.max(1, Math.min(colourColumns, cellBlocks));
        int[] offsets = new int[per];
        for (int k = 0; k < per; k++) offsets[k] = (k * cellBlocks) / per + (cellBlocks / per) / 2;
        Reader reader = new Reader(level, per * per);
        double[] north = new double[w];
        double[] row = new double[w];
        Arrays.fill(north, Double.NaN);
        for (int cz = minCz - 1; cz <= maxCz; cz++) {
            Arrays.fill(row, Double.NaN);
            int bz = cz * cellBlocks + half;
            for (int cx = minCx; cx <= maxCx; cx++) {
                int bx = cx * cellBlocks + half;
                boolean inside = cz >= minCz && inCircle(bx, bz, x, z, r2);
                boolean northOfInside = cz < maxCz && inCircle(bx, bz + cellBlocks, x, z, r2);
                if (!inside && !northOfInside) continue;
                CellClass cls = CellClass.UNKNOWN;
                if (inside) {
                    LevelChunk chunk = reader.chunk(bx, bz);
                    if (chunk == null) continue;
                    cls = sampleColumn(chunk, reader.pos, bx, bz, shallowDepth);
                }
                int n = reader.readCell(cx * cellBlocks, cz * cellBlocks, offsets);
                if (n > 0) row[cx - minCx] = MapColours.meanHeight(reader.heights, n);
                if (inside) {
                    int colour = MapColours.cellColour(reader.ids, reader.heights, reader.depths, n, north[cx - minCx], (cx + cz) & 1, cellBlocks);
                    grid.set(cx, cz, cls, colour);
                }
            }
            double[] t = north;
            north = row;
            row = t;
        }
        return grid;
    }

    private static boolean inCircle(int bx, int bz, double x, double z, double r2) {
        double dx = bx + 0.5 - x;
        double dz = bz + 0.5 - z;
        return dx * dx + dz * dz <= r2;
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

    /**
     * CHART4: vanilla's map column at block {@code (bx, bz)} of a loaded chunk, or null when the column is empty: the
     * first block with a map colour from the top (at most {@link #MAX_COLOURLESS} colourless blocks are skipped), the
     * fluid it holds shown as the fluid, and the water depth to the ocean floor under a fluid.
     */
    public static MapColours.Column readColumn(ServerLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos, int bx, int bz) {
        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz);
        int min = chunk.getMinBuildHeight();
        if (y < min) return null;
        BlockState state = chunk.getBlockState(pos.set(bx, y, bz));
        for (int skipped = 0; state.getMapColor(level, pos) == MapColor.NONE && y > min && skipped < MAX_COLOURLESS; skipped++) {
            state = chunk.getBlockState(pos.set(bx, --y, bz));
        }
        int depth = 0;
        if (!state.getFluidState().isEmpty()) {
            depth = Math.max(0, y - chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, bx, bz));
            state = MapColours.shownState(state, level, pos);
        }
        return new MapColours.Column(state.getMapColor(level, pos).id, y, depth);
    }

    /** Reads the colour columns of one cell at a time, remembering the last chunk. */
    private static final class Reader {
        final ServerLevel level;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final int[] ids;
        final int[] heights;
        final int[] depths;
        LevelChunk chunk;

        Reader(ServerLevel level, int max) {
            this.level = level;
            ids = new int[max];
            heights = new int[max];
            depths = new int[max];
        }

        LevelChunk chunk(int bx, int bz) {
            int chunkX = bx >> 4;
            int chunkZ = bz >> 4;
            if (chunk == null || chunk.getPos().x != chunkX || chunk.getPos().z != chunkZ) {
                chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            }
            return chunk;
        }

        /** Reads the columns of the cell whose north-west block is {@code (x0, z0)}; returns how many were loaded. */
        int readCell(int x0, int z0, int[] offsets) {
            int n = 0;
            for (int ox : offsets) {
                for (int oz : offsets) {
                    int bx = x0 + ox;
                    int bz = z0 + oz;
                    LevelChunk c = chunk(bx, bz);
                    if (c == null) continue;
                    MapColours.Column col = readColumn(level, c, pos, bx, bz);
                    if (col == null) continue;
                    ids[n] = col.colourId();
                    heights[n] = col.height();
                    depths[n] = col.waterDepth();
                    n++;
                }
            }
            return n;
        }
    }
}
