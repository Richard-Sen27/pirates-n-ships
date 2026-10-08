package com.richardsenger.piratesnships.combat.cannon.npc;

import com.richardsenger.piratesnships.combat.cannon.CannonBlock;
import com.richardsenger.piratesnships.combat.cannon.CannonStation;
import com.richardsenger.piratesnships.combat.cannon.CannonStation.CannonOrder;
import com.richardsenger.piratesnships.crew.npc.CrewPose;
import com.richardsenger.piratesnships.crew.npc.StationPoses;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * The gun crew's poses (ART7): while the cannon station loads ({@link CannonOrder#LOAD}) the crew member rams
 * ({@link CrewPose#CANNON_LOAD}), while it fires ({@link CannonOrder#FIRE}, the fuse) it lunges with the linstock once
 * ({@link CrewPose#CANNON_FIRE}), and while its crew has a target of its own ({@link Gunnery#aiming}, fire at will or a
 * chosen ship) or takes "Fire at will" it sights along the barrel with a hand on the breech ({@link CrewPose#CANNON_AIM}).
 * It then faces the gun from its spot. Otherwise the ordinary poses apply (and it looks around). Pure choice in
 * {@link #pose}; registered with {@link StationPoses} by {@link Gunnery#register}.
 */
public final class GunCrewPoses {

    private GunCrewPoses() {
    }

    static void register() {
        StationPoses.register(CannonStation.INSTANCE.id(), GunCrewPoses::resolve);
    }

    static @Nullable StationPoses.Shown resolve(ServerLevel level, StationRef station, @Nullable StationState<Object> state, BlockPos spot) {
        Object order = state != null && state.phase() == StationState.Phase.OPERATING ? state.order() : null;
        CrewPose pose = pose(order, Gunnery.aiming(station));
        return pose == null ? null : new StationPoses.Shown(pose, facing(level, station.pos(), spot));
    }

    /**
     * @param order  the order the station is carrying out, or null when it carries out none
     * @param aiming the crew has a target ({@link Gunnery#aiming})
     * @return the station pose, or null for the ordinary poses
     */
    static @Nullable CrewPose pose(@Nullable Object order, boolean aiming) {
        if (order instanceof CannonOrder o) {
            return switch (o) {
                case LOAD -> CrewPose.CANNON_LOAD;
                case FIRE -> CrewPose.CANNON_FIRE;
                case FIRE_AT_WILL -> CrewPose.CANNON_AIM;
            };
        }
        return aiming ? CrewPose.CANNON_AIM : null;
    }

    /** Towards the station block when it is beside the spot, else towards any cannon block beside it (the rear half). */
    private static @Nullable Direction facing(ServerLevel level, BlockPos gun, BlockPos spot) {
        Direction d = StationPoses.toward(spot, gun);
        if (d != null) return d;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(spot.relative(side)).getBlock() instanceof CannonBlock) return side;
        }
        return null;
    }
}
