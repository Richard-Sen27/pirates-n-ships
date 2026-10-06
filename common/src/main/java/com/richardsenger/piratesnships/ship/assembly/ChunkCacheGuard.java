package com.richardsenger.piratesnships.ship.assembly;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Flushes the server chunk cache's last-chunk memo so that plot chunks Sable replaced this tick are looked up again.
 *
 * <p>Why: {@code ServerChunkCache#getChunk} (used by {@code Level#getChunkAt}, {@code getBlockState},
 * {@code getBlockEntity}) answers from a 4-entry memo ({@code lastChunkPos}/{@code lastChunk}) before it asks the
 * chunk map, and that memo is cleared only once per tick ({@code ServerChunkCache#tick}) or when the chunk map is
 * promoted. Sable's {@code ServerChunkCacheMixin} redirects the chunk map lookups for plot chunks but not that memo,
 * and removing or allocating a plot ({@code ServerLevelPlot#onRemoveChunkHolder}, {@code #addChunkHolder}) does not
 * clear it. Sable gives a freed plot to the next new sub-level at once ({@code SubLevelContainer#getFirstEmptyPlot}),
 * so when a ship is removed and another assembled in the same tick, {@code Level#getChunkAt} can return the removed
 * ship's dead chunk (or Sable's empty placeholder) for the new plot. {@code SubLevelAssemblyHelper#moveBlocks} writes
 * block states through the chunk map (correct) but loads block entity data through {@code Level#getBlockEntity}
 * (stale): a chest arrives in the new ship empty, its items loaded into a block entity of the dead chunk.
 *
 * <p>The memo is private and has no public clear, so this pushes it out: eight lookups of distinct keys that are
 * never loaded (chunk coordinates far beyond the world border, outside Sable's plot grid) with
 * {@code requireChunk = false}. Such a lookup finds no holder, returns {@code null} without tickets, loading or
 * generation, and stores itself in the memo; with at most four of the eight already memoised, at least four miss, which
 * replaces all four entries.
 */
final class ChunkCacheGuard {

    /** Far beyond the world border (±30,000,000 blocks = ±1,875,000 chunks) and negative, so outside Sable's plots. */
    private static final int FAR_CHUNK = -1_990_000;
    private static final int LOOKUPS = 8;

    private ChunkCacheGuard() {
    }

    /** Flushes the memo. Call on the server thread around a Sable block move. */
    static void flush(ServerLevel level) {
        ServerChunkCache cache = level.getChunkSource();
        for (int i = 0; i < LOOKUPS; i++) {
            cache.getChunk(FAR_CHUNK - i, FAR_CHUNK, ChunkStatus.FULL, false);
        }
    }
}
