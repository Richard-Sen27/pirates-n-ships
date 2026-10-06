package com.richardsenger.piratesnships.crew.npc;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.station.StationConfig;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationSpot;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.seat.StationSeat;
import com.richardsenger.piratesnships.station.winch.SailOrder;
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
 * release it, and give sail orders. Used by the captain's whistle, the {@code /pirates crew} commands and the
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

    public enum AssignResult { ASSIGNED, TAKEN, NOT_A_STATION, DISABLED }

    private CrewStations() {
    }

    /** Assigns {@code crew} to the station at plot position {@code plotPos} and seats it there. */
    public static AssignResult assign(ServerLevel level, CrewMember crew, BlockPos plotPos) {
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
        crew.setAssignment(ref);
        seat(level, crew, ref);
        return AssignResult.ASSIGNED;
    }

    /** Releases {@code crew} from its station: off the seat (at the seat's world position, on deck), station freed. */
    public static void release(ServerLevel level, CrewMember crew) {
        StationRef ref = crew.assignment();
        crew.setAssignment(null);
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

    /** Seated: re-asserts the occupancy (station states are not saved, see {@link Stations}). */
    static void keepOccupied(ServerLevel level, CrewMember crew) {
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
        if (ref == null || crew.isAtStation()) {
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
            BlockPos spot = StationSpot.choose(ref.pos(),
                    p -> level.getBlockState(p).getCollisionShape(level, p).isEmpty(),
                    p -> level.getBlockState(p).isFaceSturdy(level, p, net.minecraft.core.Direction.UP));
            seat = StationSeat.spawn(level, ref.pos(), spot);
        }
        crew.stopRiding();
        crew.getNavigation().stop();
        crew.startRiding(seat, true);
    }

    /** Gives a sail order to one crew member; it acknowledges near itself. */
    public static Stations.OrderResult order(ServerLevel level, CrewMember crew, SailOrder order) {
        StationRef ref = crew.assignment();
        Stations.OrderResult r = ref == null ? Stations.OrderResult.NOT_OCCUPIED : Stations.order(level, ref, order);
        switch (r) {
            case STARTED -> say(level, crew, Component.translatable(order.ackKey()));
            case NOTHING_TO_DO -> say(level, crew, Component.translatable(KEY_NOTHING_TO_DO, Component.translatable(order.nameKey())));
            case NOT_APPLICABLE -> say(level, crew, Component.translatable(KEY_NO_SAILS));
            default -> { }
        }
        return r;
    }

    /** Gives a sail order to every crew member at a station on {@code ship}; returns how many carry it out. */
    public static int orderShip(ServerLevel level, UUID ship, SailOrder order) {
        int n = 0;
        for (CrewMember c : crewOf(level, ship)) {
            if (order(level, c, order) == Stations.OrderResult.STARTED) n++;
        }
        return n;
    }

    /** Crew members assigned to stations of {@code ship}, found around the ship. */
    public static List<CrewMember> crewOf(ServerLevel level, UUID ship) {
        var body = SableShips.byId(level, ship);
        if (body == null) {
            return List.of();
        }
        AABB box = body.worldBounds().inflate(4);
        List<CrewMember> out = new ArrayList<>();
        for (CrewMember c : level.getEntitiesOfClass(CrewMember.class, box, c -> c.assignment() != null)) {
            if (c.assignment().ship().equals(ship)) out.add(c);
        }
        return out;
    }

    /** A translatable chat line from the crew member to the players near it. */
    static void say(ServerLevel level, CrewMember crew, Component line) {
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
