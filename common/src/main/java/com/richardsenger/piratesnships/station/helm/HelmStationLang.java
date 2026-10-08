package com.richardsenger.piratesnships.station.helm;

import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.station.StationCommands;
import java.util.List;
import java.util.Locale;

/** English text of the NPC helmsman (WS3a), for the station module's datagen. */
public final class HelmStationLang {

    private static final CourseOrder SAMPLE = new CourseOrder(List.of(), false);

    private HelmStationLang() {
    }

    public static void lang(LangBuilder lang) {
        lang.add(SAMPLE.nameKey(), "hold the course")
                .add(SAMPLE.ackKey(), "Aye, holding the course!")
                .add(SAMPLE.nothingToDoKey(), "No course to hold, captain")
                .add(SAMPLE.unableKey(), "This helm doesn't steer the ship, captain!")
                .add(HelmCourses.KEY_WAYPOINT, "Waypoint reached, steering for the next (%s of %s)")
                .add(HelmCourses.KEY_ARRIVED, "We've arrived, captain! Rudder midships")
                .add(HelmCourses.KEY_STUCK, "We're not making headway, captain! We're stuck")
                .add(StationCommands.KEY_COURSE_SYNTAX, "Give the course as pairs of x and z, e.g. 120 -40 200 -40, optionally followed by loop")
                .add(key(HelmCourses.SetResult.STARTED), "Course set: %s waypoints; the helmsman holds it")
                .add(key(HelmCourses.SetResult.POSTED), "Course set: %s waypoints; a free hand will take the helm")
                .add(key(HelmCourses.SetResult.NO_HELMSMAN), "Course set (%s waypoints), but nobody is free to take the helm")
                .add(key(HelmCourses.SetResult.NO_HELM), "This ship has no helm that can hold a course")
                .add(key(HelmCourses.SetResult.EMPTY), "A course needs at least one waypoint")
                .add(key(HelmCourses.SetResult.DISABLED), "Holding a course is disabled on this server");
    }

    private static String key(HelmCourses.SetResult r) {
        return StationCommands.KEY_COURSE + r.name().toLowerCase(Locale.ROOT);
    }
}
