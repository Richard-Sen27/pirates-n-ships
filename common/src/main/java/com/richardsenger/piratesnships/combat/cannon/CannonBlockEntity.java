package com.richardsenger.piratesnships.combat.cannon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Server state of a cannon that is not in the block state: the elevation step and the game time the reload cooldown
 * ends. Saved with the block, so both move with the ship and survive a reload. The elevation step is synced to clients
 * (CAN2: the update tag on chunk load, a block entity data packet on every change), where
 * {@code client/CannonBarrelRenderer} draws the barrel at it; the reload cooldown stays on the server.
 */
public class CannonBlockEntity extends BlockEntity {

    static final String TAG_ELEVATION = "elevation";

    /** Elevation step; -1 = not set yet, which means the level step of the current config. */
    private int elevation = -1;
    private long reloadUntil;
    /** Client only: the drawn barrel angle eased between steps. */
    private final CannonBarrelTilt tilt = new CannonBarrelTilt();

    public CannonBlockEntity(BlockPos pos, BlockState state) {
        super(CannonContent.CANNON_ENTITY.get(), pos, state);
    }

    /** The elevation step, clamped to the configured steps. */
    public int elevationStep() {
        int steps = CannonConfig.ELEVATION_STEPS.get();
        int e = elevation < 0 ? CannonConfig.levelStep() : elevation;
        return Math.max(0, Math.min(steps - 1, e));
    }

    /** Sets the elevation step; on the server a change is saved and sent to clients, which tilt the drawn barrel. */
    public void setElevationStep(int step) {
        boolean changed = step != elevation;
        elevation = step;
        setChanged();
        if (changed && level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public long reloadUntil() {
        return reloadUntil;
    }

    public void setReloadUntil(long gameTime) {
        reloadUntil = gameTime;
        setChanged();
    }

    /** The client's eased barrel angle (CAN2), read by the renderer. */
    public CannonBarrelTilt tilt() {
        return tilt;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ELEVATION, elevation);
        tag.putLong("reload_until", reloadUntil);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        elevation = tag.contains(TAG_ELEVATION) ? tag.getInt(TAG_ELEVATION) : -1;
        reloadUntil = tag.getLong("reload_until");
    }

    /** What a client needs to draw the gun: the elevation step only (CAN2). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_ELEVATION, elevation);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
