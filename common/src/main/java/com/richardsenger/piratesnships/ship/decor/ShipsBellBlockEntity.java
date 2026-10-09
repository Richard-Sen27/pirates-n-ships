package com.richardsenger.piratesnships.ship.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * When the ship's bell was last rung and which way the strike pushed it (BELL1, design.md §4.8 "Decor"). Set on the
 * server by {@link ShipsBellBlock#ring} (use and WS5's raid alarm alike) and synced to the client through the update
 * tag with a block update; {@code ShipsBellRenderer} swings the bell from it ({@link ShipsBellSwing}). Saved too, so a
 * bell rung just before its chunk is saved or carried onto a ship keeps its game time; a ring long past is at rest.
 */
public class ShipsBellBlockEntity extends BlockEntity {

    /** No ring yet. */
    public static final long NEVER = Long.MIN_VALUE;
    private static final String TAG_RING_START = "ring_start";
    private static final String TAG_STRIKE = "strike";

    private long ringStart = NEVER;
    private @Nullable Direction strike;

    public ShipsBellBlockEntity(BlockPos pos, BlockState state) {
        super(ShipDecor.SHIPS_BELL_BLOCK_ENTITY.get(), pos, state);
    }

    /** The game time of the last ring, {@link #NEVER} for none. */
    public long ringStart() {
        return ringStart;
    }

    /** The horizontal direction the last strike pushed the bell (striker towards bell); null for a raid alarm or none. */
    public @Nullable Direction strike() {
        return strike;
    }

    /** Ticks since the last ring at game time {@code now} (fractional with the partial tick), or -1 for none. */
    public double sinceRing(double now) {
        return ringStart == NEVER ? -1.0 : now - ringStart;
    }

    /** Records a ring at {@code gameTime}; the caller sends the block update. */
    void ring(long gameTime, @Nullable Direction push) {
        ringStart = gameTime;
        strike = push != null && push.getAxis().isHorizontal() ? push : null;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ringStart != NEVER) {
            tag.putLong(TAG_RING_START, ringStart);
            if (strike != null) tag.putByte(TAG_STRIKE, (byte) strike.get3DDataValue());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ringStart = tag.contains(TAG_RING_START, Tag.TAG_LONG) ? tag.getLong(TAG_RING_START) : NEVER;
        strike = tag.contains(TAG_STRIKE, Tag.TAG_BYTE) ? Direction.from3DDataValue(tag.getByte(TAG_STRIKE)) : null;
        if (strike != null && !strike.getAxis().isHorizontal()) strike = null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
