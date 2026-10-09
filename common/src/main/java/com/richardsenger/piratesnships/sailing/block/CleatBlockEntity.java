package com.richardsenger.piratesnships.sailing.block;

import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.rope.RopeAnchorBlockEntity;
import com.richardsenger.piratesnships.sailing.sail.SailTint;
import com.richardsenger.piratesnships.sailing.sail.TriangleCloth;
import com.richardsenger.piratesnships.sailing.sail.TriangularSailContent;
import com.richardsenger.piratesnships.sailing.sail.TriangularSails;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The block entity of every cleat. As every rope anchor ({@link RopeAnchorBlockEntity}, RP1) it holds this cleat's ends
 * of its ropes, stored in the cleat's own frame so that they survive the ship's assembly and a rotated disassembly. A
 * rope to another cleat that passes the stay rule with a clew is the stay of a sail (F5b); any other rope is a
 * decorative line.
 *
 * <p>On a sail's head the server also stores the {@link TriangleCloth} it computed, saved and synced to clients, so
 * that the renderer draws the cloth without knowing the rule (the cloth's tack offset also tells it which rope is the
 * stay). The trim is the head's {@link CleatBlock#TRIM} block state. The server re-checks the cloth every
 * {@code sailing.sails.yard_refresh_ticks} (a block put into the gap below the head triggers no cleat update on land),
 * and once on its first server tick, so a cloth loaded from saved data that no longer fits (a ship disassembled with a
 * turn moves the block entities with their old cloth, stored in world axes) is corrected at once.
 *
 * <p><b>Dye (SAIL2).</b> The head cleat keeps the sail's dye ({@link #dye()}), saved and synced with the update tag,
 * together with the tint the client draws ({@link SailTint#clothTint}, decided by the server with
 * {@code sails.dyeing}). Stay sails take dye only, no banner.
 */
public class CleatBlockEntity extends RopeAnchorBlockEntity {

    private static final String CLOTH = "cloth";
    private static final String DYE = "dye";
    private static final String TINT = "tint";

    private @Nullable TriangleCloth cloth;
    private @Nullable DyeColor dye;
    /** The tint the server last sent (read on the client). */
    private int syncedTint = SailTint.NONE;
    /** Server: whether the first tick's re-check ran (not saved: every new or loaded block entity checks once). */
    private boolean checked;

    // client only, for the renderer
    /** Drawn fraction shown last frame (NaN before the first frame). */
    public float shownFraction = Float.NaN;
    /** Game time plus partial tick of the last frame. */
    public double shownTime = Double.NaN;
    /** Side of the cloth's plane it bellies out to: +1 or -1 along the triangle's normal. */
    public int side = 1;

    public CleatBlockEntity(BlockPos pos, BlockState state) {
        super(TriangularSailContent.CLEAT_BLOCK_ENTITY.get(), pos, state);
    }

    /** The cloth this cleat heads, or null. */
    public @Nullable TriangleCloth cloth() {
        return cloth;
    }

    /** The other end of the stay of the sail this cleat heads (from the cloth's tack), or null. */
    public @Nullable BlockPos clothTack() {
        return cloth == null ? null : worldPosition.offset(cloth.tackX(), cloth.tackY(), cloth.tackZ());
    }

    /** Server: stores the cloth this cleat heads (null: none) and syncs it when it changed. */
    public void setCloth(@Nullable TriangleCloth c) {
        if (Objects.equals(c, cloth)) {
            return;
        }
        cloth = c;
        changed();
    }

    /** The sail's dye, or null (never dyed). */
    public @Nullable DyeColor dye() {
        return dye;
    }

    /** Server: dyes the sail this cleat heads (null: undyed) and syncs it when it changed. */
    public void setDye(@Nullable DyeColor d) {
        if (d == dye) {
            return;
        }
        dye = d;
        changed();
    }

    /** The cloth's tint: on the server from the dye and the config, on a client as the server sent it. */
    public int clothTint() {
        return level != null && !level.isClientSide
                ? SailTint.clothTint(dye, ItemStack.EMPTY, SailingConfig.SAIL_DYEING.get(), false) : syncedTint;
    }

    @Override
    public void clearRopes() {
        super.clearRopes();
        setCloth(null);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CleatBlockEntity be) {
        if (!be.hasRopes() && be.cloth == null) {
            return;
        }
        int interval = SailingConfig.YARD_REFRESH_TICKS.get();
        if (be.checked && (level.getGameTime() + Math.floorMod(pos.asLong(), interval)) % interval != 0) {
            return;
        }
        be.checked = true;
        TriangularSails.refresh(level, pos);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (cloth != null) {
            tag.putIntArray(CLOTH, new int[] {cloth.tackX(), cloth.tackY(), cloth.tackZ(), cloth.drop()});
        }
        if (dye != null) {
            tag.putString(DYE, dye.getSerializedName());
        }
        if (level != null && !level.isClientSide) {
            tag.putInt(TINT, clothTint()); // what the client draws (SAIL2)
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int[] c = tag.getIntArray(CLOTH);
        cloth = c.length == 4 ? new TriangleCloth(c[0], c[1], c[2], c[3]) : null;
        dye = tag.contains(DYE, CompoundTag.TAG_STRING) ? DyeColor.byName(tag.getString(DYE), null) : null;
        syncedTint = tag.contains(TINT, CompoundTag.TAG_INT) ? tag.getInt(TINT) : SailTint.NONE;
    }
}
