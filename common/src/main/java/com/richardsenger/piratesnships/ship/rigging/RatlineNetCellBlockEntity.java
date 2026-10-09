package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.sailing.sail.CleatFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A net cell's fitting A (RL2): the offset to the fitting the net was strung from, in the cell's own frame
 * ({@link CleatFrame} by the cell's {@link RatlineNetCellBlock#FACING}, which a rotation turns), so the cell finds A
 * again after an assembly or a rotated disassembly. Synced to clients: the climbing assist runs where the climber's
 * movement is computed, which is the client for a player.
 */
public class RatlineNetCellBlockEntity extends BlockEntity {

    private static final String A = "net_a";

    private @Nullable Vec3i aLocal;

    public RatlineNetCellBlockEntity(BlockPos pos, BlockState state) {
        super(RiggingContent.NET_CELL_BLOCK_ENTITY.get(), pos, state);
    }

    private int quarterTurns() {
        return getBlockState().getValue(RatlineNetCellBlock.FACING).get2DDataValue();
    }

    /** The net's fitting A, or null for a cell that lost it. */
    public @Nullable BlockPos fittingA() {
        if (aLocal == null) {
            return null;
        }
        int[] w = CleatFrame.toWorld(quarterTurns(), aLocal.getX(), aLocal.getY(), aLocal.getZ());
        return worldPosition.offset(w[0], w[1], w[2]);
    }

    public void setFittingA(BlockPos a) {
        int[] l = CleatFrame.toLocal(quarterTurns(), a.getX() - worldPosition.getX(), a.getY() - worldPosition.getY(),
                a.getZ() - worldPosition.getZ());
        aLocal = new Vec3i(l[0], l[1], l[2]);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (aLocal != null) {
            tag.putIntArray(A, new int[] {aLocal.getX(), aLocal.getY(), aLocal.getZ()});
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int[] a = tag.getIntArray(A);
        aLocal = a.length == 3 ? new Vec3i(a[0], a[1], a[2]) : null;
    }
}
