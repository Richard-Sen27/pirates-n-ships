package com.richardsenger.piratesnships.crew.npc;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.crew.hammock.CrewRest;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.ShipSplits;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.station.StationBlock;
import com.richardsenger.piratesnships.station.StationConfig;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationSpot;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.seat.StationSeat;
import com.richardsenger.piratesnships.station.order.CrewOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

/**
 * Crew ↔ station glue (docs/design.md §6, §7.2): assign a crew member to a station (it takes the station's seat),
 * release it, and give orders ({@link CrewOrder}: sail orders, the pump order). Used by the captain's whistle, the {@code /pirates crew} commands and the
 * GameTests alike.
 * <p>
 * Life cycle: an assigned crew member that is not seated retries every {@code seat_check_interval} ticks. It is
 * released when the station block is gone, the station is taken by somebody else, the ship no longer exists
 * ({@link ShipRegistry} has no record: disassembled or removed for good) or the feature is disabled. While the ship is
 * merely unloaded it waits.
 */
public final class CrewStations {

    static final String KEY = "message." + Constants.MOD_ID + ".crew.";
    public static final String KEY_ASSIGNED = KEY + "assigned";
    public static final String KEY_RELEASED = KEY + "released";
    public static final String KEY_TAKEN = KEY + "taken";
    public static final String KEY_NOT_A_STATION = KEY + "not_a_station";
    public static final String KEY_NOTHING_TO_DO = KEY + "nothing_to_do";
    public static final String KEY_NO_SAILS = KEY + "no_sails";
    public static final String KEY_DISABLED = KEY + "disabled";
    public static final String KEY_WRONG_STATION = KEY + "wrong_station";

    public enum AssignResult { ASSIGNED, TAKEN, NOT_A_STATION, DISABLED }

    private CrewStations() {
    }

    /**
     * Assigns {@code crew} by hand (whistle, command) to the station at plot position {@code plotPos} and seats it
     * there; it is {@linkplain CrewMember#isPinned() pinned}, the job board never moves it.
     */
    public static AssignResult assign(ServerLevel level, CrewMember crew, BlockPos plotPos) {
        return assign(level, crew, plotPos, true);
    }

    /**
     * Assigns {@code crew} to the station at plot position {@code plotPos} and seats it there. {@code pinned}: by hand
     * (the board never moves it) or by the job board (CR1, it may be re-tasked while idle).
     */
    public static AssignResult assign(ServerLevel level, CrewMember crew, BlockPos plotPos, boolean pinned) {
        if (!StationConfig.ENABLED.get()) {
            return AssignResult.DISABLED;
        }
        StationRef ref = Stations.at(level, plotPos);
        if (ref == null) {
            return AssignResult.NOT_A_STATION;
        }
        StationState.OccupyResult r = Stations.occupy(level, ref, occupant(crew));
        if (r == null) {
            return AssignResult.NOT_A_STATION;
        }
        if (r == StationState.OccupyResult.TAKEN) {
            return AssignResult.TAKEN;
        }
        if (crew.assignment() != null && !crew.assignment().equals(ref)) {
            release(level, crew);
        }
        if (crew.rest() != null) {
            CrewRest.getUp(crew, true); // ordered to a station at night: up at once, on duty for the night (HM1)
        }
        crew.setAssignment(ref);
        crew.setPinned(pinned);
        seat(level, crew, ref);
        return AssignResult.ASSIGNED;
    }

    /** Releases {@code crew} from its station: off the seat (at the seat's world position, on deck), station freed. */
    public static void release(ServerLevel level, CrewMember crew) {
        StationRef ref = crew.assignment();
        crew.setAssignment(null);
        crew.setPinned(false);
        if (crew.getVehicle() instanceof StationSeat seat) {
            crew.stopRiding();
            seat.discard();
        }
        if (ref != null) {
            Stations.release(ref, crew.getUUID());
        }
    }

    /** The crew member died or was removed for good. */
    static void onCrewGone(CrewMember crew) {
        StationRef ref = crew.assignment();
        crew.setAssignment(null);
        if (ref != null) {
            Stations.release(ref, crew.getUUID());
        }
        if (crew.getVehicle() instanceof StationSeat seat) {
            crew.stopRiding();
            seat.discard();
        }
    }

    /**
     * The ship under the station split (RS1, {@link ShipSplits}): a station that stayed with the ship (the keeper) is
     * manned again at its new place if it moved to another body; a station on a wreck or a dropped piece is given up,
     * which ends the order, and the crew member stays where it stands, on that piece's deck. Returns true if the
     * assignment changed.
     */
    private static boolean followSplit(ServerLevel level, CrewMember crew) {
        StationRef ref = crew.assignment();
        ShipSplits.Relocation r = ref == null ? null : ShipSplits.relocate(level, ref.ship(), ref.pos());
        if (r == null || r.keeper() && !r.moved(ref.ship(), ref.pos())) {
            return false;
        }
        boolean pinned = crew.isPinned();
        release(level, crew);
        if (r.keeper()) {
            assign(level, crew, r.pos(), pinned);
        }
        return true;
    }

    /** Seated: re-asserts the occupancy (station states are not saved, see {@link Stations}). */
    static void keepOccupied(ServerLevel level, CrewMember crew) {
        if (followSplit(level, crew)) {
            return;
        }
        StationRef ref = crew.assignment();
        if (ref != null && Stations.state(ref) == null) {
            StationState.OccupyResult r = Stations.occupy(level, ref, occupant(crew));
            if (r == StationState.OccupyResult.TAKEN || r == null) {
                release(level, crew);
            }
        }
        if (!StationConfig.ENABLED.get()) {
            release(level, crew);
        }
    }

    /** Assigned but not seated: take the seat, wait for the ship to load, or give up. */
    static void ensureSeated(ServerLevel level, CrewMember crew) {
        StationRef ref = crew.assignment();
        if (ref == null || crew.isAtStation() || followSplit(level, crew)) {
            return;
        }
        if (!StationConfig.ENABLED.get()) {
            release(level, crew);
            return;
        }
        if (SableShips.byId(level, ref.ship()) == null) {
            if (ShipRegistry.get(level.getServer()).find(ref.ship()).isEmpty()) {
                release(level, crew); // disassembled or removed for good
            }
            return; // unloaded: wait
        }
        StationState.OccupyResult r = Stations.occupy(level, ref, occupant(crew));
        if (r == null || r == StationState.OccupyResult.TAKEN) {
            release(level, crew);
            return;
        }
        seat(level, crew, ref);
    }

    private static void seat(ServerLevel level, CrewMember crew, StationRef ref) {
        if (crew.isAtStation()) {
            return;
        }
        StationSeat seat = StationSeat.free(level, ref.pos());
        if (seat == null) {
            // beside any block of the station (a two-block cannon: beside its front or rear), never on one of them
            var state = level.getBlockState(ref.pos());
            List<BlockPos> footprint = state.getBlock() instanceof StationBlock b ? b.footprint(state, ref.pos()) : List.of(ref.pos());
            BlockPos spot = StationSpot.choose(footprint,
                    p -> level.getBlockState(p).getCollisionShape(level, p).isEmpty(),
                    p -> level.getBlockState(p).isFaceSturdy(level, p, net.minecraft.core.Direction.UP));
            seat = StationSeat.spawn(level, ref.pos(), spot);
        }
        crew.stopRiding();
        crew.getNavigation().stop();
        if (crew.startRiding(seat, true)) {
            seat.positionRider(crew); // place it now, not on the next ride tick (Sable maps it to world space)
        }
    }

    /**
     * Gives an order to one crew member; it answers near itself. A crew member at a station of another kind (a pump
     * order at the winch, a sail order at the pump) refuses with {@link #KEY_WRONG_STATION} and keeps its work.
     */
    public static Stations.OrderResult order(ServerLevel level, CrewMember crew, CrewOrder order) {
        StationRef ref = crew.assignment();
        Stations.OrderResult r = ref == null ? Stations.OrderResult.NOT_OCCUPIED : Stations.order(level, ref, order);
        Component name = Component.translatable(order.nameKey());
        switch (r) {
            case STARTED -> say(level, crew, Component.translatable(order.ackKey()));
            case NOTHING_TO_DO -> say(level, crew, Component.translatable(order.nothingToDoKey(), name));
            case NOT_APPLICABLE -> say(level, crew, Component.translatable(order.unableKey()));
            case WRONG_STATION -> say(level, crew, Component.translatable(KEY_WRONG_STATION, name));
            default -> { }
        }
        return r;
    }

    /** Whether {@code crew} mans a station that takes {@code order} (whatever it is doing now). */
    public static boolean takes(ServerLevel level, CrewMember crew, CrewOrder order) {
        StationRef ref = crew.assignment();
        return ref != null && Stations.accepts(level, ref, order);
    }

    /**
     * Gives an order to the crew at stations of {@code ship} that take it (sail orders to the winches, pump orders to
     * the pumps); crew at other stations do not hear it. Returns how many carry it out.
     */
    public static int orderShip(ServerLevel level, UUID ship, CrewOrder order) {
        int n = 0;
        for (CrewMember c : crewOf(level, ship)) {
            if (takes(level, c, order) && order(level, c, order) == Stations.OrderResult.STARTED) n++;
        }
        return n;
    }

    /** Releases every crew member at a station of {@code ship} (the whistle's "release crew"); returns how many. */
    public static int releaseShip(ServerLevel level, UUID ship) {
        List<CrewMember> crew = crewOf(level, ship);
        for (CrewMember c : crew) release(level, c);
        return crew.size();
    }

    /** Crew members assigned to stations of {@code ship}, found around the ship. */
    public static List<CrewMember> crewOf(ServerLevel level, UUID ship) {
        var body = SableShips.byId(level, ship);
        if (body == null) {
            return List.of();
        }
        AABB box = worldBox(body, 4);
        List<CrewMember> out = new ArrayList<>();
        for (CrewMember c : level.getEntitiesOfClass(CrewMember.class, box, c -> c.assignment() != null)) {
            if (c.assignment().ship().equals(ship)) out.add(c);
        }
        return out;
    }

    /**
     * A world box around the ship from its plot bounds and pose: a cube around the transformed plot center. Unlike
     * {@code ShipBody#worldBounds} it is valid right after assembly (Sable fills the world bounds on the next tick).
     */
    public static AABB worldBox(com.richardsenger.piratesnships.ship.sable.ShipBody ship, double margin) {
        BlockPos[] b = ship.plotBounds();
        net.minecraft.world.phys.Vec3 lo = net.minecraft.world.phys.Vec3.atLowerCornerOf(b[0]);
        net.minecraft.world.phys.Vec3 hi = net.minecraft.world.phys.Vec3.atLowerCornerOf(b[1]).add(1, 1, 1);
        net.minecraft.world.phys.Vec3 center = ship.toWorld(lo.add(hi).scale(0.5));
        double r = hi.subtract(lo).length() / 2 + margin;
        return new AABB(center, center).inflate(r);
    }

    /** A translatable chat line from the crew member to the players near it. */
    public static void say(ServerLevel level, CrewMember crew, Component line) {
        int r = StationConfig.ACK_RADIUS.get();
        Component msg = Component.literal("<").append(crew.getDisplayName()).append("> ").append(line);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(crew) <= (double) r * r) {
                p.sendSystemMessage(msg);
            }
        }
    }

    private static StationState.Occupant occupant(CrewMember crew) {
        return new StationState.Occupant(crew.getUUID(), false);
    }
}
