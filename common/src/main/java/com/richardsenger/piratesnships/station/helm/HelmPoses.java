package com.richardsenger.piratesnships.station.helm;

import com.richardsenger.piratesnships.crew.npc.CrewPose;
import com.richardsenger.piratesnships.crew.npc.StationPoses;
import com.richardsenger.piratesnships.sailing.helm.HelmBlockEntity;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * The helmsman's pose (ART7): a crew member at a helm station holds the wheel ({@link CrewPose#HELM}) and turns it hand
 * over hand while the wheel angle changes ({@link CrewPose#HELM_TURN_RIGHT} to starboard, {@link CrewPose#HELM_TURN_LEFT}
 * to port, {@link HelmTurn}), facing the helm block from his spot. The animations put the hands on the rim for a
 * helmsman on the helm's {@code FACING} side ({@code art/README.md}, "Crew station animations (ART7)"); from another
 * side he still faces the helm but his hands are beside the wheel. Registered with {@link StationPoses} by
 * {@code StationModule}.
 */
public final class HelmPoses {

    /** A helm's turn state is dropped when nobody looked at it for this many ticks. */
    static final long FORGET_TICKS = 200;

    private static final Map<StationRef, HelmTurn> TURNS = new ConcurrentHashMap<>();

    private HelmPoses() {
    }

    public static void register() {
        StationPoses.register(HelmStation.INSTANCE.id(), HelmPoses::resolve);
    }

    static @Nullable StationPoses.Shown resolve(ServerLevel level, StationRef station, @Nullable StationState<Object> state, BlockPos spot) {
        Direction facing = StationPoses.toward(spot, station.pos());
        double wheel = level.getBlockEntity(station.pos()) instanceof HelmBlockEntity be ? be.wheel() : 0.0;
        long now = level.getGameTime();
        int turn = TURNS.computeIfAbsent(station, s -> new HelmTurn()).update(wheel, now);
        if (TURNS.size() > 64) {
            TURNS.values().removeIf(t -> now - t.lastSeen() > FORGET_TICKS);
        }
        return new StationPoses.Shown(pose(turn), facing);
    }

    /** The pose for a turn direction of {@link HelmTurn#update}. */
    static CrewPose pose(int turn) {
        return turn > 0 ? CrewPose.HELM_TURN_RIGHT : turn < 0 ? CrewPose.HELM_TURN_LEFT : CrewPose.HELM;
    }

    /** Forgets every helm's turn (server stop). */
    public static void clear() {
        TURNS.clear();
    }
}
