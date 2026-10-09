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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * The net end of a ratline fitting (RL2, a {@link ChainplateBlock} or a {@link MastHoopBlock}): the other fitting of
 * the one net it holds, and whether this end is the net's fitting A (the end the net was strung from, which draws it
 * and which its cells name). Nothing else. The offset to the other fitting is stored in this fitting's own frame
 * ({@link CleatFrame}, by its horizontal facing), so a net survives the ship's assembly (a translation) and a rotated
 * disassembly (the facing turns, and the offset with it), although block entity data itself is never rotated. Synced
 * to clients, which draw the net from A.
 */
public class NetFittingBlockEntity extends BlockEntity {

    private static final String OTHER = "net_other";
    private static final String IS_A = "net_a";

    private @Nullable Vec3i otherLocal;
    private boolean isA;

    public NetFittingBlockEntity(BlockPos pos, BlockState state) {
        super(RiggingContent.NET_FITTING_BLOCK_ENTITY.get(), pos, state);
    }

    private int quarterTurns() {
        BlockState s = getBlockState();
        return s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? s.getValue(BlockStateProperties.HORIZONTAL_FACING).get2DDataValue() : 0;
    }

    /** Whether this fitting holds a net. */
    public boolean hasNet() {
        return otherLocal != null;
    }

    /** Whether this fitting is the net's A end (it draws the net; the cells name it). */
    public boolean isA() {
        return otherLocal != null && isA;
    }

    /** The other fitting of the net held here, or null. */
    public @Nullable BlockPos partner() {
        if (otherLocal == null) {
            return null;
        }
        int[] w = CleatFrame.toWorld(quarterTurns(), otherLocal.getX(), otherLocal.getY(), otherLocal.getZ());
        return worldPosition.offset(w[0], w[1], w[2]);
    }

    /** The net's A end: this fitting when it is A, else its partner; null without a net. */
    public @Nullable BlockPos netA() {
        return isA() ? worldPosition : partner();
    }

    /** Server: this fitting now holds a net to {@code other}; {@code a} when this end is A. */
    public void setNet(BlockPos other, boolean a) {
        int[] l = CleatFrame.toLocal(quarterTurns(), other.getX() - worldPosition.getX(),
                other.getY() - worldPosition.getY(), other.getZ() - worldPosition.getZ());
        otherLocal = new Vec3i(l[0], l[1], l[2]);
        isA = a;
        changed();
    }

    /** Server: forgets the net. */
    public void clearNet() {
        if (otherLocal != null) {
            otherLocal = null;
            isA = false;
            changed();
        }
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // always written, also empty: the client ignores an empty update tag and must still learn that the net is gone
        tag.putIntArray(OTHER, otherLocal == null ? new int[0]
                : new int[] {otherLocal.getX(), otherLocal.getY(), otherLocal.getZ()});
        tag.putBoolean(IS_A, isA);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int[] o = tag.getIntArray(OTHER);
        otherLocal = o.length == 3 ? new Vec3i(o[0], o[1], o[2]) : null;
        isA = otherLocal != null && tag.getBoolean(IS_A);
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
