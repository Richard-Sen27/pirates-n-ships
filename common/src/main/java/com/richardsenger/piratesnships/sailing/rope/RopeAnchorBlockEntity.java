package com.richardsenger.piratesnships.sailing.rope;

import com.richardsenger.piratesnships.sailing.sail.CleatFrame;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The rope ends of a {@link RopeAnchor} block (RP1): up to {@link #MAX_ROPES} ropes, each stored as the offset to the
 * other anchor in this anchor's own frame ({@link CleatFrame}, by the block's horizontal facing), so that a rope
 * survives the ship's assembly (a translation) and a rotated disassembly (the facing turns, and the offset with it),
 * although block entity data itself is never rotated. Both ends of a rope store it, and a rope counts only while the
 * two agree ({@link RopeLines#partners}). Synced to clients, which draw the ropes.
 *
 * <p>The cleat's block entity extends this one (it adds the sail's cloth); the mooring ring uses it as it is, with its
 * own block entity type.
 */
public class RopeAnchorBlockEntity extends BlockEntity {

    /** Most ropes one anchor holds. */
    public static final int MAX_ROPES = 4;

    private static final String ROPES = "ropes";
    /** The single stay of a cleat saved before RP1. */
    private static final String LEGACY_STAY = "stay";

    private final List<Vec3i> ropesLocal = new ArrayList<>();

    public RopeAnchorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private int quarterTurns() {
        BlockState s = getBlockState();
        RopeAnchor a = RopeAnchor.of(s);
        return a == null ? 0 : a.quarterTurns(s);
    }

    /** The other ends of this anchor's ropes as stored here (not checked against the other anchors), in stored order. */
    public List<BlockPos> ropeTargets() {
        if (ropesLocal.isEmpty()) {
            return Collections.emptyList();
        }
        int q = quarterTurns();
        List<BlockPos> out = new ArrayList<>(ropesLocal.size());
        for (Vec3i l : ropesLocal) {
            int[] w = CleatFrame.toWorld(q, l.getX(), l.getY(), l.getZ());
            out.add(worldPosition.offset(w[0], w[1], w[2]));
        }
        return out;
    }

    public boolean hasRopes() {
        return !ropesLocal.isEmpty();
    }

    public int ropeCount() {
        return ropesLocal.size();
    }

    public boolean hasRopeTo(BlockPos target) {
        return ropesLocal.contains(local(target));
    }

    /** Server: adds a rope to {@code target}; false when it is there already or this anchor is full. */
    public boolean addRope(BlockPos target) {
        Vec3i l = local(target);
        if (ropesLocal.contains(l) || ropesLocal.size() >= MAX_ROPES) {
            return false;
        }
        ropesLocal.add(l);
        changed();
        return true;
    }

    /** Server: removes the rope to {@code target}; false when there was none. */
    public boolean removeRope(BlockPos target) {
        boolean removed = ropesLocal.remove(local(target));
        if (removed) {
            changed();
        }
        return removed;
    }

    /** Server: forgets every rope. */
    public void clearRopes() {
        if (!ropesLocal.isEmpty()) {
            ropesLocal.clear();
            changed();
        }
    }

    private Vec3i local(BlockPos target) {
        int[] l = CleatFrame.toLocal(quarterTurns(), target.getX() - worldPosition.getX(),
                target.getY() - worldPosition.getY(), target.getZ() - worldPosition.getZ());
        return new Vec3i(l[0], l[1], l[2]);
    }

    /** Saves and, on the server, sends the new data to clients. */
    protected void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // always written, also empty: the client ignores an empty update tag, and must still learn that the ropes are gone
        int[] flat = new int[ropesLocal.size() * 3];
        for (int i = 0; i < ropesLocal.size(); i++) {
            Vec3i l = ropesLocal.get(i);
            flat[3 * i] = l.getX();
            flat[3 * i + 1] = l.getY();
            flat[3 * i + 2] = l.getZ();
        }
        tag.putIntArray(ROPES, flat);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ropesLocal.clear();
        int[] flat = tag.contains(ROPES) ? tag.getIntArray(ROPES) : tag.getIntArray(LEGACY_STAY);
        for (int i = 0; i + 2 < flat.length && ropesLocal.size() < MAX_ROPES; i += 3) {
            Vec3i l = new Vec3i(flat[i], flat[i + 1], flat[i + 2]);
            if (!ropesLocal.contains(l)) {
                ropesLocal.add(l);
            }
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
}
