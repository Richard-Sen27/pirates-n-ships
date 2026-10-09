package com.richardsenger.piratesnships.combat.boarding;

import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Every cell of a boarding plank run (BRD1, BRD2, BRD4) has one: where the straight board's underside lies in this cell
 * ({@link #near()} and {@link #far()}, blocks above the cell's floor at its near and far edge along the block's
 * {@code facing}), from which the cell's stepped collision is built ({@link BoardingPlankBlock#cellShape}), and where
 * the run's segment 0 is ({@link #base()}).
 *
 * <p>Segment 0 (the base) also holds the run: its cells ({@link #cells()}), its length in columns and rise in whole
 * blocks (the board drawn by {@code BoardingPlankRenderer} runs from {@link #boardStart()} to {@link #boardEnd()}),
 * whether a lip is drawn at either end, and what both ends rest on. The near end (segment 0's end) rests on
 * {@link #baseSupport()}, a block in the run's own frame (the ship whose plot holds the run, or the world). The far end
 * rests on {@link #farBlock()} of the far body: a ship ({@link #farShip()}) or the world (null), and was laid at
 * {@link #farPoint()} in that body's frame.
 *
 * <p>Every {@code boarding.plank.check_interval_ticks} the base breaks the whole run ({@link PlankRun#breaks}) when the
 * run left the ship it was laid in, a ship at either end is gone, a block either end rests on is gone, the run lost a
 * cell, or the far end drifted from where it was laid more than {@code break_distance} sideways or more than
 * {@code break_rise} up or down (a moored ship's heave in a storm does not snap a gangway at a pier). A run between two
 * world points does not move, so only its supports and cells are watched.
 *
 * <p><b>Old saves.</b> A BRD1 or BRD2 base (no {@code cells} in its data) recomputes its run on its first server tick
 * ({@link #relay}): a level run (BRD1, or BRD2 landing level on one row) keeps its blocks and gets flat boards; any
 * other BRD2 run (ramps, steps) is taken up without drops and laid again as one straight board by the planner from the
 * block its near end rests on (its top face, else its side facing the run); when no straight board fits there any
 * more, the run is gone and its one plank drops.
 */
public class BoardingPlankBlockEntity extends BlockEntity {

    /** How a base loaded from an old save still has to be turned into a straight run. */
    enum Legacy { NONE, BRD1, BRD2 }

    // every cell
    private double near;
    private double far;
    private @Nullable BlockPos baseOffset;
    private @Nullable VoxelShape shape;

    // segment 0
    private final List<BlockPos> cellOffsets = new ArrayList<>();
    private int columns = 1;
    private int rise;
    private boolean lipNear = true;
    private boolean lipFar;
    private boolean onShip;
    private BlockPos baseSupport = BlockPos.ZERO;
    private @Nullable UUID farShip;
    private Vec3 farPoint = Vec3.ZERO;
    private BlockPos farBlock = BlockPos.ZERO;
    private BlockPos tip = BlockPos.ZERO;
    private int length = 1;
    private PlankRun.Landing landing = PlankRun.Landing.LEVEL;
    /** The near support is not known (a BRD1 save), so it is not watched. */
    private boolean unknownSupport;
    private Legacy legacy = Legacy.NONE;

    public BoardingPlankBlockEntity(BlockPos pos, BlockState state) {
        super(BoardingContent.PLANK_BLOCK_ENTITY.get(), pos, state);
    }

    // ------------------------------------------------------------------ laying

    /** Records this cell's board heights and its run's segment 0 (called by {@link BoardingPlanks#place}). */
    void cell(BlockPos base, double near, double far) {
        this.near = near;
        this.far = far;
        this.baseOffset = base.subtract(getBlockPos());
        this.shape = null;
        setChanged();
    }

    /** Records the run and what it rests on, on segment 0 (called by {@link BoardingPlanks#place} after the cells). */
    void link(List<BlockPos> cells, int columns, int rise, boolean lipNear, boolean lipFar, boolean onShip,
              BlockPos baseSupport, @Nullable UUID farShip, Vec3 farPoint, BlockPos farBlock, PlankRun.Landing landing) {
        cellOffsets.clear();
        for (BlockPos c : cells) cellOffsets.add(c.subtract(getBlockPos()));
        this.columns = Math.max(1, columns);
        this.rise = rise;
        this.lipNear = lipNear;
        this.lipFar = lipFar;
        this.onShip = onShip;
        this.baseSupport = baseSupport.immutable();
        this.farShip = farShip;
        this.farPoint = farPoint;
        this.farBlock = farBlock.immutable();
        this.tip = cells.get(cells.size() - 1).immutable();
        this.length = cells.size();
        this.landing = landing;
        this.unknownSupport = false;
        this.legacy = Legacy.NONE;
        setChanged();
    }

    /** Sends this cell's data to the clients watching it (after {@link #cell} or {@link #link}). */
    void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------ the board

    /** The board's underside at this cell's near edge, blocks above the cell's floor. */
    public double near() {
        return near;
    }

    /** The board's underside at this cell's far edge, blocks above the cell's floor. */
    public double far() {
        return far;
    }

    /** This cell belongs to a known run (false for a cell of an old save not recomputed yet, or a Ponder scene's). */
    public boolean linked() {
        return baseOffset != null;
    }

    /** Position of the run's segment 0, or null when not linked. */
    public @Nullable BlockPos base() {
        return baseOffset == null ? null : getBlockPos().offset(baseOffset);
    }

    /** This is segment 0 of a known run, holding the run's data. */
    public boolean isBase() {
        return baseOffset != null && baseOffset.equals(BlockPos.ZERO) && !cellOffsets.isEmpty();
    }

    /** The run's cells (segment 0 first), on segment 0; empty elsewhere. */
    public List<BlockPos> cells() {
        List<BlockPos> out = new ArrayList<>(cellOffsets.size());
        for (BlockPos o : cellOffsets) out.add(getBlockPos().offset(o));
        return out;
    }

    /** The run's length in columns. */
    public int columns() {
        return columns;
    }

    /** Whole blocks the board rises from segment 0's end to the far end (negative when it falls). */
    public int rise() {
        return rise;
    }

    public boolean lipNear() {
        return lipNear;
    }

    public boolean lipFar() {
        return lipFar;
    }

    /** Segment 0's end of the board's underside (its middle), relative to this block's origin. */
    public Vec3 boardStart() {
        Direction f = facing();
        return new Vec3(0.5 - 0.5 * f.getStepX(), near, 0.5 - 0.5 * f.getStepZ());
    }

    /** The far end of the board's underside (its middle), relative to this block's origin. */
    public Vec3 boardEnd() {
        Direction f = facing();
        return boardStart().add(f.getStepX() * (double) columns, rise, f.getStepZ() * (double) columns);
    }

    /** This cell's collision and outline: the board's steps within it. */
    public VoxelShape shape() {
        VoxelShape s = shape;
        if (s == null) {
            s = BoardingPlankBlock.cellShape(facing(), near * 16.0, far * 16.0);
            shape = s;
        }
        return s;
    }

    // ------------------------------------------------------------------ the ends

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

    /** Cells of the run. */
    public int length() {
        return length;
    }

    public PlankRun.Landing landing() {
        return landing;
    }

    public Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(BoardingPlankBlock.FACING) ? s.getValue(BoardingPlankBlock.FACING) : Direction.NORTH;
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

    /** Every cell of the run is still there and still of this run. */
    public boolean intact(Level level) {
        for (BlockPos c : cells()) {
            if (!level.getBlockState(c).is(BoardingContent.PLANK.get())
                    || !(level.getBlockEntity(c) instanceof BoardingPlankBlockEntity be) || !getBlockPos().equals(be.base())) {
                return false;
            }
        }
        return !cellOffsets.isEmpty();
    }

    /** Looks at both ends now. */
    public Check check(ServerLevel level) {
        ShipBody own = SableShips.containing(this);
        if (onShip && (own == null || own.isRemoved())) {
            return new Check(false, false, Double.NaN, Double.NaN);
        }
        boolean supported = (unknownSupport || holds(level, baseSupport)) && holds(level, farBlock) && intact(level);
        Vec3 tipWorld = own != null ? own.toWorld(Vec3.atCenterOf(tip)) : Vec3.atCenterOf(tip);
        Vec3 farNow;
        if (farShip != null) {
            ShipBody farBody = SableShips.byId(level, farShip);
            if (farBody == null || farBody.isRemoved()) {
                return new Check(false, supported, Double.NaN, Double.NaN);
            }
            farNow = farBody.toWorld(farPoint);
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
        if (be.legacy != Legacy.NONE) {
            be.relay(server);
            return;
        }
        int interval = Math.max(1, BoardingConfig.CHECK_INTERVAL_TICKS.get());
        if (Math.floorMod(server.getGameTime() + pos.hashCode(), interval) != 0) {
            return;
        }
        if (be.isBase() && be.check(server).breaks()) {
            // segment 0 drops the one plank and plays the wood break; the rest of the run goes with it
            server.destroyBlock(pos, true);
        }
    }

    // ------------------------------------------------------------------ old saves

    /** Turns a BRD1 or BRD2 run into a straight one (see the class comment). */
    void relay(ServerLevel level) {
        Legacy kind = legacy;
        legacy = Legacy.NONE;
        BlockPos pos = getBlockPos();
        Direction f = facing();
        BoardingPlankBlock block = BoardingContent.PLANK.get();
        List<BlockPos> old = new ArrayList<>();
        old.add(pos);
        for (int k = 1; k < length; k++) {
            BlockPos at = null;
            BlockPos column = pos.relative(f, k);
            for (int dy = kind == Legacy.BRD1 ? 0 : -k - 1; dy <= (kind == Legacy.BRD1 ? 0 : k + 1) && at == null; dy++) {
                BlockState s = level.getBlockState(column.above(dy));
                if (s.is(block) && s.getValue(BoardingPlankBlock.FACING) == f && s.getValue(BoardingPlankBlock.SEGMENT) == k) {
                    at = column.above(dy);
                }
            }
            if (at == null) break;
            old.add(at);
        }
        // BRD1 runs were level; a BRD2 run is level when it landed level and all its cells lie on one row
        boolean level0 = old.size() == length && (kind == Legacy.BRD1 || landing == PlankRun.Landing.LEVEL);
        for (BlockPos p : old) level0 &= p.getY() == pos.getY();
        if (level0) {
            // a level run: the same cells, flat boards
            for (BlockPos p : old) {
                if (level.getBlockEntity(p) instanceof BoardingPlankBlockEntity be) {
                    be.cell(pos, 0.0, 0.0);
                    be.sync();
                }
            }
            columns = length;
            rise = 0;
            lipNear = true;
            lipFar = false;
            cellOffsets.clear();
            for (BlockPos p : old) cellOffsets.add(p.subtract(pos));
            tip = old.get(old.size() - 1);
            setChanged();
            sync();
            return;
        }
        // a sloped BRD2 run: taken up and laid again as one straight board from its near support
        BlockPos support = baseSupport;
        boolean known = !unknownSupport;
        BoardingPlankBlock.quietly(() -> {
            for (BlockPos p : old) {
                BlockState s = level.getBlockState(p);
                if (s.is(block)) level.setBlock(p, Waterlogging.leftBehind(s), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        });
        PlankPlanner.Plan plan = null;
        if (known) {
            plan = PlankPlanner.plan(level, support, Direction.UP, f, PlankPlanner.settings());
            if (!plan.found()) plan = PlankPlanner.plan(level, support, f, f, PlankPlanner.settings());
        }
        if (plan != null && plan.found()) {
            BoardingPlanks.place(level, plan);
        } else {
            Block.popResource(level, pos, new ItemStack(BoardingContent.PLANK_ITEM.get()));
        }
    }

    // ------------------------------------------------------------------ save

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("near", near);
        tag.putDouble("far", far);
        if (baseOffset != null) {
            tag.putIntArray("base", new int[]{baseOffset.getX(), baseOffset.getY(), baseOffset.getZ()});
        }
        if (cellOffsets.isEmpty()) {
            return;
        }
        int[] cells = new int[cellOffsets.size() * 3];
        for (int k = 0; k < cellOffsets.size(); k++) {
            BlockPos o = cellOffsets.get(k);
            cells[3 * k] = o.getX();
            cells[3 * k + 1] = o.getY();
            cells[3 * k + 2] = o.getZ();
        }
        tag.putIntArray("cells", cells);
        tag.putInt("columns", columns);
        tag.putInt("rise", rise);
        tag.putBoolean("lip_near", lipNear);
        tag.putBoolean("lip_far", lipFar);
        tag.putBoolean("on_ship", onShip);
        tag.putBoolean("unknown_support", unknownSupport);
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
        shape = null;
        near = tag.getDouble("near");
        far = tag.getDouble("far");
        int[] base = tag.getIntArray("base");
        baseOffset = base.length == 3 ? new BlockPos(base[0], base[1], base[2]) : null;
        cellOffsets.clear();
        int[] cells = tag.getIntArray("cells");
        for (int k = 0; k + 2 < cells.length; k += 3) cellOffsets.add(new BlockPos(cells[k], cells[k + 1], cells[k + 2]));
        farShip = tag.hasUUID("far_ship") ? tag.getUUID("far_ship") : null;
        farPoint = new Vec3(tag.getDouble("far_x"), tag.getDouble("far_y"), tag.getDouble("far_z"));
        farBlock = NbtUtils.readBlockPos(tag, "far_block").orElse(BlockPos.ZERO);
        length = Math.max(1, Math.min(PlankRun.MAX_CELLS, tag.getInt("length")));
        try {
            landing = PlankRun.Landing.valueOf(tag.getString("landing"));
        } catch (IllegalArgumentException e) {
            landing = PlankRun.Landing.LEVEL;
        }
        if (!cellOffsets.isEmpty()) {
            columns = Math.max(1, tag.getInt("columns"));
            rise = tag.getInt("rise");
            lipNear = tag.getBoolean("lip_near");
            lipFar = tag.getBoolean("lip_far");
            onShip = tag.getBoolean("on_ship");
            unknownSupport = tag.getBoolean("unknown_support");
            baseSupport = NbtUtils.readBlockPos(tag, "base_support").orElse(getBlockPos());
            tip = NbtUtils.readBlockPos(tag, "tip").orElse(getBlockPos());
            legacy = Legacy.NONE;
        } else if (tag.contains("length")) {
            // segment 0 of a BRD1 or BRD2 save: recomputed as a straight run on the first server tick (relay)
            Direction facing = facing();
            if (tag.contains("on_ship")) {
                onShip = tag.getBoolean("on_ship");
                baseSupport = NbtUtils.readBlockPos(tag, "base_support").orElse(getBlockPos());
                tip = NbtUtils.readBlockPos(tag, "tip").orElse(getBlockPos());
                legacy = Legacy.BRD2;
            } else {
                // BRD1: laid from a ship onto a ship, straight and level; its near support was not recorded
                onShip = true;
                tip = getBlockPos().relative(facing, length - 1);
                baseSupport = BlockPos.ZERO;
                unknownSupport = true;
                legacy = Legacy.BRD1;
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
