package com.richardsenger.piratesnships.sailing.oars;

import com.richardsenger.piratesnships.crew.npc.CrewPose;
import com.richardsenger.piratesnships.crew.npc.StationPoses;
import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * The oar bench as a station (OAR1, docs/design.md §5.3b, §6). Rowing has no end of its own: {@link RowOrder#ROW}
 * lasts {@link #ROW_TICKS} and is given again while the ship rows ({@link #complete}); "Ship oars" or any other order
 * ends it through {@link Rowing}, which releases the rower. Its pose: {@link CrewPose#ROW_SEATED} facing aft while it
 * rows ({@link #resolve}).
 */
public final class OarBenchStation implements StationKind<RowOrder> {

    public static final OarBenchStation INSTANCE = new OarBenchStation();

    /** Work time of one row order: five minutes, renewed while the ship rows. */
    public static final int ROW_TICKS = 6000;

    private OarBenchStation() {
    }

    @Override
    public String id() {
        return "oar_bench";
    }

    @Override
    public Class<RowOrder> orderType() {
        return RowOrder.class;
    }

    @Override
    public int durationTicks(ServerLevel level, StationRef station, RowOrder order) {
        return OarsConfig.ENABLED.get() ? ROW_TICKS : -1;
    }

    /** Still rowing: pull on. */
    @Override
    public void complete(ServerLevel level, StationRef station, RowOrder order) {
        if (Rowing.isRowing(station.ship())) {
            Stations.order(level, station, order);
        }
    }

    /** Registers the bench's pose and facing with {@link StationPoses}. */
    static void registerPoses() {
        StationPoses.register(INSTANCE.id(), OarBenchStation::resolve);
    }

    /** Seated and rowing: {@link CrewPose#ROW_SEATED}, facing aft (the stern of the ship's bow); else the ordinary poses. */
    static StationPoses.@Nullable Shown resolve(ServerLevel level, StationRef station, @Nullable StationState<Object> state, BlockPos spot) {
        boolean rowing = state != null && state.phase() == StationState.Phase.OPERATING && state.order() instanceof RowOrder;
        Direction aft = rowing ? Rowing.aft(level, station.ship()) : null;
        return aft == null ? null : new StationPoses.Shown(CrewPose.ROW_SEATED, aft);
    }
}
