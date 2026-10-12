package com.richardsenger.piratesnships.sailing.block;

import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.sail.BoomRules;
import com.richardsenger.piratesnships.sailing.sail.TriangleCloth;
import java.util.Arrays;
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
 * The block entity of every boom (SAIL5). The server stores the boom's <b>arm</b>, the horizontal offset from the
 * boom to its sail's tack ({@code {x, z}}, world or plot axes like the head cleat's cloth), saved and synced, so the
 * renderer draws the arm out to the sail's foot and the hand knows the boom's line; null when the boom carries no sail
 * (the renderer then draws a short arm along {@link BoomBlock#FACING}). The head cleat writes it whenever it works out
 * its cloth ({@code TriangularSails.refresh}); the boom re-checks it every {@code sailing.sails.yard_refresh_ticks} and
 * once on its first server tick (a rotated disassembly moves block entities with their old, world-axis data) by
 * looking up its column for the head whose boomed cloth reaches down to it.
 *
 * <p>Server only, not saved: until when a hand holds the boom against the crew's auto-trim. Client only: the sheet
 * angle drawn last frame, eased toward the block state's ({@link #shownSheet}), read by both the arm and the cloth
 * renderer so they agree.
 */
public class BoomBlockEntity extends BlockEntity {

    private static final String ARM = "arm";
    /** How fast the drawn boom swings toward its sheet [degrees per second]. */
    public static final float SWING_DEGREES_PER_SECOND = 90f;

    private int @Nullable [] arm;
    private boolean checked;
    private long handHeldUntil = Long.MIN_VALUE;

    // client only
    private float shownSheet = Float.NaN;
    private double shownTime = Double.NaN;

    public BoomBlockEntity(BlockPos pos, BlockState state) {
        super(SailingBlocks.BOOM_BLOCK_ENTITY.get(), pos, state);
    }

    /** The offset to the sail's tack {@code {x, z}}, or null without a sail (copy). */
    public int @Nullable [] arm() {
        return arm == null ? null : arm.clone();
    }

    /** Server: stores the arm (null: no sail) and syncs it when it changed. */
    public void setArm(int @Nullable [] a) {
        if (Arrays.equals(a, arm)) {
            return;
        }
        arm = a == null ? null : a.clone();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Server: a hand sheeted the boom; the crew leaves it until {@code gameTime}. */
    public void holdByHand(long gameTime) {
        handHeldUntil = gameTime;
    }

    /** Server: whether a hand holds the boom at {@code gameTime}. */
    public boolean heldByHand(long gameTime) {
        return gameTime < handHeldUntil;
    }

    /**
     * Client: the sheet angle to draw at {@code now} (game time plus partial tick) [degrees], eased from the last frame's
     * toward the block state's at {@link #SWING_DEGREES_PER_SECOND}; the first frame jumps. The same {@code now} gives
     * the same angle, so the arm and the cloth renderer may both ask.
     */
    public float shownSheet(double now) {
        float target = (float) BoomBlock.sheetDegrees(getBlockState());
        if (Float.isNaN(shownSheet) || Double.isNaN(shownTime)) {
            shownSheet = target;
        } else if (now != shownTime) {
            float step = (float) (Math.min(Math.max(now - shownTime, 0.0), 40.0) / 20.0) * SWING_DEGREES_PER_SECOND;
            float d = target - shownSheet;
            shownSheet = Math.abs(d) <= step ? target : shownSheet + Math.signum(d) * step;
        }
        shownTime = now;
        return shownSheet;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoomBlockEntity be) {
        int interval = SailingConfig.YARD_REFRESH_TICKS.get();
        if (be.checked && (level.getGameTime() + Math.floorMod(pos.asLong(), interval)) % interval != 0) {
            return;
        }
        be.checked = true;
        be.setArm(findArm(level, pos));
    }

    /**
     * The arm of the boom at {@code pos}: up its column through air, mast and yard blocks to the first other block; a
     * cleat there whose cloth is boomed and reaches exactly down here gives its tack's horizontal offset.
     */
    static int @Nullable [] findArm(Level level, BlockPos pos) {
        int reach = SailingConfig.STAY_MAX_LENGTH.get();
        for (int dy = 1; dy <= reach; dy++) {
            BlockPos p = pos.above(dy);
            BlockState s = level.getBlockState(p);
            if (level.getBlockEntity(p) instanceof CleatBlockEntity head) {
                TriangleCloth c = head.cloth();
                return c != null && c.boomed() && c.drop() == dy ? new int[] {c.tackX(), c.tackZ()} : null;
            }
            if (!com.richardsenger.piratesnships.sailing.sail.TriangularSails.cell(s).isOpen()) {
                return null;
            }
        }
        return null;
    }

    /** The arm's length [blocks], or 0 without a sail. */
    public double armLength() {
        return arm == null ? 0.0 : Math.hypot(arm[0], arm[1]);
    }

    /** The boom's end relative to its pivot {@code {x, z}} at {@code sheetDegrees}, or null without a sail. */
    public double @Nullable [] end(double sheetDegrees) {
        return arm == null ? null : BoomRules.turn(arm[0], arm[1], sheetDegrees);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (arm != null) {
            tag.putIntArray(ARM, arm);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int[] a = tag.getIntArray(ARM);
        arm = a.length == 2 ? a : null;
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
