package com.richardsenger.piratesnships.station.capstan;

import com.richardsenger.piratesnships.crew.npc.CrewPose;
import com.richardsenger.piratesnships.crew.npc.StationPoses;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * The capstan crew's pose (CRW3, ART7's {@code capstan_push}): while the station carries out an anchor order the crew
 * member leans into the bars and walks them on the spot, facing the capstan from its spot ({@code StationSpot}'s
 * default, beside the drum); idle at the capstan it shows the ordinary poses. Walking round the drum is not attempted.
 * Registered with {@link StationPoses} by {@code StationModule}.
 */
public final class CapstanPoses {

    private CapstanPoses() {
    }

    public static void register() {
        StationPoses.register(CapstanStation.INSTANCE.id(), CapstanPoses::resolve);
    }

    static StationPoses.@Nullable Shown resolve(ServerLevel level, StationRef station, @Nullable StationState<Object> state, BlockPos spot) {
        boolean working = state != null && state.phase() == StationState.Phase.OPERATING && state.order() instanceof AnchorOrder;
        return shown(working, spot, station.pos());
    }

    /** The pose of a crew member at {@code spot} beside the capstan at {@code capstan}: pushing while it works. Pure. */
    static StationPoses.@Nullable Shown shown(boolean working, BlockPos spot, BlockPos capstan) {
        return working ? new StationPoses.Shown(CrewPose.CAPSTAN_PUSH, StationPoses.toward(spot, capstan)) : null;
    }
}
