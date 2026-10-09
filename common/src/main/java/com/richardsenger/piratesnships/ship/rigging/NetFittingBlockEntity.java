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

import java.util.ArrayList;
import java.util.List;

/**
 * The net end of a ratline fitting (RL2, a {@link ChainplateBlock} or a {@link MastHoopBlock}): the nets it holds, each
 * as a link to the net's other fitting and whether this end is the net's fitting A (the end it was strung from). A
 * chainplate holds one net; a mast hoop up to {@code rigging.net_max_strands} (RL4), the strands of its fan. At a hub
 * the server keeps the links in fan order ({@link RatlineNetRules#fanOrder}) and marks for each strand whether it forms
 * one net with the next ({@link Link#joinNext}), so the client draws the fan without the server's config.
 *
 * <p>The offsets to the other fittings are stored in this fitting's own frame ({@link CleatFrame}, by its horizontal
 * facing), so the nets survive the ship's assembly (a translation) and a rotated disassembly (the facing turns, and the
 * offsets with it), although block entity data itself is never rotated; the fan order is a bearing order and does not
 * change when everything turns. Synced to clients.
 */
public class NetFittingBlockEntity extends BlockEntity {

    /** RL2's single link (read for old saves only). */
    private static final String OTHER = "net_other";
    private static final String IS_A = "net_a";
    /** RL4: every link as x, y, z, flags (bit 0 this end is A, bit 1 joins the next strand). */
    private static final String LINKS = "nets";

    /** A net held here: the other fitting (world or plot position), whether this end is A, whether it joins the next strand. */
    public record Link(BlockPos other, boolean isA, boolean joinNext) {
    }

    private record Entry(Vec3i otherLocal, boolean isA, boolean joinNext) {
    }

    private final List<Entry> links = new ArrayList<>();

    public NetFittingBlockEntity(BlockPos pos, BlockState state) {
        super(RiggingContent.NET_FITTING_BLOCK_ENTITY.get(), pos, state);
    }

    private int quarterTurns() {
        BlockState s = getBlockState();
        return s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? s.getValue(BlockStateProperties.HORIZONTAL_FACING).get2DDataValue() : 0;
    }

    private BlockPos world(Vec3i local) {
        int[] w = CleatFrame.toWorld(quarterTurns(), local.getX(), local.getY(), local.getZ());
        return worldPosition.offset(w[0], w[1], w[2]);
    }

    private Vec3i local(BlockPos other) {
        int[] l = CleatFrame.toLocal(quarterTurns(), other.getX() - worldPosition.getX(),
                other.getY() - worldPosition.getY(), other.getZ() - worldPosition.getZ());
        return new Vec3i(l[0], l[1], l[2]);
    }

    /** Whether this fitting holds a net. */
    public boolean hasNet() {
        return !links.isEmpty();
    }

    /** How many nets this fitting holds. */
    public int netCount() {
        return links.size();
    }

    /** Whether this fitting is the A end of its first net. */
    public boolean isA() {
        return !links.isEmpty() && links.get(0).isA();
    }

    /** The other fitting of the first net held here, or null. */
    public @Nullable BlockPos partner() {
        return links.isEmpty() ? null : world(links.get(0).otherLocal());
    }

    /** The first net's A end: this fitting when it is A, else its partner; null without a net. */
    public @Nullable BlockPos netA() {
        return isA() ? worldPosition : partner();
    }

    /** Every net held here, in the stored order (fan order at a hub). */
    public List<Link> links() {
        List<Link> out = new ArrayList<>(links.size());
        for (Entry e : links) {
            out.add(new Link(world(e.otherLocal()), e.isA(), e.joinNext()));
        }
        return out;
    }

    /** Whether this fitting holds a net to {@code other}. */
    public boolean holds(BlockPos other) {
        return indexOf(other) >= 0;
    }

    /** Whether the strand to {@code other} forms one net with the next strand of this hub's fan. */
    public boolean joinsNext(BlockPos other) {
        int i = indexOf(other);
        return i >= 0 && links.get(i).joinNext();
    }

    private int indexOf(BlockPos other) {
        Vec3i l = local(other);
        for (int i = 0; i < links.size(); i++) {
            if (links.get(i).otherLocal().equals(l)) {
                return i;
            }
        }
        return -1;
    }

    /** Server: this fitting now holds a net to {@code other} (added, or replacing one to the same fitting); {@code a} when this end is A. */
    public void setNet(BlockPos other, boolean a) {
        Entry e = new Entry(local(other), a, false);
        int i = indexOf(other);
        if (i >= 0) {
            links.set(i, e);
        } else {
            links.add(e);
        }
        changed();
    }

    /** Server: forgets every net. */
    public void clearNet() {
        if (!links.isEmpty()) {
            links.clear();
            changed();
        }
    }

    /** Server: forgets the net to {@code other}. */
    public void clearNet(BlockPos other) {
        int i = indexOf(other);
        if (i >= 0) {
            links.remove(i);
            changed();
        }
    }

    /**
     * Server: the fan at this hub: the links to {@code order} first, in that order, with {@code joinNext[k]} for the
     * k-th; every other link after them, joining nothing.
     */
    public void setFan(List<BlockPos> order, boolean[] joinNext) {
        List<Entry> out = new ArrayList<>(links.size());
        List<Entry> rest = new ArrayList<>(links);
        for (int k = 0; k < order.size(); k++) {
            int i = indexOf(order.get(k));
            if (i < 0) {
                continue;
            }
            Entry e = links.get(i);
            rest.remove(e);
            out.add(new Entry(e.otherLocal(), e.isA(), k < joinNext.length && joinNext[k]));
        }
        for (Entry e : rest) {
            out.add(new Entry(e.otherLocal(), e.isA(), false));
        }
        if (!out.equals(links)) {
            links.clear();
            links.addAll(out);
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
        // always written, also empty: the client ignores an empty update tag and must still learn that the nets are gone
        int[] flat = new int[links.size() * 4];
        for (int i = 0; i < links.size(); i++) {
            Entry e = links.get(i);
            flat[i * 4] = e.otherLocal().getX();
            flat[i * 4 + 1] = e.otherLocal().getY();
            flat[i * 4 + 2] = e.otherLocal().getZ();
            flat[i * 4 + 3] = (e.isA() ? 1 : 0) | (e.joinNext() ? 2 : 0);
        }
        tag.putIntArray(LINKS, flat);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        links.clear();
        if (tag.contains(LINKS)) {
            int[] f = tag.getIntArray(LINKS);
            for (int i = 0; i + 3 < f.length; i += 4) {
                links.add(new Entry(new Vec3i(f[i], f[i + 1], f[i + 2]), (f[i + 3] & 1) != 0, (f[i + 3] & 2) != 0));
            }
        } else {
            int[] o = tag.getIntArray(OTHER);
            if (o.length == 3) {
                links.add(new Entry(new Vec3i(o[0], o[1], o[2]), tag.getBoolean(IS_A), false));
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
