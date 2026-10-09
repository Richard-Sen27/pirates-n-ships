package com.richardsenger.piratesnships.sailing.block;

import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.sail.ClothGeometry;
import com.richardsenger.piratesnships.sailing.sail.SailBanner;
import com.richardsenger.piratesnships.sailing.sail.SailTint;
import com.richardsenger.piratesnships.sailing.sail.YardSails;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
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
 * <p><b>Dye and banner (SAIL2).</b> A yard's dye ({@link #dye()}) is kept on every block of the yard, so it survives a
 * yard that is lengthened or shortened ({@link YardSails#refreshRow} hands it on); the banner ({@link #banner()}) is
 * kept on the head only and moved to a new middle block when the yard changes. Both are saved, travel with the block
 * entity through assembly, disassembly and a ship template, and are synced. The update tag also carries what the
 * client draws, decided by the server: the cloth's tint ({@link SailTint#clothTint}) and whether the banner is shown
 * ({@link SailBanner#shown}, from the head's cloth and the {@code sails.*} config); a client reads those, never the
 * server config. As a {@link Clearable}, the old block entity of a move (Sable's {@code moveBlocks} clears it after
 * saving) forgets its banner, so breaking the old position never drops a copy.
 */
public class YardBlockEntity extends BlockEntity implements Clearable {

    private static final String TAG = "cloth";
    private static final String DYE = "dye";
    private static final String BANNER = "banner";
    private static final String TINT = "tint";
    private static final String BANNER_SHOWN = "banner_shown";

    private @Nullable ClothGeometry geometry;
    private @Nullable DyeColor dye;
    private ItemStack banner = ItemStack.EMPTY;
    /** What the server last sent for drawing (read on the client; see the class comment). */
    private int syncedTint = SailTint.NONE;
    private boolean syncedBannerShown;
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

    /** The yard's dye, or null (never dyed). */
    public @Nullable DyeColor dye() {
        return dye;
    }

    /** Server: dyes this yard block (null: undyed) and syncs it when it changed. */
    public void setDye(@Nullable DyeColor d) {
        if (d == dye) {
            return;
        }
        dye = d;
        changed();
    }

    /** The banner hung on the sail this block heads, or empty (a copy is never handed out: do not change it). */
    public ItemStack banner() {
        return banner;
    }

    /** Server: hangs {@code b} (one item; empty: none) and syncs it. */
    public void setBanner(ItemStack b) {
        if (ItemStack.matches(b, banner)) {
            return;
        }
        banner = b.isEmpty() ? ItemStack.EMPTY : b.copyWithCount(1);
        changed();
    }

    /** Server: takes the banner off and returns it (empty if there was none). */
    public ItemStack takeBanner() {
        ItemStack b = banner;
        if (!b.isEmpty()) {
            banner = ItemStack.EMPTY;
            changed();
        }
        return b;
    }

    /** The cloth's tint: on the server from the dye, the banner and the config, on a client as the server sent it. */
    public int clothTint() {
        return onServer() ? SailTint.clothTint(dye, banner, SailingConfig.SAIL_DYEING.get(), bannerShown()) : syncedTint;
    }

    /** Whether the banner's design is drawn: on the server by {@link SailBanner#shown}, on a client as the server sent it. */
    public boolean bannerShown() {
        return onServer() ? SailBanner.shown(banner, geometry, SailingConfig.SAIL_BANNERS.get(),
                SailingConfig.SAIL_BANNER_MIN_WIDTH.get(), SailingConfig.SAIL_BANNER_MIN_DROP.get()) : syncedBannerShown;
    }

    /** The pattern layers to draw over the cloth, bottom to top: the banner's while it is shown, else none. */
    public List<BannerPatternLayers.Layer> shownLayers() {
        return bannerShown() ? SailBanner.layers(banner) : List.of();
    }

    private boolean onServer() {
        return level != null && !level.isClientSide;
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** A move's old block entity (or a /clone, a structure placed over it) forgets its banner, so it is never dropped twice. */
    @Override
    public void clearContent() {
        banner = ItemStack.EMPTY;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, YardBlockEntity be) {
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
        if (dye != null) {
            tag.putString(DYE, dye.getSerializedName());
        }
        if (!banner.isEmpty()) {
            tag.put(BANNER, banner.save(registries));
        }
        if (onServer()) { // what the client draws (SAIL2)
            tag.putInt(TINT, clothTint());
            tag.putBoolean(BANNER_SHOWN, bannerShown());
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
        dye = tag.contains(DYE, CompoundTag.TAG_STRING) ? DyeColor.byName(tag.getString(DYE), null) : null;
        banner = tag.contains(BANNER, CompoundTag.TAG_COMPOUND) ? ItemStack.parseOptional(registries, tag.getCompound(BANNER)) : ItemStack.EMPTY;
        syncedTint = tag.contains(TINT, CompoundTag.TAG_INT) ? tag.getInt(TINT) : SailTint.NONE;
        syncedBannerShown = tag.getBoolean(BANNER_SHOWN);
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
