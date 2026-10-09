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
 * Segment 0 of a boarding plank run (BRD1, BRD2): remembers what both ends rest on. The near end (segment 0's end)
 * rests on {@link #baseSupport()}, a block in the run's own frame (the ship whose plot holds the run, or the world).
 * The far end rests on {@link #farBlock()} of the far body: a ship ({@link #farShip()}) or the world (null), and was laid
 * at {@link #farPoint()} in that body's frame.
 *
 * <p>Every {@code boarding.plank.check_interval_ticks} it breaks the whole run ({@link PlankRun#breaks}) when the run
 * left the ship it was laid in, a ship at either end is gone, a block either end rests on is gone, the run lost a
 * segment, or the far end drifted from where it was laid more than {@code break_distance} sideways or more than
 * {@code break_rise} up or down (a moored ship's heave in a storm does not snap a gangway at a pier). A run between two
 * world points does not move, so only its supports and segments are watched.
 */
public class BoardingPlankBlockEntity extends BlockEntity {

    private boolean onShip;
    private BlockPos baseSupport = BlockPos.ZERO;
    private @Nullable UUID farShip;
    private Vec3 farPoint = Vec3.ZERO;
    private BlockPos farBlock = BlockPos.ZERO;
    private BlockPos tip = BlockPos.ZERO;
    private int length = 1;
    private PlankRun.Landing landing = PlankRun.Landing.LEVEL;
    /** Loaded from a BRD1 save: the near support is not known, so it is not watched. */
    private boolean legacy;

    public BoardingPlankBlockEntity(BlockPos pos, BlockState state) {
        super(BoardingContent.PLANK_BLOCK_ENTITY.get(), pos, state);
    }

    /** Records what the run rests on (called by {@link BoardingPlanks#lay} right after placing it). */
    void link(boolean onShip, BlockPos baseSupport, @Nullable UUID farShip, Vec3 farPoint, BlockPos farBlock, BlockPos tip,
              int length, PlankRun.Landing landing) {
        this.onShip = onShip;
        this.baseSupport = baseSupport.immutable();
        this.farShip = farShip;
        this.farPoint = farPoint;
        this.farBlock = farBlock.immutable();
        this.tip = tip.immutable();
        this.length = length;
        this.landing = landing;
        this.legacy = false;
        setChanged();
    }

    /** The run lies in a ship's plot. */
    public boolean onShip() {
        return onShip;
    }

    /** The block segment 0's end rests on, in the run's own frame. */
    public BlockPos baseSupport() {
        return baseSupport;
    }

    /** The ship the far end rests on, or null for the world. */
    public @Nullable UUID farShip() {
        return farShip;
    }

    /** The far end's spot in the far body's frame (plot of the far ship, or the world), as laid. */
    public Vec3 farPoint() {
        return farPoint;
    }

    /** The block the far end rests on, in the far body's frame. */
    public BlockPos farBlock() {
        return farBlock;
    }

    /** The far cell, in the run's own frame. */
    public BlockPos tipPos() {
        return tip;
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

    /** What the check sees: whether the ends are there and supported, and the far end's drift. */
    public record Check(boolean endsPresent, boolean supported, double horizontal, double vertical) {
        public boolean breaks() {
            return PlankRun.breaks(endsPresent, supported, horizontal, vertical, BoardingConfig.BREAK_DISTANCE.get(),
                    BoardingConfig.BREAK_RISE.get());
        }
    }

    private static boolean holds(Level level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** Looks at both ends now. */
    public Check check(ServerLevel level) {
        ShipBody own = SableShips.containing(this);
        if (onShip && (own == null || own.isRemoved())) {
            return new Check(false, false, Double.NaN, Double.NaN);
        }
        boolean supported = (legacy || holds(level, baseSupport)) && holds(level, farBlock)
                && BoardingPlankBlock.run(level, getBlockPos()).size() == length;
        Vec3 tipWorld = own != null ? own.toWorld(Vec3.atCenterOf(tip)) : Vec3.atCenterOf(tip);
        Vec3 farNow;
        if (farShip != null) {
            ShipBody far = SableShips.byId(level, farShip);
            if (far == null || far.isRemoved()) {
                return new Check(false, supported, Double.NaN, Double.NaN);
            }
            farNow = far.toWorld(farPoint);
        } else {
            farNow = farPoint;
        }
        Vec3 d = tipWorld.subtract(farNow);
        return new Check(true, supported, Math.sqrt(d.x * d.x + d.z * d.z), d.y);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoardingPlankBlockEntity be) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        int interval = Math.max(1, BoardingConfig.CHECK_INTERVAL_TICKS.get());
        if (Math.floorMod(server.getGameTime() + pos.hashCode(), interval) != 0) {
            return;
        }
        if (be.check(server).breaks()) {
            // segment 0 drops the one plank and plays the wood break; the rest of the run follows its links
            server.destroyBlock(pos, true);
        }
    }

    // ------------------------------------------------------------------ save

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("on_ship", onShip);
        tag.put("base_support", NbtUtils.writeBlockPos(baseSupport));
        if (farShip != null) {
            tag.putUUID("far_ship", farShip);
        }
        tag.putDouble("far_x", farPoint.x);
        tag.putDouble("far_y", farPoint.y);
        tag.putDouble("far_z", farPoint.z);
        tag.put("far_block", NbtUtils.writeBlockPos(farBlock));
        tag.put("tip", NbtUtils.writeBlockPos(tip));
        tag.putInt("length", length);
        tag.putString("landing", landing.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        farShip = tag.hasUUID("far_ship") ? tag.getUUID("far_ship") : null;
        farPoint = new Vec3(tag.getDouble("far_x"), tag.getDouble("far_y"), tag.getDouble("far_z"));
        farBlock = NbtUtils.readBlockPos(tag, "far_block").orElse(BlockPos.ZERO);
        length = Math.max(1, Math.min(PlankRun.MAX_SEGMENTS, tag.getInt("length")));
        if (tag.contains("on_ship")) {
            onShip = tag.getBoolean("on_ship");
            baseSupport = NbtUtils.readBlockPos(tag, "base_support").orElse(getBlockPos());
            tip = NbtUtils.readBlockPos(tag, "tip").orElse(getBlockPos());
        } else {
            // a BRD1 plank: laid from a ship onto a ship, straight and level; its near support was not recorded
            onShip = true;
            Direction facing = getBlockState().getValue(BoardingPlankBlock.FACING);
            tip = getBlockPos().relative(facing, length - 1);
            baseSupport = BlockPos.ZERO;
            legacy = true;
        }
        try {
            landing = PlankRun.Landing.valueOf(tag.getString("landing"));
        } catch (IllegalArgumentException e) {
            landing = PlankRun.Landing.LEVEL;
        }
    }

}
