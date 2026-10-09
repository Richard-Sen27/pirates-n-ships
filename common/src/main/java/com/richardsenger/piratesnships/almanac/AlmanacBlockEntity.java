package com.richardsenger.piratesnships.almanac;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The placed Captain's Almanac (ALM1, docs/design.md §3.4). Server: who reads it and when it was last right-clicked;
 * each tick {@link AlmanacOpenRule} decides whether it lies open, and a change is synced to the clients through the
 * update tag with a block update. Client: {@link #animation()} steps from the synced flag each tick and the renderer
 * draws the book from it. Neither the readers nor the open flag are saved: a tome loaded from disk lies closed.
 */
public class AlmanacBlockEntity extends BlockEntity {

    private static final String TAG_READ = "read";
    /** Vanilla's container rule: a reader stays within reach plus this many blocks. */
    static final double READER_REACH_SLACK = 4.0;

    private boolean read;
    private long openedAt = AlmanacOpenRule.NEVER;
    /** Server only: the players who have it open, by id (references so a test player outside the level counts too). */
    private final Map<UUID, ServerPlayer> readers = new HashMap<>();
    /** Client only: the book's open and page-turn state. */
    private final AlmanacAnimation animation = new AlmanacAnimation();

    public AlmanacBlockEntity(BlockPos pos, BlockState state) {
        super(AlmanacContent.ALMANAC_BLOCK_ENTITY.get(), pos, state);
    }

    /** Whether the tome lies open (being read); the server's decision, synced to the clients. */
    public boolean isRead() {
        return read;
    }

    /** Client: the book animation the renderer draws. */
    public AlmanacAnimation animation() {
        return animation;
    }

    /** Server: whether {@code player} has it open. */
    public boolean hasReader(UUID player) {
        return readers.containsKey(player);
    }

    /** Server: {@code player} right-clicked it at {@code gameTime}; it opens at once. */
    void startReading(ServerPlayer player, long gameTime) {
        readers.put(player.getUUID(), player);
        openedAt = gameTime;
        update();
    }

    /** Server: {@code player} closed it; it closes once {@link AlmanacOpenRule} says so. */
    void stopReading(UUID player) {
        if (readers.remove(player) != null) update();
    }

    /** Server tick: drop readers who left, logged out or changed dimension, then open or close. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, AlmanacBlockEntity be) {
        be.update();
    }

    /** Client tick: step the book animation. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, AlmanacBlockEntity be) {
        be.animation.tick(be.read, level.random::nextInt);
    }

    private void update() {
        Level level = getLevel();
        if (level == null || level.isClientSide) return;
        readers.values().removeIf(p -> p.isRemoved() || p.hasDisconnected() || p.level() != level
                || !p.canInteractWithBlock(getBlockPos(), READER_REACH_SLACK));
        boolean now = AlmanacOpenRule.open(AlmanacConfig.ENABLED.get(), level.getGameTime(), openedAt,
                AlmanacConfig.OPEN_TICKS.get(), !readers.isEmpty());
        if (!AlmanacConfig.ENABLED.get()) {
            readers.clear();
            openedAt = AlmanacOpenRule.NEVER;
        }
        if (now != read) {
            read = now;
            setChanged();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // only the update tag carries it; a tome loaded from disk lies closed
        read = tag.contains(TAG_READ, Tag.TAG_BYTE) && tag.getBoolean(TAG_READ);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG_READ, read);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
