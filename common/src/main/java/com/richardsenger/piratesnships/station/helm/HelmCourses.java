package com.richardsenger.piratesnships.station.helm;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.sailing.ship.ShipControls;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.ShipHelm;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationConfig;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.jobs.JobBoard;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Courses of NPC helmsmen (WS3a, docs/design.md §6, §10.4): per ship the {@link CourseOrder} its helm station carries
 * out and how far along it is. Every {@code crew_stations.course.update_interval_ticks} on the level tick, for each
 * ship with a course whose steering helm is manned by a crew member, the rudder is set by {@link CourseKeeper} toward
 * the current waypoint through {@link ShipControls#setRudderAngle} (so both steering modes follow).
 *
 * <ul>
 *   <li><b>Waypoints:</b> within {@code arrival_radius} of the ship's center the next waypoint becomes the target
 *       ({@link CourseEvent.Type#WAYPOINT}); after the last the course starts over when it loops, else the helmsman
 *       puts the rudder midships, says so, and the course ends ({@link CourseEvent.Type#ARRIVED}).</li>
 *   <li><b>A player at the wheel:</b> while a player holds the wheel (HELM1 session) the helmsman leaves it alone; when
 *       a player moved the wheel otherwise (a click step) he waits {@code manual_override_ticks}. The course goes on
 *       afterwards.</li>
 *   <li><b>No helmsman:</b> a course set for an unmanned helm is posted on the job board and waits for a free hand
 *       ({@link CourseEvent.Type#NO_HELMSMAN} when nobody can take it). When the helmsman leaves (released, killed,
 *       the station broken) the rudder is put midships, the course ends and {@code NO_HELMSMAN} is reported.</li>
 *   <li><b>Stuck:</b> a ship with sails set that moves slower than {@code stuck_speed} for {@code stuck_ticks} is
 *       reported once ({@link CourseEvent.Type#STUCK}) per episode; the course goes on and listeners decide (WS3b).</li>
 * </ul>
 *
 * <p><b>Voice (WS4c):</b> {@link #set} has the helmsman acknowledge the course and call each waypoint
 * ({@link Voice#FULL}); a course set again and again on a timer (a patrol circling its quarry) is given with
 * {@link Voice#ACK_ONLY} the first time and {@link Voice#SILENT} afterwards, so a chase is not a stream of chat lines.
 * Arrival and a stuck ship are always said (once each). {@link #onSpoken} hears every line the helmsman says.
 *
 * Orders given to the helm by other paths (the job board, {@code CrewStations.order} with a {@code CourseOrder}) are
 * adopted on the next update. In memory only, like the station states: a course is lost on restart.
 */
public final class HelmCourses {

    static final String KEY = CourseOrder.KEY;
    public static final String KEY_ARRIVED = KEY + "arrived";
    public static final String KEY_WAYPOINT = KEY + "waypoint";
    public static final String KEY_STUCK = KEY + "stuck";

    /** What the helmsman says about a course he is given (WS4c). */
    public enum Voice {
        /** "Aye, holding the course!" and each waypoint reached (a course given by a player or a voyage). */
        FULL,
        /** The acknowledgement, but no waypoint calls (the first course of a chase). */
        ACK_ONLY,
        /** Neither (a course given again on a timer): only arrival and getting stuck are said. */
        SILENT;

        boolean acknowledges() {
            return this != SILENT;
        }

        boolean callsWaypoints() {
            return this == FULL;
        }
    }

    /** A chat line the helmsman of {@code ship} said ({@link #onSpoken}). */
    public record Spoken(UUID ship, Component line) { }

    /** What became of {@link #set}. */
    public enum SetResult {
        /** The helmsman at the steering helm took the course. */
        STARTED,
        /** The helm is unmanned; the course is posted on the job board and a free hand will take it. */
        POSTED,
        /** The helm is unmanned and nobody aboard can take the course now; it waits for a helmsman. */
        NO_HELMSMAN,
        /** The ship has no steering helm, or the helm cannot hold a course now (steering off). */
        NO_HELM,
        /** The course has no waypoints. */
        EMPTY,
        /** Courses or crew stations are switched off. */
        DISABLED
    }

    static final class Course {
        final ResourceKey<Level> dimension;
        final Voice voice;
        CourseOrder order;
        int index;
        boolean manned;
        boolean reportedNoHelmsman;
        double lastHeading = Double.NaN;
        long lastHeadingTick;
        int slowTicks;
        boolean stuckReported;
        /** The wheel angle this course wrote last (NaN: none yet); another value means a player moved the wheel. */
        double lastWheel = Double.NaN;
        long overrideUntil = Long.MIN_VALUE;
        double rudder;

        Course(ResourceKey<Level> dimension, CourseOrder order, Voice voice) {
            this.dimension = dimension;
            this.order = order;
            this.voice = voice;
        }
    }

    private static final Map<UUID, Course> COURSES = new ConcurrentHashMap<>();
    private static final List<Consumer<CourseEvent>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<Consumer<Spoken>> SPOKEN = new CopyOnWriteArrayList<>();

    private HelmCourses() {
    }

    // ------------------------------------------------------------------ API

    /** Listens to course events of every ship (WS3b: a stuck voyage skips ahead; WS4b: a patrol re-plans). */
    public static void onEvent(Consumer<CourseEvent> listener) {
        LISTENERS.add(listener);
    }

    /** Hears every line a helmsman says about his course (acknowledgement, waypoints, arrival, stuck): logs, GameTests. */
    public static void onSpoken(Consumer<Spoken> listener) {
        SPOKEN.add(listener);
    }

    /**
     * Sets the course of {@code ship}: the crew member at its steering helm takes it at once; an unmanned helm becomes
     * an open job on the ship's {@link JobBoard} that a free hand claims. Replaces an earlier course. Sails are not the
     * helmsman's job: post {@code SailOrder.HOIST} to set them. The helmsman acknowledges it and calls its waypoints
     * ({@link Voice#FULL}).
     */
    public static SetResult set(ServerLevel level, ShipBody ship, CourseOrder order) {
        return set(level, ship, order, Voice.FULL);
    }

    /**
     * {@link #set} with what the helmsman says about it: {@link Voice#SILENT} for a course given again on a timer
     * (WS4c), so neither the acknowledgement nor the waypoints are said. An unmanned helm's job-board claim is the
     * crew member's own business and still acknowledged as usual.
     */
    public static SetResult set(ServerLevel level, ShipBody ship, CourseOrder order, Voice voice) {
        if (!CourseConfig.ENABLED.get() || !StationConfig.ENABLED.get()) {
            return SetResult.DISABLED;
        }
        if (order.waypoints().isEmpty()) {
            return SetResult.EMPTY;
        }
        BlockPos helm = ShipHelm.steering(ship);
        if (helm == null || !SailingConfig.STEERING_ENABLED.get()) {
            return SetResult.NO_HELM;
        }
        StationRef ref = new StationRef(ship.id(), helm);
        Course course = new Course(level.dimension(), order, voice);
        COURSES.put(ship.id(), course);
        StationState<Object> state = Stations.state(ref);
        if (state != null && crewOccupant(state) != null) {
            course.manned = true;
            Stations.OrderResult r = Stations.order(level, ref, order);
            if (r == Stations.OrderResult.STARTED) {
                if (voice.acknowledges()) say(level, ship.id(), state, Component.translatable(order.ackKey()));
                return SetResult.STARTED;
            }
            COURSES.remove(ship.id(), course);
            return SetResult.NO_HELM;
        }
        if (state != null && state.occupant() != null) {
            // a player occupies the helm station: the course waits for a crew member
            course.reportedNoHelmsman = true;
            fire(level, ship.id(), CourseEvent.Type.NO_HELMSMAN, 0);
            return SetResult.NO_HELMSMAN;
        }
        JobBoard.Posted posted = JobBoard.post(level, ship, order);
        if (posted.jobs() > 0 && !posted.noFreeHands()) {
            return SetResult.POSTED;
        }
        course.reportedNoHelmsman = true;
        fire(level, ship.id(), CourseEvent.Type.NO_HELMSMAN, 0);
        return SetResult.NO_HELMSMAN;
    }

    /**
     * Ends the course of {@code ship}: the helmsman stops holding it (he stays at the helm) and puts the rudder
     * midships. Returns false when the ship had no course.
     */
    public static boolean clear(ServerLevel level, UUID ship) {
        Course c = COURSES.remove(ship);
        if (c == null) {
            return false;
        }
        ShipBody body = SableShips.byId(level, ship);
        BlockPos helm = body == null ? null : ShipHelm.steering(body);
        if (helm != null) {
            StationState<Object> state = Stations.state(new StationRef(ship, helm));
            if (state != null && state.order() instanceof CourseOrder) {
                state.interrupt();
            }
            ShipControls.setRudderAngle(level, helm, 0.0);
        }
        return true;
    }

    /** The course {@code ship} holds or waits to hold, or null. */
    public static @Nullable CourseOrder course(UUID ship) {
        Course c = COURSES.get(ship);
        return c == null ? null : c.order;
    }

    /** The index of the waypoint {@code ship} steers for, -1 without a course. */
    public static int waypointIndex(UUID ship) {
        Course c = COURSES.get(ship);
        return c == null ? -1 : c.index;
    }

    /** The rudder angle the helmsman of {@code ship} asked for last [degrees], 0 without a course. */
    public static double lastRudder(UUID ship) {
        Course c = COURSES.get(ship);
        return c == null ? 0.0 : c.rudder;
    }

    /** Whether {@code ship} still holds {@code order} (the helm station carries on when its estimate runs out). */
    static boolean isActive(UUID ship, CourseOrder order) {
        Course c = COURSES.get(ship);
        return c != null && c.order.equals(order);
    }

    /**
     * Horizontal length of what is left of {@code order} for {@code ship}: from the ship's center through the
     * waypoints from the current one (the first, if the ship holds another course). 0 when the ship is not loaded.
     */
    static double remainingDistance(ServerLevel level, UUID ship, CourseOrder order) {
        ShipBody body = SableShips.byId(level, ship);
        if (body == null || order.waypoints().isEmpty()) {
            return 0.0;
        }
        Course c = COURSES.get(ship);
        int from = c != null && c.order.equals(order) ? Math.min(c.index, order.waypoints().size() - 1) : 0;
        Vec3 p = center(body);
        double d = 0.0;
        for (int i = from; i < order.waypoints().size(); i++) {
            Vec3 w = order.waypoints().get(i);
            d += Math.hypot(w.x - p.x, w.z - p.z);
            p = w;
        }
        return d;
    }

    // ------------------------------------------------------------------ tick

    /** Level tick: every {@code update_interval_ticks}, each course of this level steers once. */
    public static void onLevelTick(ServerLevel level) {
        long now = level.getGameTime();
        if (now % CourseConfig.UPDATE_INTERVAL_TICKS.get() != 0) {
            return;
        }
        adopt(level);
        if (COURSES.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Course> e : new ArrayList<>(COURSES.entrySet())) {
            if (e.getValue().dimension == level.dimension()) {
                update(level, e.getKey(), e.getValue(), now);
            }
        }
    }

    /** One update of {@code ship}'s course now, whatever the interval (for the GameTests). */
    public static void update(ServerLevel level, UUID ship) {
        Course c = COURSES.get(ship);
        if (c != null) {
            update(level, ship, c, level.getGameTime());
        }
    }

    /** Courses given to a helm by other paths than {@link #set} (job board claims, direct crew orders) are taken up. */
    private static void adopt(ServerLevel level) {
        for (ShipBody ship : SableShips.all(level)) {
            if (ship.isRemoved() || COURSES.containsKey(ship.id())) {
                continue;
            }
            BlockPos helm = ShipHelm.steering(ship);
            StationState<Object> state = helm == null ? null : Stations.state(new StationRef(ship.id(), helm));
            if (state != null && state.order() instanceof CourseOrder order) {
                Course c = new Course(level.dimension(), order, Voice.FULL);
                c.manned = true;
                COURSES.put(ship.id(), c);
            }
        }
    }

    private static void update(ServerLevel level, UUID id, Course c, long now) {
        if (!CourseConfig.ENABLED.get() || !StationConfig.ENABLED.get()) {
            clear(level, id);
            return;
        }
        ShipBody ship = SableShips.byId(level, id);
        if (ship == null || ship.isRemoved()) {
            if (ShipRegistry.get(level.getServer()).find(id).isEmpty()) {
                COURSES.remove(id, c); // disassembled or removed for good
            }
            return; // unloaded: the course waits
        }
        BlockPos helm = ShipHelm.steering(ship);
        StationRef ref = helm == null ? null : new StationRef(id, helm);
        StationState<Object> state = ref == null ? null : Stations.state(ref);
        UUID helmsman = state == null ? null : crewOccupant(state);
        if (helmsman == null) {
            if (c.manned) {
                // the helmsman left: rudder midships, the course ends
                COURSES.remove(id, c);
                if (helm != null) ShipControls.setRudderAngle(level, helm, 0.0);
                fire(level, id, CourseEvent.Type.NO_HELMSMAN, c.index);
            } else if (!c.reportedNoHelmsman && JobBoard.jobs(id).isEmpty()) {
                c.reportedNoHelmsman = true;
                fire(level, id, CourseEvent.Type.NO_HELMSMAN, c.index);
            }
            return;
        }
        c.manned = true;
        if (state.order() instanceof CourseOrder given && !given.equals(c.order)) {
            c.order = given; // a newer course given to the helmsman directly
            c.index = 0;
        } else if (state.order() == null) {
            Stations.OrderResult r = Stations.order(level, ref, c.order); // e.g. seated again after a split
            if (r != Stations.OrderResult.STARTED) {
                COURSES.remove(id, c);
                return;
            }
        }
        Vec3 pos = center(ship);
        List<Vec3> points = c.order.waypoints();
        double radius = CourseConfig.ARRIVAL_RADIUS.get();
        Vec3 target = points.get(Math.min(c.index, points.size() - 1));
        if (CourseKeeper.arrived(target.x - pos.x, target.z - pos.z, radius)) {
            if (c.index < points.size() - 1 || c.order.loop()) {
                c.index = (c.index + 1) % points.size();
                fire(level, id, CourseEvent.Type.WAYPOINT, c.index);
                if (c.voice.callsWaypoints()) say(level, id, state, Component.translatable(KEY_WAYPOINT, c.index + 1, points.size()));
                target = points.get(c.index);
            } else {
                COURSES.remove(id, c);
                state.interrupt();
                ShipControls.setRudderAngle(level, helm, 0.0);
                say(level, id, state, Component.translatable(KEY_ARRIVED));
                fire(level, id, CourseEvent.Type.ARRIVED, c.index);
                return;
            }
        }
        SailingRuntime rt = SailingRuntimes.get(level, id);
        if (rt == null) {
            return;
        }
        steer(level, helm, rt, ship, c, pos, target, now);
        checkStuck(level, id, state, rt, ship, c);
    }

    private static void steer(ServerLevel level, BlockPos helm, SailingRuntime rt, ShipBody ship, Course c, Vec3 pos, Vec3 target, long now) {
        double heading = rt.headingDegrees(ship);
        double yawRate = Double.isNaN(c.lastHeading) ? 0.0 : CourseKeeper.yawRate(c.lastHeading, heading, now - c.lastHeadingTick);
        c.lastHeading = heading;
        c.lastHeadingTick = now;
        double wheel = rt.wheelAngle();
        if (ShipControls.playerAtWheel(level, helm)) {
            c.lastWheel = wheel; // the player steers; when he lets go the helmsman takes over from there
            return;
        }
        if (!Double.isNaN(c.lastWheel) && Math.abs(wheel - c.lastWheel) > 0.5) {
            c.overrideUntil = now + CourseConfig.MANUAL_OVERRIDE_TICKS.get(); // a click step by a player
            c.lastWheel = wheel;
        }
        if (now < c.overrideUntil) {
            return;
        }
        double bearing = CourseKeeper.bearing(pos.x, pos.z, target.x, target.z);
        c.rudder = CourseKeeper.rudder(heading, bearing, yawRate, SailingConfig.MAX_RUDDER_ANGLE.get(), CourseConfig.params());
        if (ShipControls.setRudderAngle(level, helm, c.rudder) == ShipControls.RudderResult.SET) {
            c.lastWheel = rt.wheelAngle();
        }
    }

    private static void checkStuck(ServerLevel level, UUID id, StationState<Object> state, SailingRuntime rt, ShipBody ship, Course c) {
        Vector3d v = ship.linearVelocity();
        double speed = Math.hypot(v.x, v.z);
        if (rt.unfurledCount() > 0 && speed < CourseConfig.STUCK_SPEED.get()) {
            c.slowTicks += CourseConfig.UPDATE_INTERVAL_TICKS.get();
            if (c.slowTicks >= CourseConfig.STUCK_TICKS.get() && !c.stuckReported) {
                c.stuckReported = true;
                say(level, id, state, Component.translatable(KEY_STUCK));
                fire(level, id, CourseEvent.Type.STUCK, c.index);
            }
        } else {
            c.slowTicks = 0;
            c.stuckReported = false;
        }
    }

    // ------------------------------------------------------------------ helpers and life cycle

    /** The world position of the center of the ship's plot bounds (valid right after assembly). */
    static Vec3 center(ShipBody ship) {
        BlockPos[] b = ship.plotBounds();
        Vec3 lo = Vec3.atLowerCornerOf(b[0]);
        Vec3 hi = Vec3.atLowerCornerOf(b[1]).add(1, 1, 1);
        return ship.toWorld(lo.add(hi).scale(0.5));
    }

    private static @Nullable UUID crewOccupant(StationState<Object> state) {
        StationState.Occupant o = state.occupant();
        return o == null || o.player() ? null : o.id();
    }

    private static void say(ServerLevel level, UUID ship, StationState<Object> state, Component line) {
        UUID id = crewOccupant(state);
        if (id != null && level.getEntity(id) instanceof CrewMember crew) {
            CrewStations.say(level, crew, line);
            Spoken spoken = new Spoken(ship, line);
            for (Consumer<Spoken> l : SPOKEN) {
                try {
                    l.accept(spoken);
                } catch (RuntimeException ex) {
                    Constants.LOG.error("Helm line listener failed on {}", spoken, ex);
                }
            }
        }
    }

    private static void fire(ServerLevel level, UUID ship, CourseEvent.Type type, int waypoint) {
        CourseEvent event = new CourseEvent(level, ship, type, waypoint);
        for (Consumer<CourseEvent> l : LISTENERS) {
            try {
                l.accept(event);
            } catch (RuntimeException ex) {
                Constants.LOG.error("Course listener failed on {}", event, ex);
            }
        }
    }

    /** A ship left the level; a ship destroyed for good takes its course along. */
    public static void onShipRemoved(ServerLevel level, UUID ship, boolean destroyed) {
        if (destroyed) {
            COURSES.remove(ship);
        }
    }

    public static void onServerStopped() {
        COURSES.clear();
    }
}
