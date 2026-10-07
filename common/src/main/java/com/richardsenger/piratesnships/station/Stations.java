package com.richardsenger.piratesnships.station;

import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.seat.StationSeat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side station service: one {@link StationState} per occupied station, keyed by {@link StationRef}. The state
 * is transient (not saved): occupancy is re-established after a reload by the seated crew ({@code CrewMember} asserts
 * its seat every tick), and an order in progress at save time is lost (documented spike limitation).
 */
public final class Stations {

    /**
     * What became of an order. {@link #WRONG_STATION}: this kind of station does not take this type of order (a pump
     * order to a winch, a sail order to a pump); the work in progress goes on. {@link #NOT_APPLICABLE}: the station
     * takes it but cannot carry it out here (a winch on a ship without sails, a pump that is switched off).
     */
    public enum OrderResult { STARTED, NOTHING_TO_DO, NOT_OCCUPIED, NOT_APPLICABLE, WRONG_STATION, NO_STATION, DISABLED }

    private record Entry(ResourceKey<Level> dimension, StationKind<?> kind, StationState<Object> state) { }

    private static final Map<StationRef, Entry> STATES = new ConcurrentHashMap<>();

    private Stations() {
    }

    /** The station kind of the block at {@code ref} (loaded ship, station block), or null. */
    public static @Nullable StationKind<?> kindAt(ServerLevel level, StationRef ref) {
        ShipBody ship = SableShips.byId(level, ref.ship());
        if (ship == null) {
            return null;
        }
        return level.getBlockState(ref.pos()).getBlock() instanceof StationBlock b ? b.stationKind() : null;
    }

    /** Whether the station at {@code ref} is of a kind that takes {@code order}; false when there is no station. */
    public static boolean accepts(ServerLevel level, StationRef ref, Object order) {
        StationKind<?> kind = kindAt(level, ref);
        return kind != null && kind.accepts(order);
    }

    /** The station whose block is at plot position {@code plotPos}, or null when it is not a station on a loaded ship. */
    public static @Nullable StationRef at(ServerLevel level, BlockPos plotPos) {
        ShipBody ship = SableShips.containing(level, plotPos);
        if (ship == null || !(level.getBlockState(plotPos).getBlock() instanceof StationBlock)) {
            return null;
        }
        return new StationRef(ship.id(), plotPos);
    }

    public static @Nullable StationState<Object> state(StationRef ref) {
        Entry e = STATES.get(ref);
        return e == null ? null : e.state();
    }

    /** Occupies a station; null when {@code ref} is not a station on a loaded ship. */
    public static StationState.@Nullable OccupyResult occupy(ServerLevel level, StationRef ref, StationState.Occupant who) {
        StationKind<?> kind = kindAt(level, ref);
        if (kind == null) {
            return null;
        }
        Entry e = STATES.computeIfAbsent(ref, r -> new Entry(level.dimension(), kind, new StationState<>()));
        return e.state().occupy(who);
    }

    /** Frees the station if {@code who} occupies it. */
    public static boolean release(StationRef ref, UUID who) {
        Entry e = STATES.get(ref);
        if (e == null || !e.state().release(who)) {
            return false;
        }
        STATES.remove(ref);
        return true;
    }

    /** Gives an order to the occupant of a station. */
    public static OrderResult order(ServerLevel level, StationRef ref, Object order) {
        if (!StationConfig.ENABLED.get()) {
            return OrderResult.DISABLED;
        }
        Entry e = STATES.get(ref);
        if (e == null || e.state().occupant() == null) {
            return OrderResult.NOT_OCCUPIED;
        }
        StationKind<?> kind = kindAt(level, ref);
        if (kind == null) {
            return OrderResult.NO_STATION;
        }
        if (!kind.accepts(order)) {
            return OrderResult.WRONG_STATION;
        }
        int ticks = duration(kind, level, ref, order);
        if (ticks < 0) {
            e.state().interrupt();
            return OrderResult.NOT_APPLICABLE;
        }
        if (ticks == 0) {
            e.state().interrupt();
            return OrderResult.NOTHING_TO_DO;
        }
        e.state().start(order, ticks);
        return OrderResult.STARTED;
    }

    /** Advances the orders in progress of this level; orders of ships that are not loaded wait. */
    public static void onLevelTick(ServerLevel level) {
        if (STATES.isEmpty()) {
            return;
        }
        List<Map.Entry<StationRef, Object>> done = new ArrayList<>();
        for (Map.Entry<StationRef, Entry> me : STATES.entrySet()) {
            Entry e = me.getValue();
            if (e.dimension() != level.dimension() || e.state().phase() != StationState.Phase.OPERATING) {
                continue;
            }
            if (SableShips.byId(level, me.getKey().ship()) == null) {
                continue;
            }
            Object finished = e.state().tick();
            if (finished != null) {
                done.add(Map.entry(me.getKey(), finished));
            }
        }
        for (Map.Entry<StationRef, Object> d : done) {
            StationKind<?> kind = kindAt(level, d.getKey());
            if (kind != null && kind.orderType().isInstance(d.getValue())) {
                complete(kind, level, d.getKey(), d.getValue());
            }
        }
    }

    /** The station block at {@code plotPos} was removed: free the station and remove its seats. */
    public static void onStationRemoved(ServerLevel level, BlockPos plotPos) {
        ShipBody ship = SableShips.containing(level, plotPos);
        if (ship != null) {
            STATES.remove(new StationRef(ship.id(), plotPos));
        }
        StationSeat.removeAt(level, plotPos);
    }

    /** A ship left the level; a ship destroyed for good takes its station states along. */
    public static void onShipRemoved(ServerLevel level, UUID ship, boolean destroyed) {
        if (!destroyed) {
            return;
        }
        for (Iterator<StationRef> it = STATES.keySet().iterator(); it.hasNext(); ) {
            if (it.next().ship().equals(ship)) {
                it.remove();
            }
        }
    }

    public static void onServerStopped() {
        STATES.clear();
    }

    @SuppressWarnings("unchecked")
    private static <O> int duration(StationKind<O> kind, ServerLevel level, StationRef ref, Object order) {
        return kind.durationTicks(level, ref, (O) order);
    }

    @SuppressWarnings("unchecked")
    private static <O> void complete(StationKind<O> kind, ServerLevel level, StationRef ref, Object order) {
        kind.complete(level, ref, (O) order);
    }
}
