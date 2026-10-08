package com.richardsenger.piratesnships.crew.npc;

import com.richardsenger.piratesnships.crew.hammock.SleepAxis;
import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;

/**
 * Station poses of the crew (ART7, {@code art/README.md} "Crew station animations (ART7)"): what a crew member at its
 * station shows and which way it faces, chosen on the server every tick and synced through {@link CrewMember}. Each
 * station kind registers a {@link Resolver} under its {@link StationKind#id()} (the helm in {@code station.helm.HelmPoses},
 * the cannon in {@code combat.cannon.npc.GunCrewPoses}); a kind without one keeps the ordinary poses ({@link CrewPose}).
 * This keeps the crew module free of the station modules: they depend on it, not the other way round.
 */
public final class StationPoses {

    /**
     * What a crew member shows at its station.
     *
     * @param pose   a station pose ({@link CrewPose#isStationPose()})
     * @param facing the plot direction it faces (towards the wheel, the gun), or null to let it look around
     */
    public record Shown(CrewPose pose, @Nullable Direction facing) {
        public Shown {
            if (!pose.isStationPose()) throw new IllegalArgumentException(pose + " is not a station pose");
            if (facing != null && facing.getAxis().isVertical()) throw new IllegalArgumentException("facing must be horizontal");
        }
    }

    /** Chooses the pose of the crew member at {@code station}, standing at plot position {@code spot}. */
    @FunctionalInterface
    public interface Resolver {
        /**
         * @param state the station's state (order, phase), or null when the station has none
         * @return the pose, or null for the ordinary poses
         */
        @Nullable Shown resolve(ServerLevel level, StationRef station, @Nullable StationState<Object> state, BlockPos spot);
    }

    private static final Map<String, Resolver> BY_KIND = new ConcurrentHashMap<>();

    private StationPoses() {
    }

    /** Registers the resolver of a station kind (once, at mod setup); a second registration replaces the first. */
    public static void register(String kindId, Resolver resolver) {
        BY_KIND.put(kindId, resolver);
    }

    /** The pose of a crew member at {@code station} standing at {@code spot}, or null for the ordinary poses. */
    static @Nullable Shown resolve(ServerLevel level, StationRef station, BlockPos spot) {
        StationKind<?> kind = Stations.kindAt(level, station);
        Resolver resolver = kind == null ? null : BY_KIND.get(kind.id());
        return resolver == null ? null : resolver.resolve(level, station, Stations.state(station), spot);
    }

    /** The horizontal direction from {@code spot} to the neighbouring {@code block}, or null when they are not side by side. */
    public static @Nullable Direction toward(BlockPos spot, BlockPos block) {
        if (spot.getY() != block.getY()) return null;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (spot.relative(d).equals(block)) return d;
        }
        return null;
    }

    /**
     * The world yaw (Minecraft degrees, 0 = south) of an entity facing the plot direction {@code facing} on a ship
     * with body-to-world rotation {@code shipOrientation} (null: in the world). The plot direction is turned into the
     * world and projected on the horizontal plane ({@link SleepAxis#yaw} does exactly that for the opposite of its
     * argument).
     */
    public static float yaw(Direction facing, @Nullable Quaterniondc shipOrientation) {
        return SleepAxis.yaw(facing.getOpposite(), shipOrientation);
    }
}
