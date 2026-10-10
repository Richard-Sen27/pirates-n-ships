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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 * <p><b>Sheets (RL5).</b> A chainplate and a railing latch ({@link RailingLatchBlock}) hold one strand each, a hoop up
 * to {@code net_max_strands} ({@link RatlineNetRules#capacity}), so a net is never wider at the top. Hoops in a row at
 * one height within {@code net_max_spread} of each other form one <em>group</em> ({@link #group}); its strands join
 * pairwise by RL5's row rule ({@link RatlineNetRules#sheetPartners}): one hub and a row of feet is RL4's triangle, a
 * row of hubs over a row of feet a rectangle or a trapezoid ({@link RatlineNetRules#sheetCells}). A group's cells are
 * brought in line as a whole ({@link #sync}). A fill cell between two hubs names both ({@link Placement#hub2}).
 *
 * <p>Breaking a strand's cell, a fill cell it owns, or its chainplate takes that strand down
 * ({@link #removeStrands}): its net item drops at its chainplate (its foot), the hub and the other strands stay and
 * re-fan; breaking the hub takes every strand of its fan. A railing latch that holds no strand any more goes
 * ({@link #freeLatches}): no orphan latches. Sable's assembly moves the blocks with
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

    /**
     * Where a fan wants a net cell: the strand that owns it, whether it is a fill cell and (RL5) the pair's other hub
     * when it is not the owner's.
     */
    public record Placement(Strand owner, boolean fill, @Nullable BlockPos hub2) {
        public Placement(Strand owner, boolean fill) {
            this(owner, fill, null);
        }
    }

    /**
     * A hub's fan: its strands in fan order, whether each owns a pair (RL5: possibly with a strand of another hub of its
     * group), and every cell its group wants.
     */
    public record Fan(BlockPos hub, List<Strand> strands, boolean[] joinNext, Map<BlockPos, Placement> cells) {
    }

    /**
     * RL5: a group of hubs with all their strands, for each strand the index of the strand it owns a pair with (or -1),
     * and every cell the group wants.
     */
    public record Sheet(Set<BlockPos> hubs, List<Strand> strands, int[] partner, Map<BlockPos, Placement> cells) {
        /** Whether strand {@code k} is in any pair. */
        public boolean joined(int k) {
            if (partner[k] >= 0) {
                return true;
            }
            for (int p : partner) {
                if (p == k) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Why a net cannot be strung, with the first blocked cell for {@link Refusal#BLOCKED}. */
    public enum Refusal { NONE, SAME, TOO_LONG, TOO_FLAT, OTHER_BODY, BUSY, FULL, BLOCKED,
        /** RAIL5: chainplates on two railings at one height, but not along one railed edge. */
        NOT_ALONG,
        /** RAIL5: chainplates on two neighbouring railings: no block between them for a boarding net. */
        TOO_SHORT }

    /**
     * The outcome of {@link #check}; {@code apart} is the distance to the nearest neighbouring strand's foot when the
     * new strand has neighbours on its hub but joins none of them, else NaN ({@code notInRow} when that neighbour is
     * close enough but not in a row with it, RL5); {@code boarding} when the net would hang along the rail as a boarding
     * net (RAIL5, {@link BoardingNets}).
     */
    public record Check(Refusal refusal, @Nullable BlockPos blocked, Net net, double apart, boolean boarding, boolean notInRow) {
        public Check(Refusal refusal, @Nullable BlockPos blocked, Net net, double apart) {
            this(refusal, blocked, net, apart, false, false);
        }

        public Check(Refusal refusal, @Nullable BlockPos blocked, Net net, double apart, boolean boarding) {
            this(refusal, blocked, net, apart, boarding, false);
        }
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

    /** RL5: what kind of anchor the fitting {@code state} is (a hoop holds several strands, the others one). */
    static RatlineNetRules.Anchor anchor(BlockState state) {
        if (state.getBlock() instanceof MastHoopBlock) {
            return RatlineNetRules.Anchor.HOOP;
        }
        return state.getBlock() instanceof RailingLatchBlock ? RatlineNetRules.Anchor.LATCH : RatlineNetRules.Anchor.CHAINPLATE;
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
            Cell here = new Cell(pos.getX(), pos.getY(), pos.getZ());
            BlockPos topBlock = s.hub();
            BlockPos h2 = cell.hub2();
            Point r2 = h2 == null ? null : ring(level, h2);
            if (r2 != null) {
                // RL5: between two hubs, towards the nearest point of the top edge (its block is where the climb ends)
                hub = RatlineNetRules.topPoint(here, hub, r2);
                topBlock = BlockPos.containing(hub.x(), hub.y(), hub.z());
            }
            Point c = RatlineNetRules.centre(here);
            double dx = c.x() - hub.x(), dy = c.y() - hub.y(), dz = c.z() - hub.z();
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double k = len < 1.0e-9 ? 0 : (len + 1.0) / len;
            Point from = new Point(hub.x() + dx * k, hub.y() + dy * k, hub.z() + dz * k);
            return new Net(s.foot(), topBlock, from, hub);
        }
        return state.getBlock() instanceof NetFitting ? netOf(level, pos) : null;
    }

    /** The hub named by the net cell at {@code pos}, or null. */
    private static @Nullable BlockPos cellHub(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RatlineNetCellBlockEntity cell ? cell.hub() : null;
    }

    /** Whether the block at {@code pos} is a net cell of the group of {@code hubs} (a cell strung before RL4 counts when its strand's hub is one). */
    private static boolean ours(BlockGetter level, BlockPos pos, Set<BlockPos> hubs) {
        if (!level.getBlockState(pos).is(RiggingContent.NET_CELL.get())) {
            return false;
        }
        BlockPos named = cellHub(level, pos);
        if (named != null) {
            return hubs.contains(named);
        }
        Strand s = ownerStrand(level, pos);
        return s == null || hubs.contains(s.hub());
    }

    // ------------------------------------------------------------------ groups and sheets (RL4, RL5)

    private static double maxSpread() {
        return RiggingConfig.NET_MAX_SPREAD.get();
    }

    /**
     * The group of {@code start}: it, and when it is a mast hoop every hoop holding strands that is reached from it
     * through hoops in a row (along x or z at one height) at most {@code net_max_spread} blocks apart; sorted, so a
     * group's strands come in one order whichever hub it is found from.
     */
    static Set<BlockPos> group(BlockGetter level, Function<BlockPos, BlockState> states, BlockPos start) {
        Set<BlockPos> out = new java.util.TreeSet<>();
        out.add(start.immutable());
        if (!(states.apply(start).getBlock() instanceof MastHoopBlock)) {
            return out;
        }
        int reach = (int) Math.floor(maxSpread() + 1.0e-9);
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(start.immutable());
        while (!queue.isEmpty()) {
            BlockPos h = queue.poll();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                for (int i = 1; i <= reach; i++) {
                    BlockPos p = h.relative(d, i).immutable();
                    if (!out.contains(p) && states.apply(p).getBlock() instanceof MastHoopBlock
                            && !hubStrands(level, states, p).isEmpty()) {
                        out.add(p);
                        queue.add(p);
                    }
                }
            }
        }
        return out;
    }

    /** The sheet of the group {@code hubs} as it stands, with {@code extra} added (a strand being checked) when not null. */
    private static Sheet sheet(BlockGetter level, Function<BlockPos, BlockState> states, Set<BlockPos> hubs,
                               @Nullable Strand extra, double maxSpread) {
        // each hub's strands in bearing order (RL4's fan order), so who owns a shared cell never depends on the order
        // the links happen to be stored in
        List<Strand> strands = new ArrayList<>();
        for (BlockPos h : hubs) {
            List<Strand> own = new ArrayList<>(hubStrands(level, states, h));
            if (extra != null && extra.hub().equals(h)) {
                own.add(extra);
            }
            strands.addAll(bearingOrder(own));
        }
        if (extra != null && !hubs.contains(extra.hub())) {
            strands.add(extra);
        }
        int n = strands.size();
        List<Point> feet = new ArrayList<>(n);
        List<Point> hubRings = new ArrayList<>(n);
        List<List<Cell>> strandCells = new ArrayList<>(n);
        boolean[] joinable = new boolean[n];
        Set<Cell> fittings = new HashSet<>();
        for (int k = 0; k < n; k++) {
            Strand s = strands.get(k);
            feet.add(s.footRing());
            hubRings.add(s.hubRing());
            strandCells.add(s.net().cells());
            joinable[k] = states.apply(s.hub()).getBlock() instanceof MastHoopBlock;
            fittings.add(new Cell(s.foot().getX(), s.foot().getY(), s.foot().getZ()));
            fittings.add(new Cell(s.hub().getX(), s.hub().getY(), s.hub().getZ()));
        }
        int[] partner = RatlineNetRules.sheetPartners(feet, hubRings, joinable, maxSpread);
        Map<BlockPos, Placement> cells = new LinkedHashMap<>();
        for (RatlineNetRules.SheetCell c : RatlineNetRules.sheetCells(feet, hubRings, strandCells, partner, fittings)) {
            BlockPos hub2 = c.otherHub() < 0 ? null : strands.get(c.otherHub()).hub();
            cells.put(new BlockPos(c.cell().x(), c.cell().y(), c.cell().z()), new Placement(strands.get(c.owner()), c.fill(), hub2));
        }
        return new Sheet(hubs, strands, partner, cells);
    }

    /** {@code strands} of one hub in RL4's fan order round it ({@link RatlineNetRules#fanOrder}). */
    private static List<Strand> bearingOrder(List<Strand> strands) {
        if (strands.size() < 2) {
            return strands;
        }
        List<Point> feet = new ArrayList<>();
        for (Strand s : strands) {
            feet.add(s.footRing());
        }
        List<Strand> out = new ArrayList<>();
        for (int i : RatlineNetRules.fanOrder(strands.get(0).hubRing(), feet)) {
            out.add(strands.get(i));
        }
        return out;
    }

    /** The sheet of {@code hub}'s group as it stands. */
    public static Sheet sheetAt(BlockGetter level, BlockPos hub) {
        return sheet(level, level::getBlockState, group(level, level::getBlockState, hub), null, maxSpread());
    }

    /**
     * The fan at {@code hub} as it stands: its strands in stored (bearing) order, whether each owns a pair, and every
     * cell of its group ({@link #sheetAt}).
     */
    public static Fan fanAt(BlockGetter level, BlockPos hub) {
        Sheet sheet = sheetAt(level, hub);
        List<Strand> own = hubStrands(level, hub);
        boolean[] join = new boolean[own.size()];
        for (int k = 0; k < own.size(); k++) {
            int i = sheet.strands().indexOf(own.get(k));
            join[k] = i >= 0 && sheet.partner()[i] >= 0;
        }
        return new Fan(hub, own, join, sheet.cells());
    }

    // ------------------------------------------------------------------ stringing

    /**
     * Whether a net may run from the fitting at {@code a} to the one at {@code b}: one body, a chainplate or latch holds
     * one net and a hoop up to {@code rigging.net_max_strands}, the line's length and pitch ({@link RatlineNetRules#check}),
     * then every cell of the strand and of its group's new fill air, water or already a cell of that group.
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
        // RAIL5: two chainplates on top of railings at one height hang a boarding net along the rail instead
        BoardingNetRules.Layout boarding = RiggingConfig.BOARDING_NETS.get()
                ? BoardingNets.layout(level, a, b, RiggingConfig.BOARDING_NET_LENGTH.get()) : null;
        if (boarding != null) {
            switch (boarding.verdict()) {
                case NOT_ALONG -> {
                    return new Check(Refusal.NOT_ALONG, null, net, Double.NaN, true);
                }
                case TOO_SHORT -> {
                    return new Check(Refusal.TOO_SHORT, null, net, Double.NaN, true);
                }
                case TOO_LONG -> {
                    return new Check(Refusal.TOO_LONG, null, net, Double.NaN, true);
                }
                default -> {
                }
            }
        } else {
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
        }
        Strand added = strand(level, net);
        BlockPos hub = added.hub();
        Set<BlockPos> hubs = group(level, level::getBlockState, hub);
        Sheet sheet = sheet(level, level::getBlockState, hubs, added, maxSpread());
        for (Cell c : net.cells()) {
            BlockPos p = new BlockPos(c.x(), c.y(), c.z());
            if (!acceptable(level, p, hubs)) {
                return new Check(Refusal.BLOCKED, p, net, Double.NaN, boarding != null);
            }
        }
        for (Map.Entry<BlockPos, Placement> e : sheet.cells().entrySet()) {
            BlockPos p = e.getKey();
            if (!acceptable(level, p, hubs) && !(e.getValue().fill() && skipsFill(level.getBlockState(p)))) {
                return new Check(Refusal.BLOCKED, p, net, Double.NaN, boarding != null);
            }
        }
        int k = sheet.strands().indexOf(added);
        if (sheet.joined(k)) {
            return new Check(Refusal.NONE, null, net, Double.NaN, boarding != null);
        }
        // strung alone: how far its nearest neighbour on the same hub is, and whether that one is just not in a row
        double nearest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < sheet.strands().size(); i++) {
            if (i != k && sheet.strands().get(i).hub().equals(hub)) {
                nearest = Math.min(nearest, RatlineNetRules.spread(added.footRing(), sheet.strands().get(i).footRing()));
            }
        }
        if (Double.isInfinite(nearest)) {
            return new Check(Refusal.NONE, null, net, Double.NaN, boarding != null);
        }
        return new Check(Refusal.NONE, null, net, nearest, boarding != null, nearest <= maxSpread() + 1.0e-9);
    }

    private static boolean acceptable(ServerLevel level, BlockPos p, Set<BlockPos> hubs) {
        return free(level.getBlockState(p)) || ours(level, p, hubs);
    }

    /**
     * Whether the fitting at {@code pos} can take another net (to {@code other}, when given): a chainplate or a railing
     * latch holds one, a hoop up to {@code rigging.net_max_strands} ({@link RatlineNetRules#takesAnother}), and no two
     * nets join the same two fittings.
     */
    public static Refusal takesAnother(BlockGetter level, BlockPos pos, @Nullable BlockPos other) {
        if (!(level.getBlockEntity(pos) instanceof NetFittingBlockEntity be) || !be.hasNet()) {
            return Refusal.NONE;
        }
        if (other != null && be.holds(other)) {
            return Refusal.BUSY;
        }
        RatlineNetRules.Anchor anchor = anchor(level.getBlockState(pos));
        if (RatlineNetRules.takesAnother(anchor, be.netCount(), RiggingConfig.NET_MAX_STRANDS.get())) {
            return Refusal.NONE;
        }
        return anchor == RatlineNetRules.Anchor.HOOP ? Refusal.FULL : Refusal.BUSY;
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

    /** Server: strings {@code net} (checked by {@link #check}): both fittings' block entities, then its group's cells. */
    public static void string(ServerLevel level, Net net) {
        Strand added = strand(level, net);
        BlockPos hub = added.hub();
        Set<BlockPos> before = group(level, level::getBlockState, hub);
        Set<BlockPos> stale = new HashSet<>(sheet(level, level::getBlockState, before, null, maxSpread()).cells().keySet());
        if (level.getBlockEntity(net.a()) instanceof NetFittingBlockEntity fa) {
            fa.setNet(net.b(), true);
        }
        if (level.getBlockEntity(net.b()) instanceof NetFittingBlockEntity fb) {
            fb.setNet(net.a(), false);
        }
        Set<BlockPos> hubs = new java.util.TreeSet<>(before);
        hubs.addAll(group(level, level::getBlockState, hub));
        sync(level, level::getBlockState, hubs, stale, Set.of());
    }

    // ------------------------------------------------------------------ keeping a fan's cells in line

    /**
     * Server: brings the cells of the group of {@code hubs} in line with its strands: stores each hub's strands in
     * bearing order ({@link RatlineNetRules#fanOrder}) with their pairs, places or re-owns every cell the group wants
     * (unless in {@code skip}), and removes every former cell of it in {@code stale} it no longer wants. A wanted strand
     * cell taken by another block breaks that strand; a wanted fill cell taken is left out.
     */
    static void sync(ServerLevel level, Function<BlockPos, BlockState> states, Set<BlockPos> hubs, Collection<BlockPos> stale,
                     Set<BlockPos> skip) {
        Sheet fan = sheet(level, states, hubs, null, maxSpread());
        storePairs(level, fan);
        List<Strand> broken = new ArrayList<>();
        BlockState cellState = RiggingContent.NET_CELL.get().defaultBlockState();
        // RAIL5: a boarding net's cells face its outboard side (read from the rails once per strand)
        Map<Strand, Optional<Direction>> outboard = new HashMap<>();
        syncing++;
        try {
            for (Map.Entry<BlockPos, Placement> e : fan.cells().entrySet()) {
                BlockPos p = e.getKey();
                Placement pl = e.getValue();
                if (skip.contains(p)) {
                    continue;
                }
                if (!ours(level, p, hubs)) {
                    if (!free(level.getBlockState(p))) {
                        if (!pl.fill() && !broken.contains(pl.owner())) {
                            broken.add(pl.owner());
                        }
                        continue;
                    }
                    boolean water = level.getFluidState(p).getType() == Fluids.WATER;
                    BlockState place = cellState.setValue(RatlineNetCellBlock.WATERLOGGED, water);
                    Direction out = outboard.computeIfAbsent(pl.owner(), s -> Optional.ofNullable(BoardingNets.outboard(level, s.net())))
                            .orElse(null);
                    if (out != null) {
                        place = place.setValue(RatlineNetCellBlock.BOARDING, true).setValue(RatlineNetCellBlock.FACING, out);
                    }
                    level.setBlock(p, place, Block.UPDATE_ALL);
                }
                if (level.getBlockEntity(p) instanceof RatlineNetCellBlockEntity cell) {
                    Net n = pl.owner().net();
                    cell.setOwner(n.a(), n.b(), pl.owner().hub(), pl.fill(), pl.hub2());
                }
            }
            for (BlockPos p : stale) {
                if (fan.cells().containsKey(p) || skip.contains(p)) {
                    continue;
                }
                BlockState s = level.getBlockState(p);
                if (s.is(RiggingContent.NET_CELL.get())) {
                    BlockPos named = cellHub(level, p);
                    if (named == null || hubs.contains(named)) {
                        level.setBlock(p, Waterlogging.leftBehind(s), Block.UPDATE_ALL);
                    }
                }
            }
        } finally {
            syncing--;
        }
        if (!broken.isEmpty()) {
            removeStrands(level, states, broken, true, skip);
        }
    }

    /** Stores in every hub of {@code sheet} its strands in bearing order with the pairs they own and are in. */
    private static void storePairs(ServerLevel level, Sheet sheet) {
        for (BlockPos h : sheet.hubs()) {
            if (!(level.getBlockEntity(h) instanceof NetFittingBlockEntity be)) {
                continue;
            }
            List<Integer> mine = new ArrayList<>();
            List<Point> feet = new ArrayList<>();
            for (int k = 0; k < sheet.strands().size(); k++) {
                if (sheet.strands().get(k).hub().equals(h)) {
                    mine.add(k);
                    feet.add(sheet.strands().get(k).footRing());
                }
            }
            if (mine.isEmpty()) {
                be.setFan(List.of(), List.of(), new boolean[0]);
                continue;
            }
            int[] order = RatlineNetRules.fanOrder(sheet.strands().get(mine.get(0)).hubRing(), feet);
            List<BlockPos> feetOrder = new ArrayList<>();
            List<BlockPos> partners = new ArrayList<>();
            boolean[] joined = new boolean[order.length];
            for (int i = 0; i < order.length; i++) {
                int k = mine.get(order[i]);
                feetOrder.add(sheet.strands().get(k).foot());
                int p = sheet.partner()[k];
                partners.add(p < 0 ? null : sheet.strands().get(p).foot());
                for (int q : sheet.partner()) {
                    joined[i] |= q == k;
                }
            }
            be.setFan(feetOrder, partners, joined);
        }
    }

    /**
     * Server: takes the {@code gone} strands down: both fittings forget each, their groups re-join without them
     * ({@link #sync}, leaving {@code skip} alone: blocks being replaced right now), a railing latch left without a strand
     * goes ({@link #freeLatches}) and, with {@code drop}, each one's net item drops at its foot. When something was
     * skipped the hubs check their nets again next tick.
     */
    static void removeStrands(ServerLevel level, Function<BlockPos, BlockState> states, List<Strand> gone,
                              boolean drop, Set<BlockPos> skip) {
        Set<BlockPos> hubs = new java.util.TreeSet<>();
        for (Strand s : gone) {
            hubs.addAll(group(level, states, s.hub()));
        }
        Set<BlockPos> stale = new HashSet<>(sheet(level, states, hubs, null, maxSpread()).cells().keySet());
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
        sync(level, states, hubs, stale, skip);
        if (drop) {
            for (Strand s : gone) {
                Block.popResource(level, s.foot(), new ItemStack(RiggingContent.NET_ITEM.get()));
            }
        }
        freeLatches(level, gone, skip);
        if (!skip.isEmpty()) {
            for (BlockPos hub : hubs) {
                BlockState h = level.getBlockState(hub);
                if (!skip.contains(hub) && h.getBlock() instanceof NetFitting) {
                    level.scheduleTick(hub, h.getBlock(), 1);
                }
            }
        }
    }

    /**
     * RL5: no orphan latches: every railing latch end of the {@code gone} strands that holds no strand any more (and is
     * not being replaced right now, {@code skip}) goes, and with it the railing's eye bolt.
     */
    private static void freeLatches(ServerLevel level, List<Strand> gone, Set<BlockPos> skip) {
        for (Strand s : gone) {
            for (BlockPos end : new BlockPos[] {s.net().a(), s.net().b()}) {
                BlockState st = level.getBlockState(end);
                if (!skip.contains(end) && st.getBlock() instanceof RailingLatchBlock
                        && !(level.getBlockEntity(end) instanceof NetFittingBlockEntity be && be.hasNet())) {
                    level.setBlock(end, Waterlogging.leftBehind(st), Block.UPDATE_ALL);
                }
            }
        }
    }

    /** A fitting's scheduled tick: its fan's cells brought in line again (fills a hole left by a block that was replaced). */
    static void resync(ServerLevel level, BlockPos hub) {
        if (!hubStrands(level, hub).isEmpty()) {
            sync(level, level::getBlockState, group(level, level::getBlockState, hub), List.of(), Set.of());
        }
    }

    /**
     * Server: takes {@code net} down: both fittings forget it, its cells and the fill it owned go (their water stays),
     * the rest of its fan re-fans and, with {@code drop}, one net item drops at its foot.
     */
    public static void breakNet(ServerLevel level, Net net, boolean drop) {
        Strand s = strand(level, net);
        removeStrands(level, level::getBlockState, List.of(s), drop, Set.of());
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
        List<Strand> gone = new ArrayList<>();
        for (Net n : netsOf(level, states, at)) {
            gone.add(strand(states, n));
        }
        removeStrands(server, states, gone, true, Set.of(at));
        be.clearNet();
    }

    /** From a cell's {@code onRemove} (before its block entity goes): the strand that owns the cell breaks. */
    static void onCellRemoved(Level level, BlockPos pos, boolean movedByPiston, BlockState newState) {
        if (syncing > 0 || movedByPiston || newState.is(RiggingContent.NET_CELL.get()) || !(level instanceof ServerLevel server)) {
            return;
        }
        Strand s = ownerStrand(level, pos);
        if (s != null) {
            removeStrands(server, level::getBlockState, List.of(s), true, Set.of(pos.immutable()));
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
