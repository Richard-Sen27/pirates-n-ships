package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Cell;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Point;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Stringing and breaking ratline nets in a level (RL2, docs/design.md §4.8 "Ratline nets between fittings"). A net
 * runs between the rings of two fittings ({@link NetFitting}) and fills every block its line passes through with an
 * invisible, climbable {@link RatlineNetCellBlock} ({@link RatlineNetRules#netCells}); the fittings' block entities name
 * each other ({@link NetFittingBlockEntity}), each cell names fitting A. Everything here is in one frame: the world, or
 * one ship's plot (both fittings on the same body).
 *
 * <p>The net breaks as one ({@link #breakNet}): breaking either fitting, breaking a cell or placing a block into one
 * clears every cell and drops one net item at A; the fittings stay. Sable's assembly moves the blocks with
 * {@code movedByPiston} set ({@code SubLevelAssemblyHelper#moveBlocks} l.409/452 call
 * {@code LevelChunk#setBlockState(pos, state, true)}), which is never a break.
 */
public final class RatlineNets {

    /** A strung net: its fittings and their rings, in one frame. */
    public record Net(BlockPos a, BlockPos b, Point ringA, Point ringB) {
        public List<Cell> cells() {
            return RatlineNetRules.netCells(ringA, ringB);
        }
    }

    /** Why a net cannot be strung, with the first blocked cell for {@link Refusal#BLOCKED}. */
    public enum Refusal { NONE, SAME, TOO_LONG, TOO_FLAT, OTHER_BODY, BUSY, BLOCKED }

    /** The outcome of {@link #check}. */
    public record Check(Refusal refusal, @Nullable BlockPos blocked, Net net) {
    }

    private RatlineNets() {
    }

    /** The ring point of the fitting at {@code pos} (block coordinates), or null when no fitting is there. */
    public static @Nullable Point ring(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof NetFitting f ? f.ring(state, pos) : null;
    }

    static Point point(Vec3 v) {
        return new Point(v.x, v.y, v.z);
    }

    /** The net held by the fitting at {@code pos}, or null. */
    public static @Nullable Net netOf(BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof NetFittingBlockEntity be) || !be.hasNet()) {
            return null;
        }
        BlockPos other = Objects.requireNonNull(be.partner());
        BlockPos a = be.isA() ? pos : other;
        BlockPos b = be.isA() ? other : pos;
        Point ra = ring(level, a), rb = ring(level, b);
        return ra == null || rb == null ? null : new Net(a, b, ra, rb);
    }

    /**
     * Whether a net may run from the fitting at {@code a} to the one at {@code b}: one body, both free, the line's
     * length and pitch ({@link RatlineNetRules#check}), then every cell on it air or water.
     */
    public static Check check(ServerLevel level, BlockPos a, BlockPos b, double maxLength, double minPitch) {
        Point ra = Objects.requireNonNull(ring(level, a)), rb = Objects.requireNonNull(ring(level, b));
        Net net = new Net(a.immutable(), b.immutable(), ra, rb);
        if (a.equals(b)) {
            return new Check(Refusal.SAME, null, net);
        }
        if (!Objects.equals(shipOf(level, a), shipOf(level, b))) {
            return new Check(Refusal.OTHER_BODY, null, net);
        }
        if (busy(level, a) || busy(level, b)) {
            return new Check(Refusal.BUSY, null, net);
        }
        switch (RatlineNetRules.check(ra, rb, maxLength, minPitch)) {
            case SAME -> {
                return new Check(Refusal.SAME, null, net);
            }
            case TOO_LONG -> {
                return new Check(Refusal.TOO_LONG, null, net);
            }
            case TOO_FLAT -> {
                return new Check(Refusal.TOO_FLAT, null, net);
            }
            default -> {
            }
        }
        for (Cell c : net.cells()) {
            BlockPos p = new BlockPos(c.x(), c.y(), c.z());
            if (!free(level.getBlockState(p))) {
                return new Check(Refusal.BLOCKED, p, net);
            }
        }
        return new Check(Refusal.NONE, null, net);
    }

    private static boolean busy(BlockGetter level, BlockPos fitting) {
        return level.getBlockEntity(fitting) instanceof NetFittingBlockEntity be && be.hasNet();
    }

    /** A cell a net may take: air, or water (a source or flowing). */
    public static boolean free(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER);
    }

    /** Server: strings {@code net} (checked by {@link #check}): the cells, then both fittings' block entities. */
    public static void string(ServerLevel level, Net net) {
        BlockState cell = RiggingContent.NET_CELL.get().defaultBlockState();
        for (Cell c : net.cells()) {
            BlockPos p = new BlockPos(c.x(), c.y(), c.z());
            boolean water = level.getFluidState(p).getType() == Fluids.WATER;
            level.setBlock(p, cell.setValue(RatlineNetCellBlock.WATERLOGGED, water), Block.UPDATE_ALL);
            if (level.getBlockEntity(p) instanceof RatlineNetCellBlockEntity be) {
                be.setFittingA(net.a());
            }
        }
        if (level.getBlockEntity(net.a()) instanceof NetFittingBlockEntity fa) {
            fa.setNet(net.b(), true);
        }
        if (level.getBlockEntity(net.b()) instanceof NetFittingBlockEntity fb) {
            fb.setNet(net.a(), false);
        }
    }

    /**
     * Server: takes {@code net} down: both fittings forget it, every cell of it goes (its water stays) and, with
     * {@code drop}, one net item drops at fitting A. Cells of another net on the line are left alone.
     */
    public static void breakNet(ServerLevel level, Net net, boolean drop) {
        for (BlockPos f : new BlockPos[] {net.a(), net.b()}) {
            if (level.getBlockEntity(f) instanceof NetFittingBlockEntity be) {
                be.clearNet();
            }
        }
        for (Cell c : net.cells()) {
            BlockPos p = new BlockPos(c.x(), c.y(), c.z());
            BlockState s = level.getBlockState(p);
            if (s.is(RiggingContent.NET_CELL.get()) && level.getBlockEntity(p) instanceof RatlineNetCellBlockEntity be
                    && net.a().equals(be.fittingA())) {
                level.setBlock(p, Waterlogging.leftBehind(s), Block.UPDATE_ALL);
            }
        }
        if (drop) {
            Block.popResource(level, net.a(), new ItemStack(RiggingContent.NET_ITEM.get()));
        }
    }

    /**
     * From a fitting's {@code onRemove} (before its block entity goes): when it held a net, the net breaks. The ring of
     * the removed fitting comes from its old {@code state}.
     */
    static void onFittingRemoved(Level level, BlockPos pos, BlockState state, boolean movedByPiston, BlockState newState) {
        if (movedByPiston || newState.is(state.getBlock()) || !(level instanceof ServerLevel server)
                || !(level.getBlockEntity(pos) instanceof NetFittingBlockEntity be) || !be.hasNet()) {
            return;
        }
        BlockPos other = Objects.requireNonNull(be.partner());
        Point self = ((NetFitting) state.getBlock()).ring(state, pos);
        Point ro = ring(level, other);
        if (ro == null) {
            be.clearNet();
            return;
        }
        Net net = be.isA() ? new Net(pos.immutable(), other, self, ro) : new Net(other, pos.immutable(), ro, self);
        be.clearNet();
        breakNet(server, net, true);
    }

    /** From a cell's {@code onRemove} (before its block entity goes): the cell's net breaks. */
    static void onCellRemoved(Level level, BlockPos pos, boolean movedByPiston, BlockState newState) {
        if (movedByPiston || newState.is(RiggingContent.NET_CELL.get()) || !(level instanceof ServerLevel server)
                || !(level.getBlockEntity(pos) instanceof RatlineNetCellBlockEntity cell) || cell.fittingA() == null) {
            return;
        }
        Net net = netOf(level, cell.fittingA());
        if (net != null && net.a().equals(cell.fittingA())) {
            breakNet(server, net, true);
        }
    }

    /** The id of the ship whose plot holds {@code pos}, or null in the world. */
    static @Nullable UUID shipOf(ServerLevel level, BlockPos pos) {
        ShipBody ship = SableShips.containing(level, pos);
        return ship == null ? null : ship.id();
    }

    /** {@code pos} in world coordinates (its block centre moved out of a ship's plot), for messages. */
    static BlockPos worldPos(ServerLevel level, BlockPos pos) {
        ShipBody ship = SableShips.containing(level, pos);
        return ship == null ? pos : BlockPos.containing(ship.toWorld(Vec3.atCenterOf(pos)));
    }

    /** The ring offset of a fitting: {@code inset} blocks from the support face behind it, at the given height. */
    static Point ringOnFace(BlockPos pos, Direction away, double fromSupport, double height) {
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;
        double back = 0.5 - fromSupport;
        return new Point(cx - away.getStepX() * back, pos.getY() + height, cz - away.getStepZ() * back);
    }

    /** All cells of {@code net} that are its cells now (for tests). */
    public static List<BlockPos> standingCells(BlockGetter level, Net net) {
        List<BlockPos> out = new ArrayList<>();
        for (Cell c : net.cells()) {
            BlockPos p = new BlockPos(c.x(), c.y(), c.z());
            if (level.getBlockState(p).is(RiggingContent.NET_CELL.get())) {
                out.add(p);
            }
        }
        return out;
    }
}
