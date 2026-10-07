package com.richardsenger.piratesnships.combat.cannon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server state of a cannon that is not in the block state: the elevation step and the game time the reload cooldown
 * ends. Saved with the block, so both move with the ship and survive a reload. Not synced: the client learns the
 * elevation from the action-bar message.
 */
public class CannonBlockEntity extends BlockEntity {

    /** Elevation step; -1 = not set yet, which means the level step of the current config. */
    private int elevation = -1;
    private long reloadUntil;

    public CannonBlockEntity(BlockPos pos, BlockState state) {
        super(CannonContent.CANNON_ENTITY.get(), pos, state);
    }

    /** The elevation step, clamped to the configured steps. */
    public int elevationStep() {
        int steps = CannonConfig.ELEVATION_STEPS.get();
        int e = elevation < 0 ? CannonConfig.levelStep() : elevation;
        return Math.max(0, Math.min(steps - 1, e));
    }

    public void setElevationStep(int step) {
        elevation = step;
        setChanged();
    }

    public long reloadUntil() {
        return reloadUntil;
    }

    public void setReloadUntil(long gameTime) {
        reloadUntil = gameTime;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("elevation", elevation);
        tag.putLong("reload_until", reloadUntil);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        elevation = tag.contains("elevation") ? tag.getInt("elevation") : -1;
        reloadUntil = tag.getLong("reload_until");
    }
}
