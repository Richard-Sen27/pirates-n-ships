package com.richardsenger.piratesnships.sailing.block;

import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.sail.ClothGeometry;
import com.richardsenger.piratesnships.sailing.sail.ClothTears;
import com.richardsenger.piratesnships.sailing.sail.SailMending;
import com.richardsenger.piratesnships.sailing.sail.YardSails;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The block entity of every yard block. On a sail's head (the upper yard's middle block) it stores the
 * {@link ClothGeometry} the server computed, saved and synced to clients ({@link #getUpdateTag}), so that the renderer
 * draws the cloth without knowing the rule; on every other yard block it is empty. The trim is not stored here: it is
 * the head's {@link YardBlock#TRIM} block state, which vanilla syncs anyway.
 *
 * <p>The server keeps it current from block changes ({@link YardSails#refreshAround}) and re-checks it every
 * {@code sailing.sails.yard_refresh_ticks} (a block placed into the gap between two yards on land triggers no yard
 * update). It also re-checks on its first server tick, so a cloth loaded from saved data that no longer fits (a ship
 * disassembled with a turn moves the block entities with their old cloth) is corrected at once rather than up to an
 * interval later. The client-only fields animate hoisting and remember the side the cloth bellies out to.
 *
 * <p>CAN3: the head also keeps the holes chain shot tore into its cloth ({@link ClothTears}), saved and synced with the
 * cloth; the renderer leaves them out and the sailing runtime scales the sail's area by the whole share. One torn cell
 * mends every {@link SailMending#interval()} ticks.
 */
public class YardBlockEntity extends BlockEntity {

    private static final String TAG = "cloth";

    private static final String TAG_TEARS = "tears";

    private @Nullable ClothGeometry geometry;
    /** CAN3: the holes in the cloth this block heads. */
    private ClothTears tears = ClothTears.NONE;
    /** Server: game time of the next mended cell (0 = not counting). Not saved: a reload restarts the count. */
    private long nextMend;
    /** Server: whether the first tick's re-check ran (not saved: every new or loaded block entity checks once). */
    private boolean checked;

    // client only, for the renderer
    /** Drawn fraction shown last frame (NaN before the first frame). */
    public float shownFraction = Float.NaN;
    /** Game time plus partial tick of the last frame. */
    public double shownTime = Double.NaN;
    /** Side of the yard the cloth bellies out to: +1 or -1 along the horizontal axis across the yard. */
    public int side = 1;

    public YardBlockEntity(BlockPos pos, BlockState state) {
        super(SailingBlocks.YARD_BLOCK_ENTITY.get(), pos, state);
    }

    /** The cloth this block heads, or null. */
    public @Nullable ClothGeometry geometry() {
        return geometry;
    }

    /** Server: stores the cloth this block heads (null: none) and syncs it when it changed. */
    public void setGeometry(@Nullable ClothGeometry g) {
        if (Objects.equals(g, geometry)) {
            return;
        }
        geometry = g;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** The holes in the cloth this block heads (CAN3); none on a block that heads no sail. */
    public ClothTears tears() {
        return tears;
    }

    /**
     * Server: replaces the holes in the cloth and syncs them; the ship's sailing runtime (if any) relinks, so the sail
     * draws with its whole share at once.
     */
    public void setTears(ClothTears t) {
        if (t.equals(tears)) {
            return;
        }
        tears = t;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            if (level instanceof ServerLevel server) {
                SailingRuntimes.onRigChanged(server, worldPosition);
            }
        }
    }

    /** Server: mends one torn cell every {@link SailMending#interval()} ticks (0 = never). */
    private void mend(Level level) {
        int every = SailMending.interval();
        if (tears.isEmpty() || every <= 0) {
            nextMend = 0;
            return;
        }
        long now = level.getGameTime();
        if (nextMend == 0) {
            nextMend = now + every;
        } else if (now >= nextMend) {
            nextMend = now + every;
            setTears(tears.mendOne());
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, YardBlockEntity be) {
        be.mend(level);
        int interval = SailingConfig.YARD_REFRESH_TICKS.get();
        if (be.checked && (level.getGameTime() + Math.floorMod(pos.asLong(), interval)) % interval != 0) {
            return;
        }
        be.checked = true;
        be.setGeometry(YardSails.geometryAt(level, pos, SailingConfig.yardRules()));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // always non-empty: the client ignores an empty update tag, and must still learn that the cloth is gone
        tag.putBoolean("heads_sail", geometry != null);
        if (geometry != null) {
            CompoundTag c = new CompoundTag();
            c.putBoolean("along_x", geometry.alongX());
            c.putFloat("upper_neg", geometry.upperNeg());
            c.putFloat("upper_pos", geometry.upperPos());
            c.putFloat("lower_neg", geometry.lowerNeg());
            c.putFloat("lower_pos", geometry.lowerPos());
            c.putInt("drop", geometry.drop());
            tag.put(TAG, c);
        }
        tag.putIntArray(TAG_TEARS, tears.packed());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG, CompoundTag.TAG_COMPOUND)) {
            CompoundTag c = tag.getCompound(TAG);
            geometry = new ClothGeometry(c.getBoolean("along_x"), c.getFloat("upper_neg"), c.getFloat("upper_pos"),
                    c.getFloat("lower_neg"), c.getFloat("lower_pos"), c.getInt("drop"));
        } else {
            geometry = null;
        }
        tears = tag.contains(TAG_TEARS) ? ClothTears.of(tag.getIntArray(TAG_TEARS)) : ClothTears.NONE;
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
