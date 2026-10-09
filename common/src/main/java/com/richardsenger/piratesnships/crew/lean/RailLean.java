package com.richardsenger.piratesnships.crew.lean;

import com.richardsenger.piratesnships.crew.lean.RailSpots.Candidate;
import com.richardsenger.piratesnships.crew.lean.RailSpots.Spot;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.walk.WalkConfig;
import com.richardsenger.piratesnships.law.brig.BrigService;
import com.richardsenger.piratesnships.ship.decor.railing.RailingBlock;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import com.richardsenger.piratesnships.station.StationBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Idle crew leaning on the rail (RAIL5, docs/design.md §4.8 "Railing extras"). An idle crew member (no order, no
 * station, not eating, sleeping, walking anywhere or held prisoner) standing on a ship with a railing within
 * {@code crew.rail_lean.radius} claims a spot ({@link RailSpots}), walks to the deck cell inboard of it over Sable's
 * plot-space pathfinding (as WALK1's {@code CrewWalk} does: targets are plot cells, sable-notes §6), steps straight
 * into the railing's cell, turns to face outboard ({@code CrewMember}'s ST5 facing) and leans for a random
 * {@code min_seconds}..{@code max_seconds}; then it strolls again and pauses {@code pause_seconds} before the next lean.
 * At most {@code leaners} per ship, a cell apart. Anything that makes it not idle (an order, a meal, the hammock) ends
 * the lean at once ({@link #check}).
 *
 * <p>The spots taken live in memory per ship, like station jobs; they are checked against the crew members holding them
 * whenever a ship's spots are read, so an unloaded or removed leaner never blocks a spot.
 */
public final class RailLean {

    /** A lean in progress: the ship, the spot, whether it has arrived and leans, and its times (game time). */
    public static final class Lean {
        private final UUID ship;
        private final Spot spot;
        private final long claimed;
        private boolean leaning;
        private long started;
        private long until;
        private long lastPath = Long.MIN_VALUE / 4;

        Lean(UUID ship, Spot spot, long claimed) {
            this.ship = ship;
            this.spot = spot;
            this.claimed = claimed;
        }

        public UUID ship() {
            return ship;
        }

        public Spot spot() {
            return spot;
        }

        /** Whether it has reached the spot and leans on the rail. */
        public boolean leaning() {
            return leaning;
        }

        /** Game time the lean on the rail began (valid while {@link #leaning()}). */
        public long started() {
            return started;
        }

        /** Game time the lean ends. */
        public long until() {
            return until;
        }
    }

    /** Spots taken per ship: crew member to its spot's cell. Server thread only. */
    private static final Map<UUID, Map<UUID, BlockPos>> TAKEN = new HashMap<>();

    private RailLean() {
    }

    /** Whether {@code crew} is idle enough to lean on the rail (see the class comment). */
    public static boolean idle(CrewMember crew) {
        return crew.isAlive() && crew.assignment() == null && crew.rest() == null && crew.walk() == null && !crew.isEating()
                && !crew.isResting() && !crew.isPassenger() && !crew.isStationary() && !BrigService.isPrisoner(crew);
    }

    /** The spots taken on {@code ship} now, after dropping every entry whose crew member no longer holds it. */
    public static Map<UUID, BlockPos> taken(ServerLevel level, UUID ship) {
        Map<UUID, BlockPos> m = TAKEN.computeIfAbsent(ship, k -> new HashMap<>());
        for (Iterator<Map.Entry<UUID, BlockPos>> it = m.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, BlockPos> e = it.next();
            if (!(level.getEntity(e.getKey()) instanceof CrewMember c) || c.lean() == null || !c.lean().ship().equals(ship)) {
                it.remove();
            }
        }
        return m;
    }

    /**
     * Server: claims a spot at the rail for {@code crew} and returns its lean (set on the crew member), or null: the rule
     * is off, it is not idle, not standing on a ship, still pausing after its last lean, the ship has its
     * {@code leaners}, or no usable spot is within reach.
     */
    public static @Nullable Lean claim(ServerLevel level, CrewMember crew) {
        if (!RailLeanConfig.ENABLED.get() || crew.lean() != null || !idle(crew) || !crew.onGround()
                || level.getGameTime() < crew.leanPauseUntil()) {
            return null;
        }
        ShipBody ship = ShipEntities.standingOrRiding(crew);
        if (ship == null) {
            return null;
        }
        Map<UUID, BlockPos> taken = taken(level, ship.id());
        if (!LeanRules.mayLean(taken.size(), RailLeanConfig.LEANERS.get())) {
            return null;
        }
        Vec3 feet = ship.toPlot(crew.position());
        int radius = RailLeanConfig.RADIUS.get();
        Spot spot = RailSpots.choose(candidates(level, BlockPos.containing(feet), radius), feet, radius, taken.values());
        if (spot == null) {
            return null;
        }
        Lean lean = new Lean(ship.id(), spot, level.getGameTime());
        taken.put(crew.getUUID(), spot.cell());
        crew.setLean(lean);
        return lean;
    }

    /** Every railed edge of every railing within {@code radius} (and a block up or down) of {@code centre}, read from the level. */
    static List<Candidate> candidates(ServerLevel level, BlockPos centre, int radius) {
        List<Candidate> out = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    p.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    BlockState s = level.getBlockState(p);
                    if (!(s.getBlock() instanceof RailingBlock)) {
                        continue;
                    }
                    BlockPos cell = p.immutable();
                    boolean floor = sturdyFloor(level, cell);
                    boolean headroom = clear(level, cell.above());
                    boolean nearStation = nearStation(level, cell);
                    for (Direction edge : RailingBlock.edges(s)) {
                        BlockPos in = cell.relative(edge.getOpposite());
                        boolean approach = clear(level, in) && clear(level, in.above()) && sturdyFloor(level, in);
                        out.add(new Candidate(cell, edge, floor, headroom, approach, nearStation));
                    }
                }
            }
        }
        return out;
    }

    private static boolean sturdyFloor(ServerLevel level, BlockPos cell) {
        BlockPos below = cell.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    private static boolean clear(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** A station block (helm, winch, gun, capstan, …) in the cell, beside it or one block up. */
    private static boolean nearStation(ServerLevel level, BlockPos cell) {
        for (BlockPos q : BlockPos.betweenClosed(cell.offset(-1, 0, -1), cell.offset(1, 1, 1))) {
            if (level.getBlockState(q).getBlock() instanceof StationBlock) {
                return true;
            }
        }
        return false;
    }

    /**
     * Server, every tick of the lean goal: walks to the spot (a path to the cell inboard of it, then straight in), leans
     * on arrival for a random {@code min_seconds}..{@code max_seconds}, and steps back onto the spot when pushed off it.
     * Returns false when the lean is over (its time is up, the ship is gone, or the spot was not reached within
     * {@code crew.walk.timeout_ticks}).
     */
    public static boolean tick(ServerLevel level, CrewMember crew) {
        Lean lean = crew.lean();
        if (lean == null) {
            return false;
        }
        ShipBody ship = SableShips.byId(level, lean.ship);
        if (ship == null) {
            return false;
        }
        long now = level.getGameTime();
        Vec3 target = RailSpots.leanPoint(lean.spot);
        Vec3 feet = ship.toPlot(crew.position());
        double d = Math.hypot(feet.x - target.x, feet.z - target.z);
        PathNavigation nav = crew.getNavigation();
        double speed = WalkConfig.SPEED.get();
        if (!lean.leaning) {
            if (now - lean.claimed > WalkConfig.TIMEOUT_TICKS.get()) {
                return false;
            }
            if (d <= LeanRules.ARRIVE) {
                lean.leaning = true;
                lean.started = now;
                lean.until = now + LeanRules.durationTicks(RailLeanConfig.MIN_SECONDS.get(), RailLeanConfig.MAX_SECONDS.get(),
                        crew.getRandom().nextDouble());
                nav.stop();
                crew.setLeaning(true);
                return true;
            }
            if (d <= LeanRules.APPROACH) {
                nav.stop();
                Vec3 w = ship.toWorld(target);
                crew.getMoveControl().setWantedPosition(w.x, w.y, w.z, speed * 0.6);
                return true;
            }
            if (nav.isDone() && now - lean.lastPath >= 10) {
                lean.lastPath = now;
                Path p = nav.createPath(RailSpots.approachCell(lean.spot), 0);
                if (p != null) {
                    nav.moveTo(p, speed);
                }
            }
            return true;
        }
        if (now >= lean.until) {
            return false;
        }
        if (d > LeanRules.DRIFT) {
            Vec3 w = ship.toWorld(target);
            crew.getMoveControl().setWantedPosition(w.x, w.y, w.z, speed * 0.6);
        }
        return true;
    }

    /**
     * Server: ends the lean of {@code crew} (its spot freed, the pose dropped) and makes it pause
     * {@code pause_seconds} before the next one. A walk that an order started meanwhile keeps its path.
     */
    public static void end(ServerLevel level, CrewMember crew) {
        Lean lean = crew.lean();
        if (lean == null) {
            return;
        }
        Map<UUID, BlockPos> m = TAKEN.get(lean.ship);
        if (m != null) {
            m.remove(crew.getUUID());
        }
        crew.setLean(null);
        crew.setLeaning(false);
        crew.setLeanPauseUntil(level.getGameTime() + 20L * RailLeanConfig.PAUSE_SECONDS.get());
        if (crew.walk() == null) {
            crew.getNavigation().stop();
        }
    }

    /** Server, every tick of a crew member with a lean: anything that makes it not idle ends the lean at once. */
    public static void check(ServerLevel level, CrewMember crew) {
        if (crew.lean() != null && (!idle(crew) || !RailLeanConfig.ENABLED.get())) {
            end(level, crew);
        }
    }

    /** The yaw the leaner faces: outboard, turned with the ship (null when it does not lean yet). */
    public static @Nullable Direction facing(CrewMember crew) {
        Lean lean = crew.lean();
        return lean != null && lean.leaning ? lean.spot.outboard() : null;
    }

    /** Server stopped: forget every spot. */
    public static void clear() {
        TAKEN.clear();
    }
}
