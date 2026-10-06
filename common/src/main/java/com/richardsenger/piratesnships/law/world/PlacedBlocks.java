package com.richardsenger.piratesnships.law.world;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.attachment.AttachmentKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Positions (as {@link BlockPos#asLong()}) of block-entity blocks a player placed in one chunk, saved with the chunk.
 * A container a player placed is never "the village's", so taking from it is never theft. Only blocks with a block
 * entity are recorded, so the set stays small. A player breaking the block removes the entry.
 */
public record PlacedBlocks(Set<Long> positions) {

    public static final PlacedBlocks EMPTY = new PlacedBlocks(Set.of());

    public static final Codec<PlacedBlocks> CODEC = Codec.LONG.listOf().xmap(
            l -> new PlacedBlocks(Set.copyOf(l)),
            p -> p.positions().stream().sorted().toList());

    /** Chunk attachment: player-placed block entities. */
    public static final AttachmentKey<PlacedBlocks> KEY = AttachmentKey.builder("player_placed_blocks", () -> EMPTY)
            .persistent(CODEC)
            .build();

    public PlacedBlocks {
        positions = Set.copyOf(positions);
    }

    public boolean contains(BlockPos pos) {
        return positions.contains(pos.asLong());
    }

    public PlacedBlocks with(BlockPos pos) {
        if (contains(pos)) return this;
        Set<Long> s = new HashSet<>(positions);
        s.add(pos.asLong());
        return new PlacedBlocks(s);
    }

    public PlacedBlocks without(BlockPos pos) {
        if (!contains(pos)) return this;
        Set<Long> s = new HashSet<>(positions);
        s.remove(pos.asLong());
        return new PlacedBlocks(s);
    }

    // --- World access -------------------------------------------------------------------------------------------

    public static void init() {
        Services.ATTACHMENTS.register(KEY);
    }

    public static boolean isPlayerPlaced(Level level, BlockPos pos) {
        ChunkAccess chunk = level.getChunk(pos);
        return Services.ATTACHMENTS.has(chunk, KEY) && Services.ATTACHMENTS.get(chunk, KEY).contains(pos);
    }

    public static void markPlayerPlaced(Level level, BlockPos pos) {
        ChunkAccess chunk = level.getChunk(pos);
        PlacedBlocks current = Services.ATTACHMENTS.get(chunk, KEY);
        PlacedBlocks next = current.with(pos);
        if (next != current) Services.ATTACHMENTS.set(chunk, KEY, next);
    }

    public static void forget(Level level, BlockPos pos) {
        ChunkAccess chunk = level.getChunk(pos);
        if (!Services.ATTACHMENTS.has(chunk, KEY)) return;
        PlacedBlocks current = Services.ATTACHMENTS.get(chunk, KEY);
        PlacedBlocks next = current.without(pos);
        if (next == current) return;
        if (next.positions().isEmpty()) Services.ATTACHMENTS.remove(chunk, KEY);
        else Services.ATTACHMENTS.set(chunk, KEY, next);
    }

    /** Listener for {@code CommonEvents.BLOCK_PLACE}. Never cancels. */
    public static boolean onBlockPlace(Level level, BlockPos pos, BlockState placed, @Nullable Entity placer) {
        if (!level.isClientSide() && placer instanceof Player && placed.hasBlockEntity()) markPlayerPlaced(level, pos);
        return false;
    }

    /** Listener for {@code CommonEvents.BLOCK_BREAK}. Never cancels. */
    public static boolean onBlockBreak(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) forget(level, pos);
        return false;
    }

    /** For tests: the stored positions of a chunk. */
    public static List<Long> stored(ChunkAccess chunk) {
        return Services.ATTACHMENTS.get(chunk, KEY).positions().stream().sorted().toList();
    }
}
