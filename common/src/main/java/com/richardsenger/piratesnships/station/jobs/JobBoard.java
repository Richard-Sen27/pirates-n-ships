package com.richardsenger.piratesnships.station.jobs;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.law.brig.BrigService;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.ShipSplits;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationBlock;
import com.richardsenger.piratesnships.station.StationConfig;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.order.CrewOrder;
import com.richardsenger.piratesnships.station.winch.CaptainsWhistleItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The job board of each ship (CR1, docs/design.md §7.2 "Job board"): the captain gives orders, not assignments. A
 * ship-wide order ({@link #post}) turns every unmanned station of the ship that takes it, and has work to do for it,
 * into an <em>open job</em>; every {@code claim_interval_ticks} free crew on board claim the open jobs
 * ({@link JobBoardRules}), are seated at the station ({@link CrewStations#assign} without pin) and get the order
 * with their usual spoken acknowledgement. A claimed job leaves the board.
 *
 * <p>One board per ship, in memory only (lost on restart, like orders in progress), keyed by the ship's id. A station
 * holds at most one open job: a later order replaces it. The board is cleared when the ship is removed or
 * disassembled, when it splits, by the whistle's "Release crew", and dropped when the ship became a wreck.
 */
public final class JobBoard {

    static final String KEY = "message." + Constants.MOD_ID + ".jobs.";
    /** Action bar to the captain: "No free hands to %s" (the order name), once per order. */
    public static final String KEY_NO_FREE_HANDS = KEY + "no_free_hands";
    /** Action bar to the captain: "Order: %s (%s crew carry it out, %s open stations for free hands)". */
    public static final String KEY_ORDER_POSTED = KEY + "order_posted";
    /** Command feedback: "%s open jobs posted for: %s". */
    public static final String KEY_POSTED = KEY + "posted";

    /** One open job: the order and its sequence number (orders issued earlier have lower numbers). */
    public record Job(CrewOrder order, long seq) { }

    /**
     * What {@link #post} did: {@code jobs} stations became open jobs; {@code unclaimed} of them no crew on board could
     * take right now (none free, or all out of reach).
     */
    public record Posted(int jobs, int unclaimed) {
        public static final Posted NONE = new Posted(0, 0);

        /** The captain is told "no free hands" for this order. */
        public boolean noFreeHands() {
            return unclaimed > 0;
        }
    }

    private static final class Board {
        final ResourceKey<Level> dimension;
        final Map<BlockPos, Job> jobs = new LinkedHashMap<>();

        Board(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }
    }

    private static final Map<UUID, Board> BOARDS = new ConcurrentHashMap<>();
    private static final AtomicLong SEQ = new AtomicLong();

    private JobBoard() {
    }

    // ------------------------------------------------------------------ posting

    /**
     * Posts {@code order} on the board of {@code ship}: each unmanned station that takes it and has work for it now
     * ({@link Stations#workTicks} &gt; 0) becomes an open job, replacing an older job there. Manned stations are not
     * touched (the caller orders their crew directly). Does nothing when the board or the crew stations are off, or on
     * a wreck.
     */
    public static Posted post(ServerLevel level, ShipBody ship, CrewOrder order) {
        if (!StationConfig.ENABLED.get() || !StationConfig.JOB_BOARD_ENABLED.get() || ShipSplits.isWreck(ship)) {
            return Posted.NONE;
        }
        long seq = SEQ.incrementAndGet();
        List<BlockPos> posted = new ArrayList<>();
        for (StationRef ref : stationsOf(level, ship)) {
            if (!Stations.isManned(ref) && Stations.workTicks(level, ref, order) > 0) {
                posted.add(ref.pos());
            }
        }
        if (posted.isEmpty()) {
            return Posted.NONE;
        }
        Board board = BOARDS.computeIfAbsent(ship.id(), id -> new Board(level.dimension()));
        for (BlockPos p : posted) {
            board.jobs.remove(p); // re-inserted at the end: the board stays in order of issue
            board.jobs.put(p, new Job(order, seq));
        }
        // dry run of the next pass: which of the new jobs nobody on board can take
        List<JobBoardRules.Job<BlockPos>> jobs = describeJobs(ship, board);
        List<JobBoardRules.Claim<BlockPos>> claims = JobBoardRules.assign(jobs, describeHands(level, ship).hands(), maxDistance());
        Set<BlockPos> open = new LinkedHashSet<>(JobBoardRules.unclaimed(jobs, claims));
        int unclaimed = (int) posted.stream().filter(open::contains).count();
        return new Posted(posted.size(), unclaimed);
    }

    /** Clears the board of {@code ship} (the whistle's "Release crew", disassembly, removal, split). */
    public static void clear(UUID ship) {
        BOARDS.remove(ship);
    }

    /** The open jobs of {@code ship} by station plot position, in order of issue (a copy; empty when none). */
    public static Map<BlockPos, Job> jobs(UUID ship) {
        Board b = BOARDS.get(ship);
        return b == null ? Map.of() : new LinkedHashMap<>(b.jobs);
    }

    // ------------------------------------------------------------------ claiming

    /** Every {@code claim_interval_ticks}: free crew claim the open jobs of the boards of this level. */
    public static void onLevelTick(ServerLevel level) {
        if (BOARDS.isEmpty() || level.getGameTime() % StationConfig.CLAIM_INTERVAL_TICKS.get() != 0) {
            return;
        }
        for (Map.Entry<UUID, Board> e : new ArrayList<>(BOARDS.entrySet())) {
            if (e.getValue().dimension == level.dimension()) {
                pass(level, e.getKey(), e.getValue());
            }
        }
    }

    /** One board pass for {@code shipId}; public for the GameTests, which do not wait for the interval. */
    public static void pass(ServerLevel level, UUID shipId) {
        Board b = BOARDS.get(shipId);
        if (b != null) {
            pass(level, shipId, b);
        }
    }

    private static void pass(ServerLevel level, UUID shipId, Board board) {
        if (!StationConfig.ENABLED.get() || !StationConfig.JOB_BOARD_ENABLED.get()) {
            BOARDS.remove(shipId, board);
            return;
        }
        ShipBody ship = SableShips.byId(level, shipId);
        if (ship == null) {
            if (ShipRegistry.get(level.getServer()).find(shipId).isEmpty()) {
                BOARDS.remove(shipId, board); // disassembled or removed for good
            }
            return; // unloaded: the jobs wait
        }
        if (ShipSplits.isWreck(ship)) {
            BOARDS.remove(shipId, board);
            return;
        }
        // jobs whose station is gone, manned meanwhile, or no longer takes the order leave the board
        for (Iterator<Map.Entry<BlockPos, Job>> it = board.jobs.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, Job> j = it.next();
            StationRef ref = new StationRef(shipId, j.getKey());
            if (Stations.isManned(ref) || !Stations.accepts(level, ref, j.getValue().order())) {
                it.remove();
            }
        }
        if (board.jobs.isEmpty()) {
            BOARDS.remove(shipId, board);
            return;
        }
        Hands hands = describeHands(level, ship);
        List<JobBoardRules.Claim<BlockPos>> claims = JobBoardRules.assign(describeJobs(ship, board), hands.hands(), maxDistance());
        for (JobBoardRules.Claim<BlockPos> c : claims) {
            CrewMember crew = hands.crew().get(c.crew());
            Job job = board.jobs.get(c.job());
            if (crew == null || job == null) {
                continue;
            }
            CrewStations.AssignResult r = CrewStations.assign(level, crew, c.job(), false);
            if (r == CrewStations.AssignResult.ASSIGNED) {
                board.jobs.remove(c.job());
                CrewStations.order(level, crew, job.order());
            } else if (r != CrewStations.AssignResult.DISABLED) {
                board.jobs.remove(c.job()); // taken meanwhile, or no station any more
            }
        }
        if (board.jobs.isEmpty()) {
            BOARDS.remove(shipId, board);
        }
    }

    // ------------------------------------------------------------------ life cycle

    /** A ship destroyed for good (removed, disassembled) takes its board along. */
    public static void onShipRemoved(ServerLevel level, UUID ship, boolean destroyed) {
        if (destroyed) {
            BOARDS.remove(ship);
        }
    }

    /** A split: the stations moved or broke off; the board of the ship and of its keeper starts over. */
    public static void onSplit(ShipSplits.SplitEvent event) {
        BOARDS.remove(event.parent());
        BOARDS.remove(event.keeper());
    }

    public static void onServerStopped() {
        BOARDS.clear();
    }

    // ------------------------------------------------------------------ world → rules

    private record Hands(List<JobBoardRules.Hand> hands, Map<UUID, CrewMember> crew) { }

    /** The stations of {@code ship}, each once (a multi-block station at its master). */
    private static List<StationRef> stationsOf(ServerLevel level, ShipBody ship) {
        Set<StationRef> out = new LinkedHashSet<>();
        for (BlockPos p : ship.plotBlocks()) {
            if (level.getBlockState(p).getBlock() instanceof StationBlock) {
                StationRef ref = Stations.at(level, p);
                if (ref != null && ref.ship().equals(ship.id())) out.add(ref);
            }
        }
        return new ArrayList<>(out);
    }

    private static List<JobBoardRules.Job<BlockPos>> describeJobs(ShipBody ship, Board board) {
        List<JobBoardRules.Job<BlockPos>> out = new ArrayList<>();
        for (Map.Entry<BlockPos, Job> e : board.jobs.entrySet()) {
            Vec3 w = ship.toWorld(Vec3.atCenterOf(e.getKey()));
            out.add(new JobBoardRules.Job<>(e.getKey(), w.x, w.y, w.z, e.getValue().seq()));
        }
        return out;
    }

    /**
     * The crew on board of {@code ship}: free (on the ship, not at a station, not a prisoner) or at one of its
     * stations; the pinned and the dead are left out or marked so the rules never move them.
     */
    private static Hands describeHands(ServerLevel level, ShipBody ship) {
        List<JobBoardRules.Hand> hands = new ArrayList<>();
        Map<UUID, CrewMember> crew = new HashMap<>();
        for (CrewMember c : level.getEntitiesOfClass(CrewMember.class, CrewStations.worldBox(ship, 4), CrewMember::isAlive)) {
            JobBoardRules.Status status = status(level, ship, c);
            if (status == null) continue;
            hands.add(new JobBoardRules.Hand(c.getUUID(), c.getX(), c.getY(), c.getZ(), status));
            crew.put(c.getUUID(), c);
        }
        return new Hands(hands, crew);
    }

    private static JobBoardRules.Status status(ServerLevel level, ShipBody ship, CrewMember c) {
        if (BrigService.isPrisoner(c)) {
            return null;
        }
        StationRef a = c.assignment();
        if (a == null) {
            ShipBody on = CaptainsWhistleItem.shipOf(level, c);
            return on != null && on.id().equals(ship.id()) ? JobBoardRules.Status.FREE : null;
        }
        if (!a.ship().equals(ship.id())) {
            return null;
        }
        if (c.isPinned()) {
            return JobBoardRules.Status.PINNED;
        }
        StationState<Object> st = Stations.state(a);
        return st != null && st.phase() == StationState.Phase.OPERATING ? JobBoardRules.Status.BOARD_BUSY : JobBoardRules.Status.BOARD_IDLE;
    }

    private static double maxDistance() {
        return StationConfig.MAX_CLAIM_DISTANCE.get();
    }
}
