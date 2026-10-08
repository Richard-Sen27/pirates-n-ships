package com.richardsenger.piratesnships.station.helm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CourseKeeperTest {

    private static final CourseKeeper.Params P = new CourseKeeper.Params(2.0, 3.0, 1.5);
    private static final double MAX = 35.0;
    private static final double EPS = 1e-9;

    @Test
    void errorWrapsToTheShortWayRound() {
        assertEquals(10.0, CourseKeeper.error(350.0, 0.0), EPS);
        assertEquals(-10.0, CourseKeeper.error(0.0, 350.0), EPS);
        assertEquals(180.0, CourseKeeper.error(0.0, 180.0), EPS);
        assertEquals(180.0, CourseKeeper.error(180.0, 0.0), EPS, "exactly astern counts as starboard (−180 excluded)");
        assertEquals(-90.0, CourseKeeper.error(90.0, 0.0), EPS);
        assertEquals(0.0, CourseKeeper.error(720.0, 0.0), EPS);
    }

    @Test
    void bearingIsACompassBearing() {
        assertEquals(0.0, CourseKeeper.bearing(0, 0, 0, -10), EPS, "north is −z");
        assertEquals(90.0, CourseKeeper.bearing(0, 0, 10, 0), EPS, "east is +x");
        assertEquals(180.0, CourseKeeper.bearing(0, 0, 0, 10), EPS);
        assertEquals(270.0, CourseKeeper.bearing(0, 0, -10, 0), EPS);
        assertEquals(45.0, CourseKeeper.bearing(5, 5, 15, -5), EPS);
    }

    @Test
    void rudderHasTheSignOfTheErrorAndIsProportional() {
        assertEquals(20.0, CourseKeeper.rudder(0.0, 10.0, 0.0, MAX, P), EPS, "bearing to starboard: starboard rudder, gain 2");
        assertEquals(-20.0, CourseKeeper.rudder(0.0, 350.0, 0.0, MAX, P), EPS, "bearing to port: port rudder");
        assertEquals(8.0, CourseKeeper.rudder(90.0, 94.0, 0.0, MAX, P), EPS, "just outside the deadband");
    }

    @Test
    void rudderIsClampedToTheMaximum() {
        assertEquals(MAX, CourseKeeper.rudder(180.0, 270.0, 0.0, MAX, P), EPS);
        assertEquals(-MAX, CourseKeeper.rudder(180.0, 90.0, 0.0, MAX, P), EPS);
        assertEquals(MAX, CourseKeeper.rudder(180.0, 0.0, 0.0, MAX, P), EPS, "a point dead astern: hard over, to starboard");
        assertEquals(0.0, CourseKeeper.rudder(0.0, 90.0, 0.0, 0.0, P), EPS, "no rudder to give");
    }

    @Test
    void deadbandLeavesTheRudderMidships() {
        assertEquals(0.0, CourseKeeper.rudder(100.0, 103.0, 0.0, MAX, P), EPS);
        assertEquals(0.0, CourseKeeper.rudder(100.0, 97.0, 0.0, MAX, P), EPS);
        assertEquals(0.0, CourseKeeper.rudder(100.0, 101.0, 1.0, MAX, P), EPS, "a slow swing inside the band");
        CourseKeeper.Params none = new CourseKeeper.Params(2.0, 0.0, 0.0);
        assertEquals(2.0, CourseKeeper.rudder(100.0, 101.0, 0.0, MAX, none), EPS, "without a deadband every error counts");
    }

    @Test
    void yawRateEasesTheRudderBeforeTheBowPointsAtTheBearing() {
        // 10° to go, swinging toward it at 4°/s: predicted error 10 − 6 = 4 → 8° of rudder instead of 20°
        assertEquals(8.0, CourseKeeper.rudder(0.0, 10.0, 4.0, MAX, P), EPS);
        // swinging fast: counter-rudder before the bearing is reached
        assertTrue(CourseKeeper.rudder(0.0, 5.0, 10.0, MAX, P) < 0.0, "counter-rudder against an overshoot");
        // swinging away from the bearing: more rudder
        assertEquals(MAX, CourseKeeper.rudder(0.0, 10.0, -8.0, MAX, P), EPS);
        // in the deadband but swinging hard out of it: the rudder already checks the swing
        assertTrue(CourseKeeper.rudder(0.0, 0.0, 5.0, MAX, P) < 0.0);
        CourseKeeper.Params blind = new CourseKeeper.Params(2.0, 3.0, 0.0);
        assertEquals(20.0, CourseKeeper.rudder(0.0, 10.0, 4.0, MAX, blind), EPS, "anticipation 0: pure P control");
    }

    @Test
    void badInputsGiveMidships() {
        assertEquals(0.0, CourseKeeper.rudder(Double.NaN, 10.0, 0.0, MAX, P), EPS);
        assertEquals(20.0, CourseKeeper.rudder(0.0, 10.0, Double.NaN, MAX, P), EPS, "an unknown yaw rate counts as 0");
    }

    @Test
    void closedLoopSettlesOnTheBearingWithoutRunningAway() {
        // a crude yaw model: the bow turns at k·rudder °/s with first-order lag, as a slow hull does
        double heading = 180.0, rate = 0.0, bearing = 240.0, k = 0.3, tau = 2.0, dt = 0.25;
        double maxError = 0.0;
        for (int i = 0; i < 400; i++) {
            double r = CourseKeeper.rudder(heading, bearing, rate, MAX, P);
            rate += (k * r - rate) * dt / tau;
            heading = (heading + rate * dt) % 360.0;
            if (i > 200) maxError = Math.max(maxError, Math.abs(CourseKeeper.error(heading, bearing)));
        }
        assertTrue(maxError < 5.0, "heading still off by " + maxError + "° after 50 s");
    }

    @Test
    void yawRateFromTwoHeadings() {
        assertEquals(4.0, CourseKeeper.yawRate(358.0, 0.0, 10), EPS, "2° across north in half a second");
        assertEquals(-4.0, CourseKeeper.yawRate(0.0, 358.0, 10), EPS);
        assertEquals(0.0, CourseKeeper.yawRate(10.0, 20.0, 0), EPS);
        assertEquals(0.0, CourseKeeper.yawRate(Double.NaN, 20.0, 5), EPS);
    }

    @Test
    void arrivalIsAHorizontalRadius() {
        assertTrue(CourseKeeper.arrived(6.0, 5.0, 8.0));
        assertTrue(CourseKeeper.arrived(8.0, 0.0, 8.0));
        assertFalse(CourseKeeper.arrived(6.0, 6.0, 8.0));
    }

    @Test
    void estimateIsClamped() {
        assertEquals(400, CourseKeeper.estimateTicks(20.0, 1.0, 100, 24000));
        assertEquals(100, CourseKeeper.estimateTicks(1.0, 1.0, 100, 24000));
        assertEquals(24000, CourseKeeper.estimateTicks(1e9, 1.0, 100, 24000));
        assertEquals(400, CourseKeeper.estimateTicks(20.0, 0.0, 100, 24000), "no speed: 1 block per second");
    }

    @Test
    void courseCommandArgumentParses() {
        Vec3 origin = new Vec3(100.0, 64.0, -50.0);
        Optional<CourseOrder> one = CourseOrder.parse("120 -40", origin);
        assertTrue(one.isPresent());
        assertEquals(List.of(new Vec3(120.0, 64.0, -40.0)), one.get().waypoints());
        assertFalse(one.get().loop());

        CourseOrder two = CourseOrder.parse(" ~10 ~  ~ ~-20 loop ", origin).orElseThrow();
        assertEquals(List.of(new Vec3(110.0, 64.0, -50.0), new Vec3(100.0, 64.0, -70.0)), two.waypoints());
        assertTrue(two.loop());

        assertTrue(CourseOrder.parse("120", origin).isEmpty(), "odd count");
        assertTrue(CourseOrder.parse("120 north", origin).isEmpty(), "not a number");
        assertTrue(CourseOrder.parse("loop", origin).isEmpty(), "no waypoint");
        assertTrue(CourseOrder.parse("1 2 3", origin).isEmpty());
        assertTrue(CourseOrder.parse("NaN 2", origin).isEmpty());
    }

    @Test
    void courseOrderIsACrewOrderWithItsOwnId() {
        CourseOrder o = CourseOrder.to(new Vec3(1, 2, 3));
        assertEquals("hold_course", o.id());
        assertEquals(o, CourseOrder.to(new Vec3(1, 2, 3)), "equal waypoints are the same course");
        assertFalse(o.loop());
    }
}
