package com.richardsenger.piratesnships.worldsim.materialize;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Chunk loading for a voyage about to appear (WS3b): the vanilla region ticket {@code pirates_n_ships:voyage} with
 * radius {@value #RADIUS} around the spawn point, held for {@code ticket_ticks} and then released by {@link #tick}
 * (the ticket type has no lifespan of its own so the config value applies without a restart). Loader-free:
 * {@code ServerChunkCache#addRegionTicket}/{@code removeRegionTicket}.
 */
public final class VoyageChunks {

    public static final TicketType<ChunkPos> TICKET = TicketType.create("pirates_n_ships:voyage", Comparator.comparingLong(ChunkPos::toLong));
    public static final int RADIUS = 2;

    private record Key(ResourceKey<Level> dimension, long chunk) { }

    private static final Map<Key, Long> HELD = new HashMap<>();

    private VoyageChunks() {
    }

    /**
     * Whether the chunks under a ship of {@code reach} blocks around {@code (x, z)} are loaded and the centre chunk
     * ticks entities (so the fighters and crew can be spawned and seen).
     */
    public static boolean ready(ServerLevel level, double x, double z, int reach) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        return level.hasChunksAt(bx - reach, bz - reach, bx + reach, bz + reach)
                && level.isPositionEntityTicking(new BlockPos(bx, level.getSeaLevel(), bz));
    }

    /** Takes (or renews) the ticket at the chunk of {@code (x, z)} until {@code now + ticket_ticks}. */
    public static void request(ServerLevel level, double x, double z, long now) {
        ChunkPos chunk = new ChunkPos(BlockPos.containing(x, 0, z));
        Key key = new Key(level.dimension(), chunk.toLong());
        if (!HELD.containsKey(key)) {
            level.getChunkSource().addRegionTicket(TICKET, chunk, RADIUS, chunk);
        }
        HELD.put(key, now + MaterializeConfig.TICKET_TICKS.get());
    }

    /** Whether a ticket of ours is held at the chunk of {@code (x, z)}. */
    public static boolean held(ServerLevel level, double x, double z) {
        return HELD.containsKey(new Key(level.dimension(), new ChunkPos(BlockPos.containing(x, 0, z)).toLong()));
    }

    /** Releases the tickets of {@code level} that ran out. */
    public static void tick(ServerLevel level, long now) {
        for (Iterator<Map.Entry<Key, Long>> it = HELD.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Key, Long> e = it.next();
            if (e.getKey().dimension() != level.dimension() || e.getValue() > now) continue;
            ChunkPos chunk = new ChunkPos(e.getKey().chunk());
            level.getChunkSource().removeRegionTicket(TICKET, chunk, RADIUS, chunk);
            it.remove();
        }
    }

    /** Tickets die with the server; forget them. */
    public static void clear() {
        HELD.clear();
    }
}
