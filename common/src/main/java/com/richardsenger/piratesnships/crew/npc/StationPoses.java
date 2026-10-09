package com.richardsenger.piratesnships.crew.npc;

import com.richardsenger.piratesnships.crew.hammock.SleepAxis;
import com.richardsenger.piratesnships.station.StationBlock;
import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

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
     * @param facing  the plot direction it faces (towards the wheel, the gun), or null to let it look around
     * @param heading a horizontal plot direction it faces instead of {@code facing}, any way round (CAP2: along the
     *                capstan's circle while it walks the bars round), or null
     */
    public record Shown(CrewPose pose, @Nullable Direction facing, @Nullable Vec3 heading) {
        public Shown {
            if (!pose.isStationPose()) throw new IllegalArgumentException(pose + " is not a station pose");
            if (facing != null && facing.getAxis().isVertical()) throw new IllegalArgumentException("facing must be horizontal");
            if (heading != null && heading.x * heading.x + heading.z * heading.z < 1e-12) heading = null;
        }

        public Shown(CrewPose pose, @Nullable Direction facing) {
            this(pose, facing, null);
        }

        /** Whether it turns to a set direction ({@link #heading} or {@link #facing}) rather than looking around. */
        public boolean faces() {
            return heading != null || facing != null;
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

    /**
     * Which way a crew member at a station faces when its pose does not say (ST5): a horizontal plot direction, any
     * length, or null for the default ({@link #heading}).
     */
    @FunctionalInterface
    public interface Facing {
        @Nullable Vec3 heading(ServerLevel level, StationRef station, BlockPos spot);
    }

    private static final Map<String, Resolver> BY_KIND = new ConcurrentHashMap<>();
    private static final Map<String, Facing> FACING_BY_KIND = new ConcurrentHashMap<>();

    private StationPoses() {
    }

    /** Registers the resolver of a station kind (once, at mod setup); a second registration replaces the first. */
    public static void register(String kindId, Resolver resolver) {
        BY_KIND.put(kindId, resolver);
    }

    /**
     * Registers which way a crew member at a station kind faces when its pose does not say (ST5; the crow's nest faces
     * the bow), replacing the default {@link #toward(Vec3, BlockPos, List)}.
     */
    public static void registerFacing(String kindId, Facing facing) {
        FACING_BY_KIND.put(kindId, facing);
    }

    /**
     * The horizontal plot direction a crew member at {@code station} faces from its seat at plot position {@code seat}
     * (ST5): the kind's registered {@link Facing}, else towards the station's block from the seat
     * ({@link #toward(Vec3, BlockPos, List)}); null when neither gives one (a seat on top of its station).
     */
    static @Nullable Vec3 heading(ServerLevel level, StationRef station, Vec3 seat) {
        StationKind<?> kind = Stations.kindAt(level, station);
        Facing facing = kind == null ? null : FACING_BY_KIND.get(kind.id());
        BlockPos spot = BlockPos.containing(seat);
        Vec3 h = facing == null ? null : facing.heading(level, station, spot);
        if (h != null && h.x * h.x + h.z * h.z > 1e-8) {
            return h;
        }
        BlockState state = level.getBlockState(station.pos());
        List<BlockPos> footprint = state.getBlock() instanceof StationBlock sb ? sb.footprint(state, station.pos()) : List.of(station.pos());
        return toward(seat, station.pos(), footprint);
    }

    /**
     * The default facing of a seat at plot position {@code seat} (ST5): towards the middle of the first block of the
     * station's {@code footprint} beside the seat's cell (the cannon's rear half too), else of the {@code station}
     * block (a capstan seat walking round the drum); null when the seat stands on or in the station block. A horizontal plot direction. Pure.
     */
    public static @Nullable Vec3 toward(Vec3 seat, BlockPos station, List<BlockPos> footprint) {
        BlockPos spot = BlockPos.containing(seat);
        BlockPos at = station;
        for (BlockPos block : footprint) {
            if (toward(spot, block) != null) {
                at = block;
                break;
            }
        }
        double dx = at.getX() + 0.5 - seat.x;
        double dz = at.getZ() + 0.5 - seat.z;
        return dx * dx + dz * dz < 0.01 ? null : new Vec3(dx, 0, dz);
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

    /**
     * The world yaw of an entity facing the horizontal plot direction {@code heading} (CAP2), like
     * {@link #yaw(Direction, Quaterniondc)} for any direction: turned into the world and projected on the horizontal plane.
     */
    public static float yaw(Vec3 heading, @Nullable Quaterniondc shipOrientation) {
        Vector3d d = new Vector3d(heading.x, 0, heading.z);
        if (shipOrientation != null) {
            Vector3d w = shipOrientation.transform(new Vector3d(d));
            if (w.x * w.x + w.z * w.z > 1e-8) {
                d = w;
            }
        }
        return Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
    }

    /** The yaw of the pose {@code shown} on a ship with {@code shipOrientation}: its heading, else its facing. */
    public static float yaw(Shown shown, @Nullable Quaterniondc shipOrientation) {
        Vec3 heading = shown.heading();
        if (heading != null) {
            return yaw(heading, shipOrientation);
        }
        Direction facing = shown.facing();
        if (facing == null) throw new IllegalArgumentException("the pose faces nowhere");
        return yaw(facing, shipOrientation);
    }
}
