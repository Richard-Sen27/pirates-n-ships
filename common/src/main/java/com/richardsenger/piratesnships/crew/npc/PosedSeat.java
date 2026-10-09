package com.richardsenger.piratesnships.crew.npc;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * A seat that is no station's yet gives its rider a station pose and a facing (OAR1: the rail rower's place,
 * {@code sailing.oars.OarSeat}): {@link CrewMember} asks it every server tick, as it asks {@link StationPoses} for a
 * station seat, and turns the rider to the pose's facing (ST5). Keeps the crew module free of the modules that own
 * such seats.
 */
public interface PosedSeat {

    /** What the rider shows now, or null for the ordinary poses (and no facing). */
    StationPoses.@Nullable Shown shown(ServerLevel level);
}
