package com.richardsenger.piratesnships.mob.village;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The day of a villager (LIFE1): which activity, and at which of its stations, at which time of day. Pure: the goal
 * ({@link ResidentRoutineGoal}) asks {@link #step} with the level's day time and walks there.
 *
 * <p>Times are Minecraft day ticks (0 = sunrise, 6000 = noon, 12000 = sunset, 13000 = dusk, 18000 = midnight,
 * 23000 = dawn). Every role's spans cover the whole day exactly once ({@link #spans}); a span may wrap past midnight.
 * Station indices count from 0 into the villager's planned stations ({@code ResidentPlan#work}); an index past the
 * last station means the last one.
 *
 * <table>
 *   <caption>The schedules</caption>
 *   <tr><th>role</th><th>day</th></tr>
 *   <tr><td>villager</td><td>1000 work at home (bench), 5000 about the village, 10500 sit (tavern, a bench), 12500 bed</td></tr>
 *   <tr><td>fishwife</td><td>0 the drying rack, 5000 the fish market's counter, 10500 sit, 12500 bed, 23000 home</td></tr>
 *   <tr><td>net mender</td><td>1000 nets, 4000 the other frame, 7000 nets, 10000 sit, 12500 bed</td></tr>
 *   <tr><td>dockhand</td><td>500 warehouse crates, 5500 the pier's cargo, 10500 sit, 12500 bed, 23000 home</td></tr>
 *   <tr><td>shipwright</td><td>1000 bench, 6000 the hull, 11000 sit, 12500 bed</td></tr>
 *   <tr><td>innkeeper</td><td>2000 the bar until 18000, then bed</td></tr>
 *   <tr><td>lamplighter</td><td>22500 the lamp round (dawn), 1000 the beacon tower, 10000 sit, 11500 the lamp round
 *   (dusk), 13500 bed</td></tr>
 *   <tr><td>vendor</td><td>1000 the stall, 11000 sit, 12500 bed</td></tr>
 * </table>
 */
public final class VillageRoutine {

    public static final int DAY = 24000;

    /** What a villager does in a span of the day. */
    public enum Activity {
        /** At home: near its first station, idle. */
        HOME,
        /** At a station, working (the rig's {@code work} pose). */
        WORK,
        /** Strolling about the village (as a sailor does). */
        WANDER,
        /** Sitting on a bench or at a tavern table (the rig's {@code sit} pose). */
        SIT,
        /** In bed (the rig's {@code sleep} pose). */
        SLEEP,
        /** The lamplighter's round of the lamp posts, a lantern in hand. */
        LAMP_ROUND
    }

    /** One activity, at station {@code station} when it is {@link Activity#WORK}. */
    public record Step(Activity activity, int station) {
        public static Step of(Activity activity) {
            return new Step(activity, 0);
        }
    }

    /** {@code step} from day tick {@code from} (inclusive) to {@code to} (exclusive); {@code to < from} wraps. */
    public record Span(int from, int to, Step step) {
        public boolean contains(int time) {
            return from <= to ? time >= from && time < to : time >= from || time < to;
        }

        public int length() {
            return from <= to ? to - from : DAY - from + to;
        }
    }

    private static final Map<VillageRole, List<Span>> SCHEDULES = new EnumMap<>(VillageRole.class);

    static {
        put(VillageRole.VILLAGER,
                span(23500, 1000, Activity.HOME), work(1000, 5000, 0), span(5000, 10500, Activity.WANDER),
                span(10500, 12500, Activity.SIT), span(12500, 23500, Activity.SLEEP));
        put(VillageRole.FISHWIFE,
                work(0, 5000, 0), work(5000, 10500, 1), span(10500, 12500, Activity.SIT),
                span(12500, 23000, Activity.SLEEP), span(23000, 0, Activity.HOME));
        put(VillageRole.NET_MENDER,
                span(23500, 1000, Activity.HOME), work(1000, 4000, 0), work(4000, 7000, 1), work(7000, 10000, 0),
                span(10000, 12500, Activity.SIT), span(12500, 23500, Activity.SLEEP));
        put(VillageRole.DOCKHAND,
                span(23000, 500, Activity.HOME), work(500, 5500, 0), work(5500, 10500, 1),
                span(10500, 12500, Activity.SIT), span(12500, 23000, Activity.SLEEP));
        put(VillageRole.SHIPWRIGHT,
                span(23500, 1000, Activity.HOME), work(1000, 6000, 0), work(6000, 11000, 1),
                span(11000, 12500, Activity.SIT), span(12500, 23500, Activity.SLEEP));
        put(VillageRole.INNKEEPER,
                work(2000, 18000, 0), span(18000, 2000, Activity.SLEEP));
        put(VillageRole.LAMPLIGHTER,
                span(22500, 1000, Activity.LAMP_ROUND), work(1000, 10000, 0), span(10000, 11500, Activity.SIT),
                span(11500, 13500, Activity.LAMP_ROUND), span(13500, 22500, Activity.SLEEP));
        put(VillageRole.VENDOR,
                span(23500, 1000, Activity.HOME), work(1000, 11000, 0), span(11000, 12500, Activity.SIT),
                span(12500, 23500, Activity.SLEEP));
    }

    private VillageRoutine() {
    }

    private static Span span(int from, int to, Activity activity) {
        return new Span(from, to, Step.of(activity));
    }

    private static Span work(int from, int to, int station) {
        return new Span(from, to, new Step(Activity.WORK, station));
    }

    private static void put(VillageRole role, Span... spans) {
        List<Span> list = new ArrayList<>(List.of(spans));
        SCHEDULES.put(role, Collections.unmodifiableList(list));
    }

    /** The schedule of {@code role}, in day order from its first span. */
    public static List<Span> spans(VillageRole role) {
        return SCHEDULES.get(role);
    }

    /** The time of day of a level's day time ({@code Level#getDayTime}), 0..23999. */
    public static int timeOfDay(long dayTime) {
        return (int) Math.floorMod(dayTime, (long) DAY);
    }

    /** What {@code role} does at day time {@code dayTime}. */
    public static Step step(VillageRole role, long dayTime) {
        int t = timeOfDay(dayTime);
        for (Span s : SCHEDULES.get(role)) {
            if (s.contains(t)) return s.step();
        }
        return Step.of(Activity.HOME); // unreachable while every schedule covers the day (VillageRoutineTest)
    }

    /**
     * What {@code role} does at {@code dayTime} with the routines switched on or off ({@code mobs.villagers.routines}):
     * off, every villager strolls about the village as a sailor does.
     */
    public static Step step(VillageRole role, long dayTime, boolean routines) {
        return routines ? step(role, dayTime) : Step.of(Activity.WANDER);
    }

    /** The first day time (0..23999) at which {@code role} does {@code step}, or -1 if never (tests, commands). */
    public static int startOf(VillageRole role, Step step) {
        for (Span s : SCHEDULES.get(role)) {
            if (s.step().equals(step)) return s.from();
        }
        return -1;
    }
}
