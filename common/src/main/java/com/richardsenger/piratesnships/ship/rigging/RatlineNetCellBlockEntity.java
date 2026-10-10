package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.sailing.sail.CleatFrame;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Cell;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Point;
import com.richardsenger.piratesnships.ship.rigging.RatlineNets.Strand;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A net cell's net (RL2, RL4): the strand that owns it (its fittings A and B), the hub of that strand's fan (the hoop)
 * and whether the cell is a fill cell of the triangle between two strands rather than one of the strand's own line. The
 * offsets are kept in the cell's own frame ({@link CleatFrame} by the cell's {@link RatlineNetCellBlock#FACING}, which a
 * rotation turns), so the cell finds its fittings again after an assembly or a rotated disassembly. Synced to clients:
 * the climbing assist runs where the climber's movement is computed, which is the client for a player.
 *
 * <p>For the climbing assist a cell answers its {@link #climbDirection()} (unit, towards the hub's ring, in the frame
 * of the cell's level: the ship's plot on a ship) and whether it {@link #isTop()} (the next cell along that direction
 * is the hub's block). Both are derived from the stored fittings, so they turn with the ship.
 *
 * <p>RL5: a fill cell between two strands with different hubs (a rectangle or a trapezoid) also names the other hub
 * ({@link #hub2()}); it climbs towards the nearest point between the two hubs' rings ({@link RatlineNetRules#topPoint}).
 */
public class RatlineNetCellBlockEntity extends BlockEntity {

    private static final String A = "net_a";
    private static final String B = "net_b";
    private static final String HUB = "net_hub";
    private static final String FILL = "net_fill";
    private static final String HUB2 = "net_hub2";

    private @Nullable Vec3i aLocal;
    private @Nullable Vec3i bLocal;
    private @Nullable Vec3i hubLocal;
    private @Nullable Vec3i hub2Local;
    private boolean fill;

    public RatlineNetCellBlockEntity(BlockPos pos, BlockState state) {
        super(RiggingContent.NET_CELL_BLOCK_ENTITY.get(), pos, state);
    }

    private int quarterTurns() {
        return getBlockState().getValue(RatlineNetCellBlock.FACING).get2DDataValue();
    }

    private @Nullable BlockPos world(@Nullable Vec3i local) {
        if (local == null) {
            return null;
        }
        int[] w = CleatFrame.toWorld(quarterTurns(), local.getX(), local.getY(), local.getZ());
        return worldPosition.offset(w[0], w[1], w[2]);
    }

    private @Nullable Vec3i local(@Nullable BlockPos p) {
        if (p == null) {
            return null;
        }
        int[] l = CleatFrame.toLocal(quarterTurns(), p.getX() - worldPosition.getX(), p.getY() - worldPosition.getY(),
                p.getZ() - worldPosition.getZ());
        return new Vec3i(l[0], l[1], l[2]);
    }

    /** The owning strand's fitting A, or null for a cell that lost it. */
    public @Nullable BlockPos fittingA() {
        return world(aLocal);
    }

    /** The owning strand's fitting B; null for a cell strung before RL4 (its net is the one A holds). */
    public @Nullable BlockPos fittingB() {
        return world(bLocal);
    }

    /** The hub of the owning strand's fan; null for a cell strung before RL4. */
    public @Nullable BlockPos hub() {
        return world(hubLocal);
    }

    /** RL5: the other hub of the pair a fill cell lies between, when it is not the owner's hub; else null. */
    public @Nullable BlockPos hub2() {
        return world(hub2Local);
    }

    /** Whether this cell fills the triangle between two strands (RL4) rather than lying on its strand's line. */
    public boolean isFill() {
        return fill;
    }

    /** RL2: names fitting A only (a cell of a lone net). */
    public void setFittingA(BlockPos a) {
        aLocal = local(a);
        changed();
    }

    /** Server: the cell belongs to the strand from {@code a} to {@code b} of the fan at {@code hub}; {@code fill} for a triangle cell. */
    public void setOwner(BlockPos a, BlockPos b, BlockPos hub, boolean fill) {
        setOwner(a, b, hub, fill, null);
    }

    /** Server (RL5): as {@link #setOwner(BlockPos, BlockPos, BlockPos, boolean)}, a fill cell also naming the pair's other hub. */
    public void setOwner(BlockPos a, BlockPos b, BlockPos hub, boolean fill, @Nullable BlockPos hub2) {
        Vec3i na = local(a), nb = local(b), nh = local(hub), n2 = local(hub2);
        if (Objects.equals(na, aLocal) && Objects.equals(nb, bLocal) && Objects.equals(nh, hubLocal)
                && Objects.equals(n2, hub2Local) && fill == this.fill) {
            return;
        }
        aLocal = na;
        bLocal = nb;
        hubLocal = nh;
        hub2Local = n2;
        this.fill = fill;
        changed();
    }

    /** Whether this cell is owned by the strand from {@code a} to {@code b} as a {@code fill} cell of the fan at {@code hub}. */
    public boolean ownedBy(BlockPos a, BlockPos b, BlockPos hub, boolean fill) {
        return a.equals(fittingA()) && b.equals(fittingB()) && hub.equals(hub()) && fill == this.fill;
    }

    /**
     * The climb direction here: unit, from the cell's centre towards the hub's ring (the hoop), in the frame of the
     * cell's level (a ship's plot on a ship); null when the cell's net is gone.
     */
    public @Nullable Vec3 climbDirection() {
        Point hub = hubRing();
        if (hub == null) {
            return null;
        }
        Point d = RatlineNetRules.climbDirection(cell(), hub);
        return new Vec3(d.x(), d.y(), d.z());
    }

    /** Whether this is a top cell: the next cell from its centre towards the hub's ring is the hub's block. */
    public boolean isTop() {
        Point hub = hubRing();
        return hub != null && RatlineNetRules.isTop(cell(), hub);
    }

    private Cell cell() {
        return new Cell(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
    }

    /** Where this cell climbs to: its hub's ring, or (RL5) the nearest point between its two hubs' rings. */
    private @Nullable Point hubRing() {
        if (level == null) {
            return null;
        }
        Strand s = RatlineNets.ownerStrand(level, worldPosition);
        if (s == null) {
            return null;
        }
        BlockPos h2 = hub2();
        Point r2 = fill && h2 != null ? RatlineNets.ring(level, h2) : null;
        return r2 == null ? s.hubRing() : RatlineNetRules.topPoint(cell(), s.hubRing(), r2);
    }

    private void changed() {
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
        put(tag, A, aLocal);
        put(tag, B, bLocal);
        put(tag, HUB, hubLocal);
        put(tag, HUB2, hub2Local);
        tag.putBoolean(FILL, fill);
    }

    private static void put(CompoundTag tag, String key, @Nullable Vec3i v) {
        if (v != null) {
            tag.putIntArray(key, new int[] {v.getX(), v.getY(), v.getZ()});
        }
    }

    private static @Nullable Vec3i get(CompoundTag tag, String key) {
        int[] a = tag.getIntArray(key);
        return a.length == 3 ? new Vec3i(a[0], a[1], a[2]) : null;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        aLocal = get(tag, A);
        bLocal = get(tag, B);
        hubLocal = get(tag, HUB);
        hub2Local = get(tag, HUB2);
        fill = tag.getBoolean(FILL);
    }
}
