package com.richardsenger.piratesnships.station.capstan;

import com.richardsenger.piratesnships.crew.npc.CrewPose;
import com.richardsenger.piratesnships.crew.npc.StationPoses;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The capstan crew's pose (CRW3, ART7's {@code capstan_push}): while the station carries out an anchor order the crew
 * member leans into the bars. CAP2: while it walks round the drum ({@link CapstanBlockEntity#walkHeading()}) it faces
 * along the circle, the way it pushes its bar; otherwise (the animation off, its spot on top of the capstan, the first
 * tick of the work) it faces the capstan from its spot ({@code StationSpot}'s default, beside the drum) and walks the
 * bars on the spot. Idle at the capstan it shows the ordinary poses. Registered with {@link StationPoses} by
 * {@code StationModule}.
 */
public final class CapstanPoses {

    private CapstanPoses() {
    }

    public static void register() {
        StationPoses.register(CapstanStation.INSTANCE.id(), CapstanPoses::resolve);
    }

    static StationPoses.@Nullable Shown resolve(ServerLevel level, StationRef station, @Nullable StationState<Object> state, BlockPos spot) {
        boolean working = state != null && state.phase() == StationState.Phase.OPERATING && state.order() instanceof AnchorOrder;
        Vec3 heading = level.getBlockEntity(station.pos()) instanceof CapstanBlockEntity be ? be.walkHeading() : null;
        return shown(working, spot, station.pos(), heading);
    }

    /**
     * The pose of a crew member at {@code spot} beside the capstan at {@code capstan}: pushing while it works, facing
     * {@code heading} while it walks round (a plot direction, or null: facing the capstan). Pure.
     */
    static StationPoses.@Nullable Shown shown(boolean working, BlockPos spot, BlockPos capstan, @Nullable Vec3 heading) {
        if (!working) {
            return null;
        }
        return heading != null
                ? new StationPoses.Shown(CrewPose.CAPSTAN_PUSH, null, heading)
                : new StationPoses.Shown(CrewPose.CAPSTAN_PUSH, StationPoses.toward(spot, capstan));
    }

    /** {@link #shown(boolean, BlockPos, BlockPos, Vec3)} on the spot. */
    static StationPoses.@Nullable Shown shown(boolean working, BlockPos spot, BlockPos capstan) {
        return shown(working, spot, capstan, null);
    }
}
