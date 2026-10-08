package com.richardsenger.piratesnships.world.treasure;

import com.richardsenger.piratesnships.chart.data.CellClass;
import com.richardsenger.piratesnships.chart.data.ChartCells;
import com.richardsenger.piratesnships.chart.sample.ChartSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The picture of a treasure map (TM1): the {@link TreasureMapData#SIZE}-cell square around the site, sampled once when
 * the map is bound, from the server's heightmaps like the chart ({@link ChartSampler#sampleColumn}), but only in loaded
 * chunks: a cell in an unloaded chunk stays unknown parchment and is never loaded or generated for the map.
 */
public final class TreasureRaster {

    private TreasureRaster() {
    }

    /** Samples the cell classes around {@code site} (row-major, {@code SIZE * SIZE}) and adds the coast flags. */
    public static byte[] sample(ServerLevel level, BlockPos site, int cellBlocks, int shallowDepth) {
        int minCx = TreasureMapData.originCell(site.getX(), cellBlocks);
        int minCz = TreasureMapData.originCell(site.getZ(), cellBlocks);
        byte[] classes = new byte[TreasureMapData.CELLS];
        int half = cellBlocks / 2;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        LevelChunk chunk = null;
        int lastX = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        for (int z = 0; z < TreasureMapData.SIZE; z++) {
            int bz = (minCz + z) * cellBlocks + half;
            for (int x = 0; x < TreasureMapData.SIZE; x++) {
                int bx = (minCx + x) * cellBlocks + half;
                int chunkX = bx >> 4;
                int chunkZ = bz >> 4;
                if (chunkX != lastX || chunkZ != lastZ) {
                    chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    lastX = chunkX;
                    lastZ = chunkZ;
                }
                if (chunk == null) continue;
                classes[z * TreasureMapData.SIZE + x] = (byte) ChartSampler.sampleColumn(chunk, pos, bx, bz, shallowDepth).ordinal();
            }
        }
        return withCoast(classes);
    }

    /**
     * Cell bytes from cell class ordinals: a known land cell with water on one of its four sides inside the picture
     * gets the coast flag (as the chart's merge sets it). Pure.
     */
    public static byte[] withCoast(byte[] classes) {
        int n = TreasureMapData.SIZE;
        if (classes.length != n * n) throw new IllegalArgumentException("picture of " + classes.length + " cells");
        byte[] out = new byte[classes.length];
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                CellClass cls = CellClass.of(classes[z * n + x]);
                boolean coast = cls.isLand() && (water(classes, x + 1, z) || water(classes, x - 1, z)
                        || water(classes, x, z + 1) || water(classes, x, z - 1));
                out[z * n + x] = ChartCells.of(cls, coast);
            }
        }
        return out;
    }

    private static boolean water(byte[] classes, int x, int z) {
        int n = TreasureMapData.SIZE;
        if (x < 0 || z < 0 || x >= n || z >= n) return false;
        return CellClass.of(classes[z * n + x]).isWater();
    }
}
