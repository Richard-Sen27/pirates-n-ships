package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Cell;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.FanCell;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Stringing and breaking ratline nets in a level (RL2, RL4; docs/design.md §4.8 "Ratline nets between fittings",
 * "Triangular ratline nets"). A net (a <em>strand</em>) runs between the rings of two fittings ({@link NetFitting}) and
 * fills every block its line passes through with an invisible, climbable {@link RatlineNetCellBlock}
 * ({@link RatlineNetRules#netCells}); the fittings' block entities name each other ({@link NetFittingBlockEntity}).
 * Everything here is in one frame: the world, or one ship's plot (both fittings on the same body).
 *
 * <p><b>Fans (RL4).</b> Each strand has a <em>hub</em>, the end it climbs to: its mast hoop (with two hoops or none, the
 * end whose ring is higher). A hoop is the hub of up to {@code rigging.net_max_strands} strands from different
 * chainplates; they form its fan: ordered by bearing round the hoop ({@link RatlineNetRules#fanOrder}), and between two
 * neighbouring strands whose feet are at most {@code rigging.net_max_spread} apart the triangle (hub ring, both foot
 * rings) is filled with net cells too ({@link RatlineNetRules#fanCells}). Every cell names its owner strand (a fill cell
 * the earlier strand of its pair) and the hub. The cells of a fan are always brought in line with its strands as a
 * whole ({@link #sync}): adding or removing a strand re-fans the rest without touching anything else.
 *
 * <p>Breaking a strand's cell, a fill cell it owns, or its chainplate takes that strand down
 * ({@link #removeStrands}): its net item drops at its chainplate (its foot), the hub and the other strands stay and
 * re-fan; breaking the hub takes every strand of its fan. Sable's assembly moves the blocks with
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

    /** A net seen from its hub (RL4): {@code hubIsB} when fitting B is the hub. */
    public record Strand(Net net, boolean hubIsB) {
        public BlockPos hub() {
            return hubIsB ? net.b() : net.a();
        }

        public BlockPos foot() {
            return hubIsB ? net.a() : net.b();
        }

        public Point hubRing() {
            return hubIsB ? net.ringB() : net.ringA();
        }

        public Point footRing() {
            return hubIsB ? net.ringA() : net.ringB();
        }
    }

    /** Where a fan wants a net cell: the strand that owns it and whether it is a fill cell. */
    public record Placement(Strand owner, boolean fill) {
    }

    /** A hub's fan: its strands in fan order, which neighbours join, and every cell it wants. */
    public record Fan(BlockPos hub, List<Strand> strands, boolean[] joinNext, Map<BlockPos, Placement> cells) {
    }

    /** Why a net cannot be strung, with the first blocked cell for {@link Refusal#BLOCKED}. */
    public enum Refusal { NONE, SAME, TOO_LONG, TOO_FLAT, OTHER_BODY, BUSY, FULL, BLOCKED }

    /**
     * The outcome of {@link #check}; {@code apart} is the distance to the nearest neighbouring strand's foot when the
     * new strand has neighbours in its fan but joins none of them (too far apart), else NaN.
     */
    public record Check(Refusal refusal, @Nullable BlockPos blocked, Net net, double apart) {
    }

    /** While a fan's cells are being brought in line, removing one of them breaks nothing (server thread only). */
    private static int syncing;

    private RatlineNets() {
    }

    // ------------------------------------------------------------------ reading

    /** The ring point of the fitting at {@code pos} (block coordinates), or null when no fitting is there. */
    public static @Nullable Point ring(BlockGetter level, BlockPos pos) {
        return ring(level.getBlockState(pos), pos);
    }

    private static @Nullable Point ring(BlockState state, BlockPos pos) {
        return state.getBlock() instanceof NetFitting f ? f.ring(state, pos) : null;
    }

    static Point point(Vec3 v) {
        return new Point(v.x, v.y, v.z);
    }

    /** The first net held by the fitting at {@code pos}, or null. */
    public static @Nullable Net netOf(BlockGetter level, BlockPos pos) {
        List<Net> all = netsOf(level, pos);
        return all.isEmpty() ? null : all.get(0);
    }

    /** Every net held by the fitting at {@code pos} (in fan order at a hub). */
    public static List<Net> netsOf(BlockGetter level, BlockPos pos) {
        return netsOf(level, level::getBlockState, pos);
    }

    private static List<Net> netsOf(BlockGetter level, Function<BlockPos, BlockState> states, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof NetFittingBlockEntity be)) {
            return List.of();
        }
        List<Net> out = new ArrayList<>();
        for (NetFittingBlockEntity.Link l : be.links()) {
            BlockPos a = l.isA() ? pos : l.other();
            BlockPos b = l.isA() ? l.other() : pos;
            Point ra = ring(states.apply(a), a), rb = ring(states.apply(b), b);
            if (ra != null && rb != null) {
                out.add(new Net(a.immutable(), b.immutable(), ra, rb));
            }
        }
        return out;
    }

    /** The net strung from fitting {@code a} to fitting {@code b}, or null. */
    public static @Nullable Net net(BlockGetter level, BlockPos a, BlockPos b) {
        if (!(level.getBlockEntity(a) instanceof NetFittingBlockEntity be) || !be.holds(b)) {
            return null;
        }
        Point ra = ring(level, a), rb = ring(level, b);
        return ra == null || rb == null ? null : new Net(a.immutable(), b.immutable(), ra, rb);
    }

    /** The strand of {@code net}: its hub is its hoop end; with two hoops or none, the end with the higher ring (B on a tie). */
    public static Strand strand(BlockGetter level, Net net) {
        return strand(level::getBlockState, net);
    }

    private static Strand strand(Function<BlockPos, BlockState> states, Net net) {
        boolean ha = states.apply(net.a()).getBlock() instanceof MastHoopBlock;
        boolean hb = states.apply(net.b()).getBlock() instanceof MastHoopBlock;
        return new Strand(net, ha != hb ? hb : net.ringB().y() >= net.ringA().y());
    }

    /** The strands whose hub is the fitting at {@code hub}, in its stored (fan) order. */
    public static List<Strand> hubStrands(BlockGetter level, BlockPos hub) {
        return hubStrands(level, level::getBlockState, hub);
    }

    private static List<Strand> hubStrands(BlockGetter level, Function<BlockPos, BlockState> states, BlockPos hub) {
        List<Strand> out = new ArrayList<>();
        for (Net n : netsOf(level, states, hub)) {
            Strand s = strand(states, n);
            if (s.hub().equals(hub)) {
                out.add(s);
            }
        }
        return out;
    }

    /** The strand that owns the net cell at {@code pos}, or null (also for a cell whose net is gone). */
    public static @Nullable Strand ownerStrand(BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof RatlineNetCellBlockEntity cell) || cell.fittingA() == null) {
            return null;
        }
        BlockPos a = cell.fittingA();
        BlockPos b = cell.fittingB();
        Net n;
        if (b != null) {
            n = net(level, a, b);
        } else {
            // a cell strung before RL4 names A only: its net is the one A holds
            n = netOf(level, a);
            if (n != null && !n.a().equals(a)) {
                n = null;
            }
        }
        return n == null ? null : strand(level, n);
    }

    /**
     * The line a climber in the climbable block {@code pos} follows (the climbing assist, {@link RatlineNetAssist}): a
     * strand cell's strand; for a fill cell (RL4) the line from below the cell through its centre to the hub's ring (ring
     * B, fitting B the hub, A the owning strand's foot), so the climber goes along its climb direction straight to the
     * hoop; a fitting's first net. Null elsewhere.
     */
    public static @Nullable Net climbLine(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(RiggingContent.NET_CELL.get())) {
            Strand s = ownerStrand(level, pos);
            if (s == null) {
                return null;
            }
            if (!(level.getBlockEntity(pos) instanceof RatlineNetCellBlockEntity cell) || !cell.isFill()) {
                return s.net();
            }
            Point hub = s.hubRing();
            Point c = RatlineNetRules.centre(new Cell(pos.getX(), pos.getY(), pos.getZ()));
            double dx = c.x() - hub.x(), dy = c.y() - hub.y(), dz = c.z() - hub.z();
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double k = len < 1.0e-9 ? 0 : (len + 1.0) / len;
            Point from = new Point(hub.x() + dx * k, hub.y() + dy * k, hub.z() + dz * k);
            return new Net(s.foot(), s.hub(), from, hub);
        }
        return state.getBlock() instanceof NetFitting ? netOf(level, pos) : null;
    }

    /** The hub named by the net cell at {@code pos}, or null. */
    private static @Nullable BlockPos cellHub(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RatlineNetCellBlockEntity cell ? cell.hub() : null;
    }

    /** Whether the block at {@code pos} is a net cell of the fan at {@code hub} (a cell strung before RL4 counts when its strand's hub is it). */
    private static boolean ours(BlockGetter level, BlockPos pos, BlockPos hub) {
        if (!level.getBlockState(pos).is(RiggingContent.NET_CELL.get())) {
            return false;
        }
        BlockPos named = cellHub(level, pos);
        if (named != null) {
            return named.equals(hub);
        }
        Strand s = ownerStrand(level, pos);
        return s == null || s.hub().equals(hub);
    }

    // ------------------------------------------------------------------ the fan

    /** The fan at {@code hub} made of {@code strands} (any order), with neighbours joined up to {@code maxSpread} apart. */
    private static Fan fan(BlockPos hub, List<Strand> strands, double maxSpread) {
        if (strands.isEmpty()) {
            return new Fan(hub, List.of(), new boolean[0], Map.of());
        }
        Point hubRing = strands.get(0).hubRing();
        List<Point> feet = new ArrayList<>();
        for (Strand s : strands) {
            feet.add(s.footRing());
        }
        int[] order = RatlineNetRules.fanOrder(hubRing, feet);
        List<Strand> ordered = new ArrayList<>();
        List<Point> orderedFeet = new ArrayList<>();
        List<List<Cell>> strandCells = new ArrayList<>();
        for (int i : order) {
            ordered.add(strands.get(i));
            orderedFeet.add(feet.get(i));
            strandCells.add(strands.get(i).net().cells());
        }
        boolean[] join = new boolean[ordered.size()];
        for (int k = 0; k + 1 < ordered.size(); k++) {
            join[k] = RatlineNetRules.joins(orderedFeet.get(k), orderedFeet.get(k + 1), maxSpread);
        }
        Map<BlockPos, Placement> cells = new LinkedHashMap<>();
        for (FanCell c : RatlineNetRules.fanCells(hubRing, orderedFeet, strandCells, join)) {
            cells.put(new BlockPos(c.cell().x(), c.cell().y(), c.cell().z()), new Placement(ordered.get(c.owner()), c.fill()));
        }
        return new Fan(hub, ordered, join, cells);
    }

    private static double maxSpread() {
        return RiggingConfig.NET_MAX_SPREAD.get();
    }

    /** The fan at {@code hub} as it stands. */
    public static Fan fanAt(BlockGetter level, BlockPos hub) {
        return fan(hub, hubStrands(level, hub), maxSpread());
    }

    // ------------------------------------------------------------------ stringing

    /**
     * Whether a net may run from the fitting at {@code a} to the one at {@code b}: one body, a chainplate holds one net
     * and a hoop up to {@code rigging.net_max_strands}, the line's length and pitch ({@link RatlineNetRules#check}),
     * then every cell of the strand and of its fan's new fill air, water or already a cell of that fan.
     */
    public static Check check(ServerLevel level, BlockPos a, BlockPos b, double maxLength, double minPitch) {
        Point ra = Objects.requireNonNull(ring(level, a)), rb = Objects.requireNonNull(ring(level, b));
        Net net = new Net(a.immutable(), b.immutable(), ra, rb);
        if (a.equals(b)) {
            return new Check(Refusal.SAME, null, net, Double.NaN);
        }
        if (!Objects.equals(shipOf(level, a), shipOf(level, b))) {
            return new Check(Refusal.OTHER_BODY, null, net, Double.NaN);
        }
        for (Refusal r : new Refusal[] {takesAnother(level, a, b), takesAnother(level, b, a)}) {
            if (r != Refusal.NONE) {
                return new Check(r, null, net, Double.NaN);
            }
        }
        switch (RatlineNetRules.check(ra, rb, maxLength, minPitch)) {
            case SAME -> {
                return new Check(Refusal.SAME, null, net, Double.NaN);
            }
            case TOO_LONG -> {
                return new Check(Refusal.TOO_LONG, null, net, Double.NaN);
            }
            case TOO_FLAT -> {
                return new Check(Refusal.TOO_FLAT, null, net, Double.NaN);
            }
            default -> {
            }
        }
        Strand added = strand(level, net);
        BlockPos hub = added.hub();
        List<Strand> strands = new ArrayList<>(hubStrands(level, hub));
        strands.add(added);
        Fan fan = fan(hub, strands, maxSpread());
        for (Cell c : net.cells()) {
            BlockPos p = new BlockPos(c.x(), c.y(), c.z());
            if (!acceptable(level, p, hub)) {
                return new Check(Refusal.BLOCKED, p, net, Double.NaN);
            }
        }
        for (Map.Entry<BlockPos, Placement> e : fan.cells().entrySet()) {
            BlockPos p = e.getKey();
            if (!acceptable(level, p, hub) && !(e.getValue().fill() && skipsFill(level.getBlockState(p)))) {
                return new Check(Refusal.BLOCKED, p, net, Double.NaN);
            }
        }
        return new Check(Refusal.NONE, null, net, apart(fan, added));
    }

    /** NaN when {@code added} has no neighbour in {@code fan} or joins one; else the distance to its nearest neighbour's foot. */
    private static double apart(Fan fan, Strand added) {
        int k = fan.strands().indexOf(added);
        if (k < 0 || fan.strands().size() < 2) {
            return Double.NaN;
        }
        boolean joined = (k > 0 && fan.joinNext()[k - 1]) || (k + 1 < fan.strands().size() && fan.joinNext()[k]);
        if (joined) {
            return Double.NaN;
        }
        double nearest = Double.POSITIVE_INFINITY;
        for (int n : new int[] {k - 1, k + 1}) {
            if (n >= 0 && n < fan.strands().size()) {
                nearest = Math.min(nearest, RatlineNetRules.spread(added.footRing(), fan.strands().get(n).footRing()));
            }
        }
        return nearest;
    }

    private static boolean acceptable(ServerLevel level, BlockPos p, BlockPos hub) {
        return free(level.getBlockState(p)) || ours(level, p, hub);
    }

    /**
     * Whether the fitting at {@code pos} can take another net (to {@code other}, when given): a chainplate holds one, a
     * hoop up to {@code rigging.net_max_strands}, and no two nets join the same two fittings.
     */
    public static Refusal takesAnother(BlockGetter level, BlockPos pos, @Nullable BlockPos other) {
        if (!(level.getBlockEntity(pos) instanceof NetFittingBlockEntity be) || !be.hasNet()) {
            return Refusal.NONE;
        }
        if (other != null && be.holds(other)) {
            return Refusal.BUSY;
        }
        if (!(level.getBlockState(pos).getBlock() instanceof MastHoopBlock)) {
            return Refusal.BUSY;
        }
        return be.netCount() >= RiggingConfig.NET_MAX_STRANDS.get() ? Refusal.FULL : Refusal.NONE;
    }

    /**
     * A block a fan's fill leaves out instead of being blocked by it: another net fitting (a chainplate waiting on the
     * rail between two strands, so the outer strands may be strung first).
     */
    public static boolean skipsFill(BlockState state) {
        return state.getBlock() instanceof NetFitting;
    }

    /** A cell a net may take: air, or water (a source or flowing). */
    public static boolean free(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER);
    }

    /** Server: strings {@code net} (checked by {@link #check}): both fittings' block entities, then its fan's cells. */
    public static void string(ServerLevel level, Net net) {
        Strand added = strand(level, net);
        BlockPos hub = added.hub();
        Set<BlockPos> stale = new HashSet<>(fanAt(level, hub).cells().keySet());
        if (level.getBlockEntity(net.a()) instanceof NetFittingBlockEntity fa) {
            fa.setNet(net.b(), true);
        }
        if (level.getBlockEntity(net.b()) instanceof NetFittingBlockEntity fb) {
            fb.setNet(net.a(), false);
        }
        sync(level, level::getBlockState, hub, stale, Set.of());
    }

    // ------------------------------------------------------------------ keeping a fan's cells in line

    /**
     * Server: brings the cells of the fan at {@code hub} in line with its strands: stores the fan order and joins in the
     * hub, places or re-owns every cell the fan wants (unless in {@code skip}), and removes every former cell of it in
     * {@code stale} the fan no longer wants. A wanted strand cell taken by another block breaks that strand; a wanted
     * fill cell taken is left out.
     */
    static void sync(ServerLevel level, Function<BlockPos, BlockState> states, BlockPos hub, Collection<BlockPos> stale,
                     Set<BlockPos> skip) {
        Fan fan = fan(hub, hubStrands(level, states, hub), maxSpread());
        if (level.getBlockEntity(hub) instanceof NetFittingBlockEntity be) {
            List<BlockPos> order = new ArrayList<>();
            for (Strand s : fan.strands()) {
                order.add(s.foot());
            }
            be.setFan(order, fan.joinNext());
        }
        List<Strand> broken = new ArrayList<>();
        BlockState cellState = RiggingContent.NET_CELL.get().defaultBlockState();
        syncing++;
        try {
            for (Map.Entry<BlockPos, Placement> e : fan.cells().entrySet()) {
                BlockPos p = e.getKey();
                Placement pl = e.getValue();
                if (skip.contains(p)) {
                    continue;
                }
                if (!ours(level, p, hub)) {
                    if (!free(level.getBlockState(p))) {
                        if (!pl.fill() && !broken.contains(pl.owner())) {
                            broken.add(pl.owner());
                        }
                        continue;
                    }
                    boolean water = level.getFluidState(p).getType() == Fluids.WATER;
                    level.setBlock(p, cellState.setValue(RatlineNetCellBlock.WATERLOGGED, water), Block.UPDATE_ALL);
                }
                if (level.getBlockEntity(p) instanceof RatlineNetCellBlockEntity cell) {
                    Net n = pl.owner().net();
                    cell.setOwner(n.a(), n.b(), hub, pl.fill());
                }
            }
            for (BlockPos p : stale) {
                if (fan.cells().containsKey(p) || skip.contains(p)) {
                    continue;
                }
                BlockState s = level.getBlockState(p);
                if (s.is(RiggingContent.NET_CELL.get())) {
                    BlockPos named = cellHub(level, p);
                    if (named == null || named.equals(hub)) {
                        level.setBlock(p, Waterlogging.leftBehind(s), Block.UPDATE_ALL);
                    }
                }
            }
        } finally {
            syncing--;
        }
        if (!broken.isEmpty()) {
            removeStrands(level, states, hub, broken, true, skip);
        }
    }

    /**
     * Server: takes the {@code gone} strands of the fan at {@code hub} down: both fittings forget each, the fan re-fans
     * without them ({@link #sync}, leaving {@code skip} alone: blocks being replaced right now) and, with {@code drop},
     * each one's net item drops at its foot. When something was skipped the hub checks its fan again next tick.
     */
    static void removeStrands(ServerLevel level, Function<BlockPos, BlockState> states, BlockPos hub, List<Strand> gone,
                              boolean drop, Set<BlockPos> skip) {
        Set<BlockPos> stale = new HashSet<>(fan(hub, hubStrands(level, states, hub), maxSpread()).cells().keySet());
        for (Strand s : gone) {
            for (Cell c : s.net().cells()) {
                stale.add(new BlockPos(c.x(), c.y(), c.z()));
            }
            if (level.getBlockEntity(s.net().a()) instanceof NetFittingBlockEntity fa) {
                fa.clearNet(s.net().b());
            }
            if (level.getBlockEntity(s.net().b()) instanceof NetFittingBlockEntity fb) {
                fb.clearNet(s.net().a());
            }
        }
        sync(level, states, hub, stale, skip);
        if (drop) {
            for (Strand s : gone) {
                Block.popResource(level, s.foot(), new ItemStack(RiggingContent.NET_ITEM.get()));
            }
        }
        if (!skip.isEmpty() && !skip.contains(hub)) {
            BlockState h = level.getBlockState(hub);
            if (h.getBlock() instanceof NetFitting) {
                level.scheduleTick(hub, h.getBlock(), 1);
            }
        }
    }

    /** A fitting's scheduled tick: its fan's cells brought in line again (fills a hole left by a block that was replaced). */
    static void resync(ServerLevel level, BlockPos hub) {
        if (!hubStrands(level, hub).isEmpty()) {
            sync(level, level::getBlockState, hub, List.of(), Set.of());
        }
    }

    /**
     * Server: takes {@code net} down: both fittings forget it, its cells and the fill it owned go (their water stays),
     * the rest of its fan re-fans and, with {@code drop}, one net item drops at its foot.
     */
    public static void breakNet(ServerLevel level, Net net, boolean drop) {
        Strand s = strand(level, net);
        removeStrands(level, level::getBlockState, s.hub(), List.of(s), drop, Set.of());
    }

    /**
     * From a fitting's {@code onRemove} (before its block entity goes): every net it held breaks, each dropping at its
     * foot; the hubs that stay re-fan. The removed fitting's ring comes from its old {@code state}.
     */
    static void onFittingRemoved(Level level, BlockPos pos, BlockState state, boolean movedByPiston, BlockState newState) {
        if (movedByPiston || newState.is(state.getBlock()) || !(level instanceof ServerLevel server)
                || !(level.getBlockEntity(pos) instanceof NetFittingBlockEntity be) || !be.hasNet()) {
            return;
        }
        BlockPos at = pos.immutable();
        Function<BlockPos, BlockState> states = p -> p.equals(at) ? state : level.getBlockState(p);
        Map<BlockPos, List<Strand>> byHub = new LinkedHashMap<>();
        for (Net n : netsOf(level, states, at)) {
            Strand s = strand(states, n);
            byHub.computeIfAbsent(s.hub(), h -> new ArrayList<>()).add(s);
        }
        for (Map.Entry<BlockPos, List<Strand>> e : byHub.entrySet()) {
            removeStrands(server, states, e.getKey(), e.getValue(), true, Set.of(at));
        }
        be.clearNet();
    }

    /** From a cell's {@code onRemove} (before its block entity goes): the strand that owns the cell breaks. */
    static void onCellRemoved(Level level, BlockPos pos, boolean movedByPiston, BlockState newState) {
        if (syncing > 0 || movedByPiston || newState.is(RiggingContent.NET_CELL.get()) || !(level instanceof ServerLevel server)) {
            return;
        }
        Strand s = ownerStrand(level, pos);
        if (s != null) {
            removeStrands(server, level::getBlockState, s.hub(), List.of(s), true, Set.of(pos.immutable()));
        }
    }

    // ------------------------------------------------------------------ helpers

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

    /** All cells of {@code net} that are net cells now (for tests). */
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
