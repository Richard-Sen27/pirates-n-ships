package com.richardsenger.piratesnships.sailing.block;

import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.sail.ClothGeometry;
import com.richardsenger.piratesnships.sailing.sail.YardSails;
import java.util.Objects;
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
 * The block entity of every yard block. On a sail's head (the upper yard's middle block) it stores the
 * {@link ClothGeometry} the server computed, saved and synced to clients ({@link #getUpdateTag}), so that the renderer
 * draws the cloth without knowing the rule; on every other yard block it is empty. The trim is not stored here: it is
 * the head's {@link YardBlock#TRIM} block state, which vanilla syncs anyway.
 *
 * <p>The server keeps it current from block changes ({@link YardSails#refreshAround}) and re-checks it every
 * {@code sailing.sails.yard_refresh_ticks} (a block placed into the gap between two yards on land triggers no yard
 * update). The client-only fields animate hoisting and remember the side the cloth bellies out to.
 */
public class YardBlockEntity extends BlockEntity {

    private static final String TAG = "cloth";

    private @Nullable ClothGeometry geometry;

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

    public static void serverTick(Level level, BlockPos pos, BlockState state, YardBlockEntity be) {
        int interval = SailingConfig.YARD_REFRESH_TICKS.get();
        if ((level.getGameTime() + Math.floorMod(pos.asLong(), interval)) % interval != 0) {
            return;
        }
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
