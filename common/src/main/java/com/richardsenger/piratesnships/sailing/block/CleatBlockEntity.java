package com.richardsenger.piratesnships.sailing.block;

import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.sail.CleatFrame;
import com.richardsenger.piratesnships.sailing.sail.TriangleCloth;
import com.richardsenger.piratesnships.sailing.sail.TriangularSailContent;
import com.richardsenger.piratesnships.sailing.sail.TriangularSails;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
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
 * The block entity of every cleat. It holds this cleat's end of a stay: the offset to the other cleat, stored in the
 * cleat's own frame ({@link CleatFrame}) so that it survives the ship's assembly (a translation) and a rotated
 * disassembly (the facing turns, and the offset with it). Both ends of a stay store it; a stay counts only while the
 * two agree ({@link TriangularSails#partner}).
 *
 * <p>On a sail's head the server also stores the {@link TriangleCloth} it computed, saved and synced to clients, so
 * that the renderer draws the cloth without knowing the rule. The stay is synced too: the head draws the rope. The
 * trim is the head's {@link CleatBlock#TRIM} block state. The server re-checks the cloth every
 * {@code sailing.sails.yard_refresh_ticks} (a block put into the gap below the head triggers no cleat update on land),
 * and once on its first server tick, so a cloth loaded from saved data that no longer fits (a ship disassembled with a
 * turn moves the block entities with their old cloth, stored in world axes) is corrected at once.
 */
public class CleatBlockEntity extends BlockEntity {

    private static final String STAY = "stay";
    private static final String CLOTH = "cloth";

    /** Offset to the other end of the stay in this cleat's frame, or null. */
    private @Nullable Vec3i stayLocal;
    private @Nullable TriangleCloth cloth;
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

    private int quarterTurns() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof CleatBlock ? s.getValue(CleatBlock.FACING).get2DDataValue() : 0;
    }

    /** The other end of this cleat's stay as stored here (not checked against the other cleat), or null. */
    public @Nullable BlockPos stayTarget() {
        if (stayLocal == null) {
            return null;
        }
        int[] w = CleatFrame.toWorld(quarterTurns(), stayLocal.getX(), stayLocal.getY(), stayLocal.getZ());
        return worldPosition.offset(w[0], w[1], w[2]);
    }

    /** Server: stores the other end of the stay (null: none), and syncs it when it changed. */
    public void setStay(@Nullable BlockPos target) {
        Vec3i local = null;
        if (target != null) {
            int[] l = CleatFrame.toLocal(quarterTurns(), target.getX() - worldPosition.getX(),
                    target.getY() - worldPosition.getY(), target.getZ() - worldPosition.getZ());
            local = new Vec3i(l[0], l[1], l[2]);
        }
        if (Objects.equals(local, stayLocal)) {
            return;
        }
        stayLocal = local;
        if (local == null) {
            cloth = null;
        }
        changed();
    }

    /** The cloth this cleat heads, or null. */
    public @Nullable TriangleCloth cloth() {
        return cloth;
    }

    /** Server: stores the cloth this cleat heads (null: none) and syncs it when it changed. */
    public void setCloth(@Nullable TriangleCloth c) {
        if (Objects.equals(c, cloth)) {
            return;
        }
        cloth = c;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CleatBlockEntity be) {
        if (be.stayLocal == null && be.cloth == null) {
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
        // always non-empty: the client ignores an empty update tag, and must still learn that stay and cloth are gone
        tag.putBoolean("has_stay", stayLocal != null);
        if (stayLocal != null) {
            tag.putIntArray(STAY, new int[] {stayLocal.getX(), stayLocal.getY(), stayLocal.getZ()});
        }
        if (cloth != null) {
            tag.putIntArray(CLOTH, new int[] {cloth.tackX(), cloth.tackY(), cloth.tackZ(), cloth.drop()});
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int[] s = tag.getIntArray(STAY);
        stayLocal = s.length == 3 ? new Vec3i(s[0], s[1], s[2]) : null;
        int[] c = tag.getIntArray(CLOTH);
        cloth = c.length == 4 ? new TriangleCloth(c[0], c[1], c[2], c[3]) : null;
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
