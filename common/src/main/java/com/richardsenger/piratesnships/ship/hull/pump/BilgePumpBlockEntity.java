package com.richardsenger.piratesnships.ship.hull.pump;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The bilge pump's block entity (PMP1): it exists so {@code client/PumpHandleRenderer} can draw the rocking handle, and
 * carries the one thing the client needs for it, whether the pump is being worked. On the server the hull runtime marks
 * every tick the pump drains ({@link BilgePumps#markWorked}); {@link #serverTick} turns that into the synced flag
 * ({@link PumpActivity}: on at once, off a few ticks after the last stroke, at most one update a second) and sends a
 * block update on each change; a client loading the chunk gets it in the update tag. Nothing is saved: a reloaded pump
 * is at rest until someone pumps again. The flood state itself stays in the hull runtime.
 */
public class BilgePumpBlockEntity extends BlockEntity {

    static final String TAG_PUMPING = "pumping";

    /** Server: the pumping flag and its sync timing. */
    private final PumpActivity activity = new PumpActivity();
    /** Client: the flag as last received. */
    private boolean pumping;
    /** Client: the drawn handle swing. */
    private final PumpHandleSwing swing = new PumpHandleSwing();

    public BilgePumpBlockEntity(BlockPos pos, BlockState state) {
        super(HullRepairContent.BILGE_PUMP_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BilgePumpBlockEntity be) {
        if (be.activity.tick(level.getGameTime())) {
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** Server: the pumping flag's state ({@link BilgePumps#markWorked} reports into it). */
    public PumpActivity activity() {
        return activity;
    }

    /** Whether the pump is being worked: the synced flag on the server, the received one on a client. */
    public boolean pumping() {
        return level != null && !level.isClientSide ? activity.synced() : pumping;
    }

    /** Client: the eased handle swing, read by the renderer. */
    public PumpHandleSwing swing() {
        return swing;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        pumping = tag.getBoolean(TAG_PUMPING);
    }

    /** What a client needs to draw the pump: the pumping flag only. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG_PUMPING, activity.synced());
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
