package com.richardsenger.piratesnships.station.helm;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.station.order.CrewOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;

/**
 * The order of the helm station (WS3a, docs/design.md §6): hold a course through {@code waypoints} (world positions;
 * only x and z count) in order, and with {@code loop} start over at the first one after the last. A record, not an
 * enum, since every course has its own waypoints; so it is not one of {@link CrewOrder#all()} (no whistle entry and no
 * {@code /pirates crew order <id>}): {@code /pirates crew order course <x> <z> ...} and {@link HelmCourses#set} give
 * it.
 */
public record CourseOrder(List<Vec3> waypoints, boolean loop) implements CrewOrder {

    public static final String ID = "hold_course";
    static final String KEY = "message." + Constants.MOD_ID + ".crew.course.";

    public CourseOrder {
        waypoints = List.copyOf(waypoints);
    }

    /** A course to one point. */
    public static CourseOrder to(Vec3 point) {
        return new CourseOrder(List.of(point), false);
    }

    @Override
    public String id() {
        return ID;
    }

    /** "hold the course". */
    @Override
    public String nameKey() {
        return "crew_order." + Constants.MOD_ID + "." + ID;
    }

    /** "Aye, holding the course!". */
    @Override
    public String ackKey() {
        return "message." + Constants.MOD_ID + ".crew.ack." + ID;
    }

    /** A course without waypoints: "No course to hold, captain". */
    @Override
    public String nothingToDoKey() {
        return KEY + "empty";
    }

    /** At a second helm, or with steering or courses switched off: "This helm doesn't steer the ship, captain!". */
    @Override
    public String unableKey() {
        return KEY + "unable";
    }

    /**
     * Parses the waypoint argument of {@code /pirates crew order course}: pairs of x and z, each a number, {@code ~}
     * or {@code ~n} (relative to {@code origin}), optionally followed by the word {@code loop}. Empty when the text is
     * not that (an odd count, a word that is no number, no pair at all).
     */
    public static Optional<CourseOrder> parse(String text, Vec3 origin) {
        String[] parts = text.trim().split("\\s+");
        int n = parts.length;
        boolean loop = n > 0 && parts[n - 1].toLowerCase(Locale.ROOT).equals("loop");
        if (loop) n--;
        if (n < 2 || n % 2 != 0) {
            return Optional.empty();
        }
        List<Vec3> points = new ArrayList<>();
        for (int i = 0; i < n; i += 2) {
            Double x = coordinate(parts[i], origin.x);
            Double z = coordinate(parts[i + 1], origin.z);
            if (x == null || z == null) {
                return Optional.empty();
            }
            points.add(new Vec3(x, origin.y, z));
        }
        return Optional.of(new CourseOrder(points, loop));
    }

    private static Double coordinate(String s, double origin) {
        try {
            if (s.startsWith("~")) {
                return s.length() == 1 ? origin : origin + Double.parseDouble(s.substring(1));
            }
            double v = Double.parseDouble(s);
            return Double.isFinite(v) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
