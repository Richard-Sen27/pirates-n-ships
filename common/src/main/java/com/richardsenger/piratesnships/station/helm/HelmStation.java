package com.richardsenger.piratesnships.station.helm;

import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.ship.assembly.ShipHelm;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The helm as a station (WS3a, docs/design.md §6 "Helm: steer (players; crew only in hold course mode)"): a crew
 * member at the ship's steering helm carries out a {@link CourseOrder}. The steering itself is continuous and lives in
 * {@link HelmCourses}, which every {@code update_interval_ticks} reads the helm station's order and turns the rudder;
 * the station's work time is only an estimate of the course's duration, and a course that is not done when it runs out
 * is taken up again ({@link #complete}). The block is {@code ship.assembly.HelmBlock}.
 *
 * <p>A second helm (HL1) does not steer, so it takes no course ({@code NOT_APPLICABLE}); neither does any helm while
 * {@code crew_stations.course.enabled} or {@code sailing_runtime.steering_enabled} is off.
 */
public final class HelmStation implements StationKind<CourseOrder> {

    public static final HelmStation INSTANCE = new HelmStation();

    /** Speed the work-time estimate assumes [blocks per second]. */
    static final double ESTIMATE_SPEED = 1.0;
    static final int MIN_TICKS = 100;
    static final int MAX_TICKS = 24000;

    private HelmStation() {
    }

    @Override
    public String id() {
        return "helm";
    }

    @Override
    public Class<CourseOrder> orderType() {
        return CourseOrder.class;
    }

    @Override
    public int durationTicks(ServerLevel level, StationRef station, CourseOrder order) {
        if (!CourseConfig.ENABLED.get() || !SailingConfig.STEERING_ENABLED.get() || !steers(level, station)) {
            return -1;
        }
        if (order.waypoints().isEmpty()) {
            return 0;
        }
        return CourseKeeper.estimateTicks(HelmCourses.remainingDistance(level, station.ship(), order), ESTIMATE_SPEED, MIN_TICKS, MAX_TICKS);
    }

    /** The estimate ran out before the course was done: carry on with the same course. */
    @Override
    public void complete(ServerLevel level, StationRef station, CourseOrder order) {
        if (HelmCourses.isActive(station.ship(), order)) {
            Stations.order(level, station, order);
        }
    }

    /** Whether the helm at {@code station} is its ship's steering helm (HL1). */
    static boolean steers(ServerLevel level, StationRef station) {
        ShipBody ship = SableShips.byId(level, station.ship());
        if (ship == null) {
            return false;
        }
        BlockPos steering = ShipHelm.steering(ship);
        return station.pos().equals(steering);
    }
}
