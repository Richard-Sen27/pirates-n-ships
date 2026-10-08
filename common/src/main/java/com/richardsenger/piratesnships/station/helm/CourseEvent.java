package com.richardsenger.piratesnships.station.helm;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/**
 * Something that happened to a ship's course ({@link HelmCourses#onEvent}).
 *
 * @param ship     the ship (Sable sub-level id)
 * @param waypoint index of the waypoint concerned: the one reached last for {@link Type#ARRIVED}, the new target for
 *                 {@link Type#WAYPOINT}, the current target otherwise
 */
public record CourseEvent(ServerLevel level, UUID ship, Type type, int waypoint) {

    public enum Type {
        /** A waypoint was reached and the next one is the target. */
        WAYPOINT,
        /** The last waypoint of a course that does not loop was reached; the rudder is midships, the course ended. */
        ARRIVED,
        /** The ship has sails set but has not moved for {@code stuck_ticks}; the course goes on. */
        STUCK,
        /** Nobody can hold the course: the helm is unmanned and nobody takes it, or the helmsman left (course ended). */
        NO_HELMSMAN
    }
}
