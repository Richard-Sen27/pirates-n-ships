package com.richardsenger.piratesnships.combat.boarding;

import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Segment 0 of a boarding plank run (BRD1): remembers the ship the run was laid onto ({@link #farShip()}) and the
 * point of that ship's plot under the run's far end at the moment it was laid ({@link #farPlot()}). Every
 * {@code boarding.plank.check_interval_ticks} it compares the far end's world position with where that point is now
 * and breaks the whole run when they are farther apart than {@code break_distance}, when the far ship is gone, or when
 * the run itself is no longer on a ship ({@link PlankRun#breaks}).
 */
public class BoardingPlankBlockEntity extends BlockEntity {

    private @Nullable UUID farShip;
    private Vec3 farPlot = Vec3.ZERO;
    private BlockPos farBlock = BlockPos.ZERO;
    private int length = 1;
    private PlankRun.Landing landing = PlankRun.Landing.LEVEL;

    public BoardingPlankBlockEntity(BlockPos pos, BlockState state) {
        super(BoardingContent.PLANK_BLOCK_ENTITY.get(), pos, state);
    }

    /** Records what the run was laid onto (called by {@link BoardingPlanks#lay} right after placing it). */
    void link(UUID farShip, Vec3 farPlot, BlockPos farBlock, int length, PlankRun.Landing landing) {
        this.farShip = farShip;
        this.farPlot = farPlot;
        this.farBlock = farBlock.immutable();
        this.length = length;
        this.landing = landing;
        setChanged();
    }

    public @Nullable UUID farShip() {
        return farShip;
    }

    /** The far end's point in the far ship's plot, as laid. */
    public Vec3 farPlot() {
        return farPlot;
    }

    /** The far ship's block the run landed on (plot position of that ship). */
    public BlockPos farBlock() {
        return farBlock;
    }

    public int length() {
        return length;
    }

    public PlankRun.Landing landing() {
        return landing;
    }

    public Direction facing() {
        return getBlockState().getValue(BoardingPlankBlock.FACING);
    }

    /** Plot position of the run's far cell (in the plot of the ship it lies in). */
    public BlockPos tipPos() {
        return getBlockPos().relative(facing(), length - 1);
    }

    /**
     * Distance in blocks between the far end now and the far ship's spot it was laid onto, or {@code NaN} when either
     * ship is gone.
     */
    public double farEndDrift(ServerLevel level) {
        ShipBody near = SableShips.containing(this);
        ShipBody far = farShip == null ? null : SableShips.byId(level, farShip);
        if (near == null || near.isRemoved() || far == null || far.isRemoved()) {
            return Double.NaN;
        }
        return near.toWorld(Vec3.atCenterOf(tipPos())).distanceTo(far.toWorld(farPlot));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoardingPlankBlockEntity be) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        int interval = Math.max(1, BoardingConfig.CHECK_INTERVAL_TICKS.get());
        if (Math.floorMod(server.getGameTime() + pos.hashCode(), interval) != 0) {
            return;
        }
        double drift = be.farEndDrift(server);
        if (PlankRun.breaks(!Double.isNaN(drift), drift, BoardingConfig.BREAK_DISTANCE.get())) {
            // segment 0 drops the one plank and plays the wood break; the rest of the run follows through updateShape
            server.destroyBlock(pos, true);
        }
    }

    // ------------------------------------------------------------------ save

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (farShip != null) {
            tag.putUUID("far_ship", farShip);
        }
        tag.putDouble("far_x", farPlot.x);
        tag.putDouble("far_y", farPlot.y);
        tag.putDouble("far_z", farPlot.z);
        tag.put("far_block", NbtUtils.writeBlockPos(farBlock));
        tag.putInt("length", length);
        tag.putString("landing", landing.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        farShip = tag.hasUUID("far_ship") ? tag.getUUID("far_ship") : null;
        farPlot = new Vec3(tag.getDouble("far_x"), tag.getDouble("far_y"), tag.getDouble("far_z"));
        farBlock = NbtUtils.readBlockPos(tag, "far_block").orElse(BlockPos.ZERO);
        length = Math.max(1, Math.min(PlankRun.MAX_SEGMENTS, tag.getInt("length")));
        try {
            landing = PlankRun.Landing.valueOf(tag.getString("landing"));
        } catch (IllegalArgumentException e) {
            landing = PlankRun.Landing.LEVEL;
        }
    }
}
