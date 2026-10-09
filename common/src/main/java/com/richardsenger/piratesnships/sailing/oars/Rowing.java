package com.richardsenger.piratesnships.sailing.oars;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.crew.hammock.CrewRest;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.crew.upkeep.Upkeep;
import com.richardsenger.piratesnships.crew.walk.CrewWalk;
import com.richardsenger.piratesnships.crew.walk.WalkConfig;
import com.richardsenger.piratesnships.crew.walk.WalkTarget;
import com.richardsenger.piratesnships.law.brig.BrigService;
import com.richardsenger.piratesnships.sailing.oars.OarRules.Side;
import com.richardsenger.piratesnships.sailing.ship.BowFrame;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.ShipSplits;
import com.richardsenger.piratesnships.ship.decor.railing.RailingBlock;
import com.richardsenger.piratesnships.ship.decor.railing.RailingPortBlock;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.winch.CaptainsWhistleItem;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * The rowing crews of the ships (OAR1, docs/design.md §5.3b), server side, in memory per ship (the "rowing" flag is
 * also kept in the ship's user data, so a restart sends the crew back to the oars).
 *
 * <h2>Row</h2>
 * {@link #toggle} (the whistle's "Row" / "Ship oars", one entry) gives Row when the ship does not row: every crew member
 * of the ship that is not at another station (free, also one in its hammock or at a meal; not a prisoner) takes an oar.
 * Benches first: a bench already manned by a crew member rows; each free bench takes the nearest free hand, which walks
 * to it (WALK1) and sits ({@link CrewStations#assign}, not pinned, {@link RowOrder#ROW}). The others go to the rail:
 * {@link OarSpots} picks a spot per hand, alternating sides with the benches counted; it walks there
 * ({@link WalkTarget.Purpose#OARS}) and takes its place on an {@link OarSeat} (at once when no walk starts).
 *
 * <h2>Ship oars</h2>
 * Ship oars ({@link #toggle} while rowing, {@link #shipOars}), any other order of the whistle or the ship screen
 * ({@link #stop}), {@code oars.enabled} off, the ship becoming a wreck, or the sails driving it faster than
 * {@code oars.max_speed} ({@link OarRules#watch}) end the rowing: bench rowers are released, rail rowers get up, oars
 * go back, and every rower walks back to the cell it stood on when Row was called.
 *
 * <h2>Each tick</h2>
 * {@link #onLevelTick}: a rower that died, left the ship's rail or bench (another job claimed it) drops out; a present
 * rower (seated at its working bench, or on its oar seat) holds the oar in the hand on its outboard side and counts for
 * the thrust; the snapshot of rowers and the crew's work speed ({@link Upkeep#workSpeed}) goes to the physics substep
 * ({@link OarThrust}). A ship left without rowers stops rowing.
 */
public final class Rowing {

    static final String KEY = "message." + Constants.MOD_ID + ".oars.";
    /** "%s hands to the oars" (how many). */
    public static final String KEY_ROWING = KEY + "rowing";
    public static final String KEY_SHIPPED = KEY + "shipped";
    public static final String KEY_NO_HANDS = KEY + "no_hands";
    public static final String KEY_DISABLED = KEY + "disabled";

    /** The ship's user data key holding {@code rowing} (true while the ship rows). */
    public static final String USER_DATA_KEY = Constants.MOD_ID + "_oars";
    /** Ticks between two looks for ships that were rowing when they were saved. */
    static final int RESUME_INTERVAL = 20;

    public enum Kind { BENCH, RAIL }

    /** A rower's place: its bench (plot position) or its spot at the rail, the outboard side of its oar. */
    public record Place(Kind kind, BlockPos pos, Direction outboard, Side side) {
        public Place {
            pos = pos.immutable();
        }
    }

    /** What the physics substep pushes with: the present rowers and the crew's work speed. */
    public record Snapshot(List<OarRules.Rower> rowers, double workSpeed) {
        public static final Snapshot EMPTY = new Snapshot(List.of(), 1.0);
    }

    public enum Result { ROWING, SHIPPED, NO_HANDS, NOT_ROWING, DISABLED }

    /** What an order did, the rowers it put to the oars and the captain's line (empty: nothing to say). */
    public record Outcome(Result result, int rowers, Component message) {
    }

    private static final class State {
        final ResourceKey<Level> dimension;
        final Map<UUID, Place> rowers = new LinkedHashMap<>();
        /** Where each rower stood when Row was called (plot cell): it walks back there when the oars are shipped. */
        final Map<UUID, BlockPos> home = new HashMap<>();
        int fastTicks;
        volatile Snapshot snapshot = Snapshot.EMPTY;

        State(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }
    }

    private static final Map<UUID, State> SHIPS = new ConcurrentHashMap<>();
    /** The handedness each rower had before it took an oar (the oar goes into the hand on its outboard side). */
    private static final Map<UUID, Boolean> HANDEDNESS = new ConcurrentHashMap<>();
    /** Ships whose user data was read for a saved Row since they were loaded. */
    private static final Set<UUID> CHECKED = ConcurrentHashMap.newKeySet();

    private Rowing() {
    }

    // ------------------------------------------------------------------ queries

    /** Whether {@code ship} rows. */
    public static boolean isRowing(UUID ship) {
        return SHIPS.containsKey(ship);
    }

    /** The rowers of {@code ship} and their places (a copy; empty when it does not row). */
    public static Map<UUID, Place> rowers(UUID ship) {
        State s = SHIPS.get(ship);
        return s == null ? Map.of() : new LinkedHashMap<>(s.rowers);
    }

    /** What the oars push with now ({@link Snapshot#EMPTY} when the ship does not row). */
    public static Snapshot snapshot(UUID ship) {
        State s = SHIPS.get(ship);
        return s == null ? Snapshot.EMPTY : s.snapshot;
    }

    /** Snapshots of every rowing ship of {@code level} (a copy), for the physics substep. */
    static Map<UUID, Snapshot> snapshots(ServerLevel level) {
        Map<UUID, Snapshot> out = new HashMap<>();
        SHIPS.forEach((id, s) -> {
            if (s.dimension == level.dimension() && !s.snapshot.rowers().isEmpty()) out.put(id, s.snapshot);
        });
        return out;
    }

    /** The plot direction of the stern of {@code ship} (where a rower faces), or null without a sailing runtime. */
    public static @Nullable Direction aft(ServerLevel level, UUID ship) {
        SailingRuntime rt = SailingRuntimes.get(level, ship);
        return rt == null ? null : Direction.fromDelta(-rt.bow().dx(), 0, -rt.bow().dz());
    }

    /**
     * Whether a rower facing {@code facing} holds its oar (pointing {@code outboard}) in the left hand: when the
     * outboard side is on its left. Seen from above, the right of a horizontal facing {@code (x, z)} is {@code (−z, x)}.
     */
    public static boolean leftHanded(Direction facing, Direction outboard) {
        return !(outboard.getStepX() == -facing.getStepZ() && outboard.getStepZ() == facing.getStepX());
    }

    // ------------------------------------------------------------------ orders

    /** The whistle's toggle: Row when {@code ship} does not row, else Ship oars. */
    public static Outcome toggle(ServerLevel level, ShipBody ship) {
        return isRowing(ship.id()) ? shipOars(level, ship.id()) : row(level, ship);
    }

    /** Row: every free crew member of {@code ship} takes an oar (rules above). */
    public static Outcome row(ServerLevel level, ShipBody ship) {
        if (!OarsConfig.ENABLED.get()) {
            return new Outcome(Result.DISABLED, 0, Component.translatable(KEY_DISABLED));
        }
        SailingRuntime rt = SailingRuntimes.getOrCreate(ship);
        if (rt == null || ShipSplits.isWreck(ship)) {
            return new Outcome(Result.NO_HANDS, 0, Component.translatable(KEY_NO_HANDS));
        }
        State st = SHIPS.computeIfAbsent(ship.id(), id -> new State(level.dimension()));
        int before = st.rowers.size();
        Direction port = OarSpots.port(rt.bow().dx(), rt.bow().dz());
        List<CrewMember> crew = level.getEntitiesOfClass(CrewMember.class, CrewStations.worldBox(ship, 4), CrewMember::isAlive);
        List<CrewMember> free = new ArrayList<>();
        for (CrewMember c : crew) {
            if (st.rowers.containsKey(c.getUUID()) || BrigService.isPrisoner(c)) continue;
            StationRef a = c.assignment();
            if (a != null) {
                // already at a bench of this ship (saved across a restart, or seated by hand): it rows there
                if (a.ship().equals(ship.id()) && level.getBlockState(a.pos()).getBlock() instanceof OarBenchBlock) {
                    benchRow(level, st, c, a, port);
                }
                continue;
            }
            ShipBody on = CaptainsWhistleItem.shipOf(level, c);
            if (on != null && on.id().equals(ship.id())) free.add(c);
        }
        // a rower still on its oar seat (after a reload of the ship) keeps its place
        for (Iterator<CrewMember> it = free.iterator(); it.hasNext(); ) {
            CrewMember c = it.next();
            if (c.getVehicle() instanceof OarSeat seat) {
                Side side = OarSpots.sideOf(seat.outboard(), port);
                st.rowers.put(c.getUUID(), new Place(Kind.RAIL, seat.blockPosition(), seat.outboard(), side));
                it.remove();
            }
        }
        // free benches take the nearest free hands
        List<BlockPos> benches = new ArrayList<>();
        for (BlockPos p : ship.plotBlocks()) {
            if (level.getBlockState(p).getBlock() instanceof OarBenchBlock && !Stations.isManned(new StationRef(ship.id(), p))) {
                benches.add(p.immutable());
            }
        }
        while (!benches.isEmpty() && !free.isEmpty()) {
            BlockPos bestBench = null;
            CrewMember bestCrew = null;
            double best = Double.MAX_VALUE;
            for (BlockPos b : benches) {
                Vec3 w = ship.toWorld(Vec3.atCenterOf(b));
                for (CrewMember c : free) {
                    double d = c.position().distanceToSqr(w);
                    if (d < best) {
                        best = d;
                        bestBench = b;
                        bestCrew = c;
                    }
                }
            }
            benches.remove(bestBench);
            free.remove(bestCrew);
            BlockPos home = plotCell(ship, bestCrew);
            if (CrewStations.assign(level, bestCrew, bestBench, false) == CrewStations.AssignResult.ASSIGNED) {
                StationRef ref = new StationRef(ship.id(), bestBench);
                if (benchRow(level, st, bestCrew, ref, port)) {
                    st.home.put(bestCrew.getUUID(), home);
                }
            } else {
                free.add(bestCrew);
            }
        }
        // the others go to the rail, alternating sides
        if (!free.isEmpty()) {
            Map<Side, Integer> already = new EnumMap<>(Side.class);
            Set<BlockPos> taken = new HashSet<>();
            for (Place p : st.rowers.values()) {
                already.merge(p.side(), 1, Integer::sum);
                if (p.kind() == Kind.RAIL) taken.add(p.pos());
            }
            List<OarSpots.Spot> spots = OarSpots.find(deck(level), deckCells(ship), port);
            List<OarSpots.Hand> hands = new ArrayList<>();
            Map<UUID, CrewMember> byId = new HashMap<>();
            for (CrewMember c : free) {
                hands.add(new OarSpots.Hand(c.getUUID(), ship.toPlot(c.position())));
                byId.put(c.getUUID(), c);
            }
            OarSpots.assign(spots, taken, hands, already).forEach((id, spot) -> {
                CrewMember c = byId.get(id);
                Place place = new Place(Kind.RAIL, spot.cell(), spot.outboard(), spot.side());
                st.rowers.put(id, place);
                st.home.put(id, plotCell(ship, c));
                toRail(level, c, ship.id(), place);
            });
        }
        if (st.rowers.isEmpty()) {
            SHIPS.remove(ship.id());
            return new Outcome(Result.NO_HANDS, 0, Component.translatable(KEY_NO_HANDS));
        }
        saveFlag(ship, true);
        int n = st.rowers.size();
        return new Outcome(Result.ROWING, n, n > before ? Component.translatable(KEY_ROWING, n) : Component.empty());
    }

    /** Orders the crew member at bench {@code ref} to row and counts it as a rower; false when the bench refuses. */
    private static boolean benchRow(ServerLevel level, State st, CrewMember c, StationRef ref, Direction port) {
        if (CrewStations.order(level, c, RowOrder.ROW) != Stations.OrderResult.STARTED) {
            return false;
        }
        BlockState state = level.getBlockState(ref.pos());
        Direction out = state.hasProperty(OarBenchBlock.FACING) ? state.getValue(OarBenchBlock.FACING) : port;
        st.rowers.put(c.getUUID(), new Place(Kind.BENCH, ref.pos(), out, OarSpots.sideOf(out, port)));
        return true;
    }

    /** Ship oars: the rowing of {@code ship} ends (rules above). */
    public static Outcome shipOars(ServerLevel level, UUID ship) {
        State st = SHIPS.remove(ship);
        if (st == null) {
            return new Outcome(Result.NOT_ROWING, 0, Component.empty());
        }
        ShipBody body = SableShips.byId(level, ship);
        for (Map.Entry<UUID, Place> e : st.rowers.entrySet()) {
            if (level.getEntity(e.getKey()) instanceof CrewMember c) {
                leave(level, c, e.getValue(), body, st.home.get(e.getKey()));
            }
        }
        if (body != null) saveFlag(body, false);
        return new Outcome(Result.SHIPPED, st.rowers.size(), Component.translatable(KEY_SHIPPED));
    }

    /** Any other order to {@code ship} (whistle, ship screen): the rowing ends first, so the rowers are free for it. */
    public static void stop(ServerLevel level, UUID ship) {
        shipOars(level, ship);
    }

    /** WALK1: a rail rower arrived at (or gave up walking to) its spot {@code cell} on {@code ship}. */
    public static void arrive(ServerLevel level, CrewMember crew, UUID ship, BlockPos cell) {
        State st = SHIPS.get(ship);
        Place p = st == null ? null : st.rowers.get(crew.getUUID());
        if (p != null && p.kind() == Kind.RAIL && p.pos().equals(cell)) {
            seatAtRail(level, crew, p);
        }
    }

    // ------------------------------------------------------------------ the crew

    private static void toRail(ServerLevel level, CrewMember crew, UUID ship, Place place) {
        if (crew.rest() != null || CrewWalk.walksFor(crew, WalkTarget.Purpose.HAMMOCK)) {
            CrewRest.getUp(crew, true); // called to the oars at night: up at once, on duty
        }
        if (!CrewWalk.begin(level, crew, WalkTarget.Purpose.OARS, ship, place.pos(), place.pos())) {
            seatAtRail(level, crew, place);
        }
    }

    private static void seatAtRail(ServerLevel level, CrewMember crew, Place place) {
        if (crew.getVehicle() instanceof OarSeat s && s.blockPosition().equals(place.pos())) {
            return;
        }
        OarSeat seat = null;
        for (OarSeat s : OarSeat.on(level, place.pos())) {
            if (!s.isVehicle()) {
                seat = s;
                break;
            }
        }
        if (seat == null) seat = OarSeat.spawn(level, place.pos(), place.outboard());
        crew.stopRiding();
        crew.getNavigation().stop();
        if (crew.startRiding(seat, true)) {
            seat.positionRider(crew); // place it now (Sable maps it to world space)
        }
    }

    /** A rower leaves its oar: off the bench or the oar seat, the oar put away, and back to where it stood. */
    private static void leave(ServerLevel level, CrewMember c, Place p, @Nullable ShipBody ship, @Nullable BlockPos home) {
        stowOar(c);
        if (p.kind() == Kind.BENCH) {
            StationRef a = c.assignment();
            if (a != null && a.pos().equals(p.pos())) CrewStations.release(level, c);
        } else {
            if (CrewWalk.walksFor(c, WalkTarget.Purpose.OARS)) CrewWalk.cancel(c);
            if (c.getVehicle() instanceof OarSeat seat) {
                c.stopRiding();
                seat.discard();
            }
        }
        if (home != null && ship != null && !c.isNoAi() && c.assignment() == null) {
            Path path = c.getNavigation().createPath(home, 1); // Sable paths in the plot (sable-notes §6, WALK1)
            if (path != null) c.getNavigation().moveTo(path, WalkConfig.SPEED.get());
        }
    }

    /** The rower holds its oar in the hand on its outboard side (only an empty hand takes it). */
    private static void holdOar(CrewMember c, Place p, @Nullable Direction aft) {
        HANDEDNESS.putIfAbsent(c.getUUID(), c.isLeftHanded());
        if (aft != null) c.setLeftHanded(leftHanded(aft, p.outboard()));
        if (c.getMainHandItem().isEmpty()) {
            c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(OarsContent.OAR.get()));
            c.setDropChance(EquipmentSlot.MAINHAND, 0f);
        }
    }

    /** Puts a rower's oar away and gives back its handedness. */
    public static void stowOar(CrewMember c) {
        if (c.getMainHandItem().is(OarsContent.OAR.get())) {
            c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        Boolean left = HANDEDNESS.remove(c.getUUID());
        if (left != null) c.setLeftHanded(left);
    }

    /** Whether the rower is at its oar now: seated at its working bench, or on its oar seat. */
    static boolean present(ServerLevel level, CrewMember c, UUID ship, Place p) {
        if (p.kind() == Kind.BENCH) {
            StationRef ref = new StationRef(ship, p.pos());
            return c.isAtStation() && ref.equals(c.assignment()) && Stations.isWorking(level, ref);
        }
        return c.getVehicle() instanceof OarSeat s && s.blockPosition().equals(p.pos());
    }

    /** Whether the rower left its oar for good: dead, released or claimed for another station, off its rail spot. */
    static boolean gone(ServerLevel level, @Nullable Entity e, UUID ship, Place p) {
        if (!(e instanceof CrewMember c) || !c.isAlive()) {
            return true;
        }
        if (p.kind() == Kind.BENCH) {
            StationRef a = c.assignment();
            if (a == null || !a.ship().equals(ship) || !a.pos().equals(p.pos())) return true;
            StationState<Object> st = Stations.state(a);
            return st != null && st.phase() == StationState.Phase.OPERATING && !(st.order() instanceof RowOrder);
        }
        if (c.assignment() != null) return true;
        return !(c.getVehicle() instanceof OarSeat s && s.blockPosition().equals(p.pos()))
                && !CrewWalk.walksTo(c, WalkTarget.Purpose.OARS, ship, p.pos());
    }

    // ------------------------------------------------------------------ ticks

    /** End of each level tick: resume saved rowing, keep the rowers, the thrust snapshot and the shipping watch. */
    public static void onLevelTick(ServerLevel level) {
        if (level.getGameTime() % RESUME_INTERVAL == 0) {
            resume(level);
        }
        if (SHIPS.isEmpty()) {
            return;
        }
        OarRules.Params params = OarsConfig.params();
        for (Map.Entry<UUID, State> e : new ArrayList<>(SHIPS.entrySet())) {
            State st = e.getValue();
            if (st.dimension != level.dimension()) continue;
            UUID id = e.getKey();
            ShipBody ship = SableShips.byId(level, id);
            if (ship == null) {
                if (ShipRegistry.get(level.getServer()).find(id).isEmpty()) {
                    SHIPS.remove(id, st); // disassembled or removed for good
                }
                continue; // unloaded: the rowing waits
            }
            if (!OarsConfig.ENABLED.get() || ShipSplits.isWreck(ship)) {
                shipOars(level, id);
                continue;
            }
            Direction aft = aft(level, id);
            List<OarRules.Rower> present = new ArrayList<>();
            for (Iterator<Map.Entry<UUID, Place>> it = st.rowers.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Place> r = it.next();
                Entity entity = level.getEntity(r.getKey());
                Place p = r.getValue();
                if (gone(level, entity, id, p)) {
                    if (entity instanceof CrewMember c) stowOar(c);
                    st.home.remove(r.getKey());
                    it.remove();
                    continue;
                }
                CrewMember c = (CrewMember) entity;
                if (present(level, c, id, p)) {
                    holdOar(c, p, aft);
                    present.add(new OarRules.Rower(p.kind() == Kind.BENCH, p.side()));
                }
            }
            if (st.rowers.isEmpty()) {
                SHIPS.remove(id, st);
                saveFlag(ship, false);
                continue;
            }
            st.snapshot = new Snapshot(List.copyOf(present), Upkeep.workSpeed(level, id));
            SailingRuntime rt = SailingRuntimes.get(level, id);
            if (rt != null) {
                double forward = rt.shipFrameVelocity(ship, new Vector3d()).z;
                st.fastTicks = OarRules.watch(st.fastTicks, forward, rt.unfurledCount() > 0, params);
                if (OarRules.shipOars(st.fastTicks, params)) {
                    shipOars(level, id); // the sails draw faster than the oars
                }
            }
        }
    }

    /** Ships saved while rowing row again once loaded. */
    private static void resume(ServerLevel level) {
        if (!OarsConfig.ENABLED.get()) {
            return;
        }
        for (SailingRuntime rt : new ArrayList<>(SailingRuntimes.runtimes(level))) {
            if (SHIPS.containsKey(rt.id()) || !CHECKED.add(rt.id())) continue;
            ShipBody ship = SableShips.byId(level, rt.id());
            if (ship != null && ship.userData(USER_DATA_KEY).getBoolean("rowing")) {
                Outcome o = row(level, ship);
                Constants.LOG.debug("Ship {} was rowing when saved: {} ({} rowers)", rt.id(), o.result(), o.rowers());
            }
        }
    }

    private static void saveFlag(ShipBody ship, boolean rowing) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("rowing", rowing);
        ship.setUserData(USER_DATA_KEY, tag);
    }

    public static void onShipRemoved(ServerLevel level, UUID ship, boolean destroyed) {
        CHECKED.remove(ship);
        if (destroyed) {
            SHIPS.remove(ship);
        }
    }

    /** A split: the benches and the rail moved or broke off; the rowing of both parts ends. */
    public static void onSplit(ShipSplits.SplitEvent event) {
        shipOars(event.level(), event.parent());
        shipOars(event.level(), event.keeper());
    }

    public static void onServerStopped() {
        SHIPS.clear();
        HANDEDNESS.clear();
        CHECKED.clear();
    }

    // ------------------------------------------------------------------ world → rules

    /** The plot cell a crew member stands in. */
    private static BlockPos plotCell(ShipBody ship, CrewMember c) {
        return BlockPos.containing(ship.toPlot(c.position().add(0, 0.1, 0)));
    }

    /** The cells above the ship's blocks: where a crew member might stand. */
    static Set<BlockPos> deckCells(ShipBody ship) {
        Set<BlockPos> out = new LinkedHashSet<>();
        for (BlockPos p : ship.plotBlocks()) out.add(p.above().immutable());
        return out;
    }

    /** The deck as the spot rule sees it ({@link OarSpots.Deck}), from the level's plot blocks. */
    static OarSpots.Deck deck(ServerLevel level) {
        return new OarSpots.Deck() {
            @Override
            public boolean standable(BlockPos cell) {
                return clear(cell) && clear(cell.above())
                        && level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP);
            }

            @Override
            public boolean open(BlockPos cell) {
                return level.getBlockState(cell).isAir() && level.getBlockState(cell.below()).isAir();
            }

            @Override
            public boolean railing(BlockPos cell) {
                var b = level.getBlockState(cell).getBlock();
                return b instanceof RailingBlock || b instanceof RailingPortBlock;
            }

            @Override
            public boolean bulwark(BlockPos cell) {
                BlockState s = level.getBlockState(cell);
                return !s.isAir() && s.isCollisionShapeFullBlock(level, cell) && clear(cell.above());
            }

            private boolean clear(BlockPos p) {
                BlockState s = level.getBlockState(p);
                return s.getCollisionShape(level, p).isEmpty() && !(s.getBlock() instanceof RailingBlock);
            }
        };
    }

    /** The bow frame of {@code ship}, or null without a runtime (for tests). */
    static @Nullable BowFrame bow(ServerLevel level, UUID ship) {
        SailingRuntime rt = SailingRuntimes.get(level, ship);
        return rt == null ? null : rt.bow();
    }
}
