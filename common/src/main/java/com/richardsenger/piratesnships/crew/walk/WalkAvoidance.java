package com.richardsenger.piratesnships.crew.walk;

import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Walking round people (WALK2, {@code crew.walk.avoids_crew}): every tick of a walk {@link CrewWalk} asks
 * {@link #tick} first; while it answers true the walker is steered here and WALK1's path upkeep waits.
 * <ul>
 *   <li><b>Occupants</b>: crew members, villagers and players on the same ship within a few blocks; a seated one (a
 *       station, meal, oar or bench seat) by its seat's cell (seats live in the ship's plot, their riders in world
 *       space, {@code StationSeat}); everyone else by the cell of its feet, turned into the plot.</li>
 *   <li>When one of the next {@link WalkAvoid#LOOKAHEAD} cells of the walker's path is held, the walker
 *       <b>sidesteps</b>: it walks the {@link WalkAvoid#detour} cell by cell (one short path per cell, so the
 *       pathfinder, which knows nothing of entities, cannot lead it back through the held cell), then looks for a
 *       fresh path to its spot. No way round: it <b>waits</b> ({@code crew.walk.wait_ticks}, or until the cells ahead
 *       are clear) and tries again.</li>
 *   <li>Another <b>walker</b> in the way waits it out, unless the two meet <b>head-on</b> (each on the other's way):
 *       then the one with the lower entity id yields ({@link WalkAvoid#refuge}, aside or back along its way) and stays
 *       there until the other has passed, the other holds.</li>
 * </ul>
 * The state lives on the crew member ({@link CrewMember#walkAvoid()}), not saved: a reload starts the walk's steering
 * afresh. No Sable call beyond {@link ShipBody#toPlot} and {@link ShipEntities}: paths are created with plot targets as
 * WALK1 does ({@code PathNavigationMixin#sable$createPath}), and a path's raw nodes stay in plot coordinates (only
 * {@code getNextEntityPos}, {@code getNextNodePos} and {@code getNodePos} are projected, {@code PathMixin}).
 */
public final class WalkAvoidance {

    /** Blocks round the walker searched for occupants (world space). */
    static final double SCAN = 3.5;
    /** Most ticks a sidestep may take per cell before it is given up (plus a fixed allowance). */
    static final int TICKS_PER_CELL = 10;
    /** A yielding walker waits in its refuge at most this many times {@code wait_ticks} for the other to pass. */
    static final int REFUGE_WAITS = 4;
    /** Most path cells of another walker's way looked at for a head-on meeting. */
    static final int WAY_CELLS = 16;

    /** What the walker does while it steers round someone. */
    public enum Mode {
        /** Walks a sidestep (or to its refuge), cell by cell. */
        SIDESTEP,
        /** Stands and waits for the cells ahead to clear. */
        WAIT,
        /** Yielded: stands off the other's way until it has passed. */
        REFUGE
    }

    /** The steering state of one walk. */
    public static final class State {
        @Nullable Mode mode;
        final List<BlockPos> waypoints = new ArrayList<>();
        int sidestepCells;
        @Nullable BlockPos leg;
        int legFails;
        long since;
        /** The cells ahead that were held when it stopped (WAIT). */
        List<BlockPos> heldAhead = List.of();
        /** Its way ahead when it stopped (for the head-on test of others while it stands). */
        List<BlockPos> way = List.of();
        /** The walker it yielded to (REFUGE, or a SIDESTEP towards the refuge), else -1. */
        int yieldedTo = -1;
        long sidestepTicks;
        /** Counters for tests and the debug log. */
        int sidesteps;
        int waits;
        int yields;

        public @Nullable Mode mode() {
            return mode;
        }

        public long sidestepTicks() {
            return sidestepTicks;
        }

        public int sidesteps() {
            return sidesteps;
        }

        public int waits() {
            return waits;
        }

        public int yields() {
            return yields;
        }
    }

    private WalkAvoidance() {
    }

    /** The walker's steering state, created on first use. */
    static State state(CrewMember crew) {
        State s = crew.walkAvoid();
        if (s == null) {
            s = new State();
            crew.setWalkAvoid(s);
        }
        return s;
    }

    /**
     * One tick of a walk on the deck (on the ground, on its ship). True: the walker is steered round someone this tick
     * and WALK1's path upkeep must not touch its navigation.
     */
    static boolean tick(ServerLevel level, CrewMember crew, ShipBody ship, WalkTarget t) {
        State s = state(crew);
        long now = level.getGameTime();
        if (s.mode != null) {
            switch (s.mode) {
                case SIDESTEP -> sidestep(level, crew, ship, t, s, now);
                case WAIT -> waitTick(level, crew, ship, t, s, now);
                case REFUGE -> refugeTick(level, crew, ship, t, s, now);
            }
            return true;
        }
        PathNavigation nav = crew.getNavigation();
        Path path = nav.getPath();
        if (path == null || path.isDone()) {
            return false;
        }
        Map<BlockPos, Entity> occupants = occupants(level, crew, ship);
        if (occupants.isEmpty()) {
            return false;
        }
        BlockPos self = cell(ship, crew.position());
        List<BlockPos> nodes = nodes(path, 0);
        List<BlockPos> ahead = WalkAvoid.ahead(nodes, path.getNextNodeIndex(), self);
        int i = WalkAvoid.firstBlocked(ahead, occupants.keySet());
        if (i < 0) {
            return false;
        }
        BlockPos held = ahead.get(i);
        BlockPos holderCell = WalkAvoid.holder(occupants.keySet(), held);
        Entity holder = holderCell == null ? null : occupants.get(holderCell);
        List<BlockPos> myWay = nodes(path, path.getNextNodeIndex());
        if (holder instanceof CrewMember other && other.walk() != null && !other.isPassenger()) {
            List<BlockPos> otherWay = way(other);
            WalkAvoid.Meeting m = WalkAvoid.meeting(crew.getId(), other.getId(), true, WalkAvoid.onWay(self, otherWay));
            if (m == WalkAvoid.Meeting.YIELD) {
                List<BlockPos> r = WalkAvoid.refuge(self, holderCell, otherWay, walkable(level, crew),
                        without(occupants, holderCell));
                if (r != null) {
                    s.yields++;
                    s.yieldedTo = other.getId();
                    if (r.isEmpty()) {
                        enterRefuge(crew, s, now);
                    } else {
                        startSidestep(level, crew, s, r, now);
                    }
                    return true;
                }
            }
            State o = other.walkAvoid();
            boolean otherStands = (o != null && o.mode != null && o.mode != Mode.SIDESTEP) || other.getNavigation().isDone();
            if (m != WalkAvoid.Meeting.NONE || !otherStands) {
                startWait(crew, s, ahead, myWay, now); // holds for the yielder, or follows a walker that goes on
                return true;
            }
        }
        int heldAt = myWay.indexOf(held);
        List<BlockPos> rejoin = new ArrayList<>(myWay.subList(heldAt + 1, myWay.size()));
        rejoin.add(t.spot());
        List<BlockPos> d = WalkAvoid.detour(self, held, rejoin, walkable(level, crew), occupants.keySet());
        if (d != null) {
            s.sidesteps++;
            startSidestep(level, crew, s, d, now);
        } else {
            startWait(crew, s, ahead, myWay, now);
        }
        return true;
    }

    /** The walk ended (arrived, timed out, cancelled): its steering goes too. */
    static void clear(CrewMember crew) {
        crew.setWalkAvoid(null);
    }

    // ------------------------------------------------------------------ modes

    private static void startSidestep(ServerLevel level, CrewMember crew, State s, List<BlockPos> cells, long now) {
        s.mode = Mode.SIDESTEP;
        s.waypoints.clear();
        s.waypoints.addAll(cells);
        s.sidestepCells = cells.size();
        s.leg = null;
        s.legFails = 0;
        s.since = now;
        s.way = List.copyOf(cells);
        crew.getNavigation().stop();
    }

    private static void startWait(CrewMember crew, State s, List<BlockPos> ahead, List<BlockPos> way, long now) {
        s.mode = Mode.WAIT;
        s.waits++;
        s.since = now;
        s.heldAhead = List.copyOf(ahead);
        s.way = List.copyOf(way.subList(0, Math.min(way.size(), WAY_CELLS)));
        crew.getNavigation().stop();
    }

    private static void enterRefuge(CrewMember crew, State s, long now) {
        s.mode = Mode.REFUGE;
        s.since = now;
        s.way = List.of();
        crew.getNavigation().stop();
    }

    /** Back to WALK1's walking: a fresh path to the spot. */
    private static void resume(CrewMember crew, State s, WalkTarget t) {
        s.mode = null;
        s.waypoints.clear();
        s.leg = null;
        s.yieldedTo = -1;
        s.way = List.of();
        s.heldAhead = List.of();
        CrewWalk.path(crew, t.spot());
    }

    private static void sidestep(ServerLevel level, CrewMember crew, ShipBody ship, WalkTarget t, State s, long now) {
        s.sidestepTicks++;
        if (now - s.since > 20 + (long) TICKS_PER_CELL * s.sidestepCells || s.legFails > 2) {
            resume(crew, s, t); // it got nowhere: a fresh path, and a fresh look at who stands in it
            return;
        }
        Vec3 local = ship.toPlot(crew.position());
        PathNavigation nav = crew.getNavigation();
        while (!s.waypoints.isEmpty() && reached(local, s.waypoints.get(0), nav.isDone() && s.waypoints.get(0).equals(s.leg))) {
            s.waypoints.remove(0);
            s.leg = null;
        }
        if (s.waypoints.isEmpty()) {
            if (s.yieldedTo >= 0) {
                enterRefuge(crew, s, now);
            } else {
                resume(crew, s, t);
            }
            return;
        }
        BlockPos next = s.waypoints.get(0);
        Map<BlockPos, Entity> occupants = occupants(level, crew, ship);
        if (WalkAvoid.occupied(occupants.keySet(), next)) {
            resume(crew, s, t); // someone stepped into the sidestep
            return;
        }
        s.way = List.copyOf(s.waypoints);
        if (!next.equals(s.leg) || nav.isDone()) {
            s.leg = next;
            if (CrewWalk.path(crew, next, 0) == null) {
                s.legFails++;
            }
        }
    }

    private static void waitTick(ServerLevel level, CrewMember crew, ShipBody ship, WalkTarget t, State s, long now) {
        crew.getNavigation().stop();
        boolean clear = WalkAvoid.firstBlocked(s.heldAhead, occupants(level, crew, ship).keySet()) < 0;
        if (WalkAvoid.waitOver(s.since, now, WalkConfig.WAIT_TICKS.get(), clear)) {
            resume(crew, s, t);
        }
    }

    private static void refugeTick(ServerLevel level, CrewMember crew, ShipBody ship, WalkTarget t, State s, long now) {
        crew.getNavigation().stop();
        Entity e = level.getEntity(s.yieldedTo);
        boolean gone = !(e instanceof CrewMember other) || other.isRemoved() || other.walk() == null || other.isPassenger();
        boolean passed = gone || WalkAvoid.passed(cell(ship, crew.position()), way((CrewMember) e));
        if (passed || now - s.since >= (long) REFUGE_WAITS * WalkConfig.WAIT_TICKS.get()) {
            resume(crew, s, t);
        }
    }

    // ------------------------------------------------------------------ the world

    /**
     * Who holds which plot cell near the walker: crew members, villagers and players (not spectators) on the same
     * ship; a rider by its vehicle's cell.
     */
    static Map<BlockPos, Entity> occupants(ServerLevel level, CrewMember crew, ShipBody ship) {
        Map<BlockPos, Entity> out = new LinkedHashMap<>();
        for (Entity e : level.getEntities(crew, crew.getBoundingBox().inflate(SCAN), WalkAvoidance::isPerson)) {
            ShipBody on = ShipEntities.standingOrRiding(e);
            if (on == null || !on.id().equals(ship.id())) {
                continue;
            }
            Entity vehicle = e.getVehicle();
            Vec3 plot;
            if (vehicle != null) {
                ShipBody in = ShipEntities.containing(vehicle);
                plot = in != null && in.id().equals(ship.id()) ? vehicle.position() : ship.toPlot(vehicle.position());
            } else {
                plot = ship.toPlot(e.position());
            }
            out.putIfAbsent(cell(plot), e);
        }
        return out;
    }

    private static boolean isPerson(Entity e) {
        if (!e.isAlive()) return false;
        if (e instanceof Player p) return !p.isSpectator();
        return e instanceof CrewMember || e instanceof AbstractVillager;
    }

    /** Another walker's way ahead (plot cells): its path's remaining nodes, or the way it stopped on. */
    static List<BlockPos> way(CrewMember other) {
        State s = other.walkAvoid();
        if (s != null && s.mode != null) {
            return s.way;
        }
        Path p = other.getNavigation().getPath();
        if (p == null || p.isDone()) {
            return List.of();
        }
        List<BlockPos> all = nodes(p, p.getNextNodeIndex());
        return all.subList(0, Math.min(all.size(), WAY_CELLS));
    }

    /** The raw nodes of {@code path} from {@code from} on: plot cells for a path on a ship ({@code PathMixin}). */
    private static List<BlockPos> nodes(Path path, int from) {
        List<BlockPos> out = new ArrayList<>();
        for (int i = Math.max(0, from); i < path.getNodeCount(); i++) {
            out.add(path.getNode(i).asBlockPos());
        }
        return out;
    }

    /** The plot cell of a plot position: the cell its feet stand in. */
    static BlockPos cell(Vec3 plot) {
        return BlockPos.containing(plot.x, plot.y + 0.25, plot.z);
    }

    private static BlockPos cell(ShipBody ship, Vec3 world) {
        return cell(ship.toPlot(world));
    }

    /** Whether the walker stands in {@code cell}: near its centre, or anywhere in it once the leg's path is done. */
    private static boolean reached(Vec3 local, BlockPos cell, boolean legDone) {
        double r = legDone ? 0.7 : 0.4;
        return Math.abs(local.x - (cell.getX() + 0.5)) < r && Math.abs(local.z - (cell.getZ() + 0.5)) < r
                && Math.abs(local.y - cell.getY()) < 1.0;
    }

    private static List<BlockPos> without(Map<BlockPos, Entity> occupants, @Nullable BlockPos cell) {
        List<BlockPos> out = new ArrayList<>(occupants.keySet());
        out.remove(cell);
        return out;
    }

    /**
     * A cell the walker may stand in, by vanilla's own walk rules (the plot's blocks are in the level): a floor under it,
     * room for its head, nothing it avoids (closed doors, fences, a cannon, {@code isPathfindable} false).
     */
    static Predicate<BlockPos> walkable(ServerLevel level, CrewMember crew) {
        PathfindingContext ctx = new PathfindingContext(level, crew);
        return c -> {
            PathType feet = WalkNodeEvaluator.getPathTypeStatic(ctx, c.mutable());
            if (feet == PathType.OPEN || feet == PathType.BLOCKED || crew.getPathfindingMalus(feet) < 0) {
                return false;
            }
            PathType head = WalkNodeEvaluator.getPathTypeStatic(ctx, c.above().mutable());
            return head != PathType.BLOCKED && crew.getPathfindingMalus(head) >= 0;
        };
    }
}
