package com.richardsenger.piratesnships.combat.grapple;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RopeSlideTest {

    private static final double EPS = 1e-9;
    private static final RopeSlide.Params P = new RopeSlide.Params(0.35, 0.02, 0.1, 0.8, 1.0);

    // ---- parameter ----

    @Test
    void parameterOfAPointIsItsProjectionOnTheRope() {
        Vec3 a = new Vec3(0, 10, 0);
        Vec3 b = new Vec3(10, 0, 0);
        assertEquals(0.5, RopeSlide.parameter(a, b, new Vec3(5, 5, 3)), EPS);
        assertEquals(0.25, RopeSlide.parameter(a, b, RopeSlide.at(a, b, 0.25).add(0, 0, -2)), EPS);
    }

    @Test
    void parameterIsClampedToTheEnds() {
        Vec3 a = new Vec3(0, 0, 0);
        Vec3 b = new Vec3(10, 0, 0);
        assertEquals(0.0, RopeSlide.parameter(a, b, new Vec3(-5, 1, 0)), EPS);
        assertEquals(1.0, RopeSlide.parameter(a, b, new Vec3(15, 1, 0)), EPS);
        assertEquals(0.0, RopeSlide.parameter(a, a, new Vec3(3, 3, 3)), EPS, "a rope without length");
    }

    // ---- speed by slope ----

    @Test
    void steeperRopesSlideFaster() {
        double gentle = RopeSlide.speed(0, 0.2, false, P);
        double steep = RopeSlide.speed(0, 0.8, false, P);
        assertEquals(0.35 + 0.02 * 0.2, gentle, EPS);
        assertEquals(0.35 + 0.02 * 0.8, steep, EPS);
        assertTrue(steep > gentle);
    }

    @Test
    void speedGrowsEveryTickUpToTheTopSpeed() {
        double v = 0;
        for (int i = 0; i < 10; i++) {
            double next = RopeSlide.speed(v, 0.5, false, P);
            assertEquals(Math.max(v, 0.35) + 0.01, next, EPS);
            v = next;
        }
        for (int i = 0; i < 1000; i++) {
            v = RopeSlide.speed(v, 1.0, false, P);
        }
        assertEquals(0.8, v, EPS, "capped at slide_max_speed");
    }

    @Test
    void aLevelRopeCrawlsTowardTheHook() {
        assertTrue(RopeSlide.level(0.0));
        assertTrue(RopeSlide.level(-0.04));
        assertFalse(RopeSlide.level(0.06));
        assertEquals(0.1, RopeSlide.speed(0.6, 0.01, false, P), EPS);
        assertEquals(1, RopeSlide.direction(0.0));
        assertEquals(1, RopeSlide.direction(0.03), "a hook end barely higher still counts as level");
        RopeSlide.Step s = RopeSlide.step(0.0, 0, 0, 10.0, 5.0, 5.0, P);
        assertEquals(1, s.dir());
        assertEquals(0.01, s.t(), EPS);
    }

    @Test
    void aTurnStartsAgainFromTheSlideSpeed() {
        assertEquals(0.35 + 0.02 * 0.5, RopeSlide.speed(0.7, 0.5, true, P), EPS);
        // moving toward the hook at 0.7, then the hook end rises above the near end
        RopeSlide.Step s = RopeSlide.step(0.5, 0.7, 1, 10.0, 0.0, 5.0, P);
        assertEquals(-1, s.dir());
        assertEquals(0.36, s.speed(), EPS);
    }

    // ---- never uphill ----

    @Test
    void neverUphill() {
        // hook end higher: always toward the near end, wherever the rider is
        for (double t = 0.1; t < 0.95; t += 0.1) {
            RopeSlide.Step s = RopeSlide.step(t, 0.4, 1, 20.0, 0.0, 8.0, P);
            assertEquals(-1, s.dir(), "at t=" + t);
            assertTrue(s.t() < t, "moved uphill at t=" + t);
        }
        // hook end lower: always toward the hook
        for (double t = 0.05; t < 0.9; t += 0.1) {
            RopeSlide.Step s = RopeSlide.step(t, 0.4, -1, 20.0, 8.0, 0.0, P);
            assertEquals(1, s.dir(), "at t=" + t);
            assertTrue(s.t() > t, "moved uphill at t=" + t);
        }
    }

    @Test
    void slideFromTheHighEndReachesTheLowEndInTheExpectedTicks() {
        // a 14 block rope falling 6 blocks toward the hook (the GameTest's crow's nest)
        double len = 14.0;
        double t = 0;
        double v = 0;
        int dir = 0;
        int ticks = 0;
        boolean arrived = false;
        while (!arrived && ticks < 200) {
            RopeSlide.Step s = RopeSlide.step(t, v, dir, len, 6.0, 0.0, P);
            assertTrue(s.t() >= t, "went back");
            t = s.t();
            v = s.speed();
            dir = s.dir();
            arrived = s.arrived();
            ticks++;
        }
        assertTrue(arrived);
        assertTrue(ticks > 25 && ticks < 40, "took " + ticks + " ticks");
        assertTrue((1 - t) * len <= 1.0 + EPS, "lets go within dismount_distance of the hook");
    }

    // ---- dismount ----

    @Test
    void arrivesWithinTheDismountDistanceOfTheFarEnd() {
        RopeSlide.Step before = RopeSlide.step(0.85, 0.35, 1, 10.0, 5.0, 0.0, P); // 1.5 left, moves 0.36
        assertFalse(before.arrived());
        RopeSlide.Step at = RopeSlide.step(0.89, 0.35, 1, 10.0, 5.0, 0.0, P); // 1.1 left, moves 0.36
        assertTrue(at.arrived());
        RopeSlide.Step already = RopeSlide.step(0.95, 0.35, 1, 10.0, 5.0, 0.0, P);
        assertTrue(already.arrived(), "boarding next to the low end lets go at once");
        assertEquals(0.95, already.t(), EPS);
        assertEquals(0.5, RopeSlide.remaining(0.95, 1, 10.0), EPS);
        assertEquals(9.5, RopeSlide.remaining(0.95, -1, 10.0), EPS);
    }

    @Test
    void arrivesAtTheNearEndWhenThatIsLower() {
        RopeSlide.Step s = RopeSlide.step(0.12, 0.35, -1, 10.0, 0.0, 5.0, P);
        assertEquals(-1, s.dir());
        assertTrue(s.arrived());
    }

    @Test
    void aRopeWithoutLengthLetsGo() {
        assertTrue(RopeSlide.step(0.5, 0, 0, 0.2, 0, 0, P).arrived());
    }

    // ---- pick ----

    @Test
    void lookingAtTheRopePicksIt() {
        Vec3 a = new Vec3(0, 5, 0);
        Vec3 b = new Vec3(10, 5, 0);
        Vec3 eye = new Vec3(4, 3.5, -1);
        // look at the rope point (4, 5, 0)
        RopeSlide.Pick p = RopeSlide.pick(eye, new Vec3(0, 1.5, 1), 2.5, a, b, 0.6);
        assertNotNull(p);
        assertEquals(0.4, p.t(), 1e-6);
        assertEquals(0.0, p.rayDistance(), 1e-6);
        assertEquals(Math.sqrt(1.5 * 1.5 + 1), p.eyeDistance(), 1e-6);
    }

    @Test
    void lookingPastTheRopeOrTooFarDoesNotPick() {
        Vec3 a = new Vec3(0, 5, 0);
        Vec3 b = new Vec3(10, 5, 0);
        Vec3 eye = new Vec3(4, 3.5, -1);
        assertNull(RopeSlide.pick(eye, new Vec3(0, 0, 1), 2.5, a, b, 0.6), "looking under the rope");
        assertNotNull(RopeSlide.pick(eye, new Vec3(0, 1.0, 1), 2.5, a, b, 0.6), "just within the pick radius");
        Vec3 far = new Vec3(4, 0, -4);
        assertNull(RopeSlide.pick(far, new Vec3(0, 5, 4), 2.5, a, b, 0.6), "out of reach");
        assertNotNull(RopeSlide.pick(far, new Vec3(0, 5, 4), 7.0, a, b, 0.6), "within a longer reach");
    }

    @Test
    void pickNearTheEndOfTheRopeClampsToIt() {
        Vec3 a = new Vec3(0, 5, 0);
        Vec3 b = new Vec3(10, 5, 0);
        // the thrower stands at the near end and looks along the rope
        RopeSlide.Pick p = RopeSlide.pick(new Vec3(0, 5.45, 0), new Vec3(1, -0.1, 0), 2.5, a, b, 0.6);
        assertNotNull(p);
        assertTrue(p.t() >= 0 && p.t() < 0.3, "t=" + p.t());
        // looking away from the rope, behind its end
        assertNull(RopeSlide.pick(new Vec3(-1, 5.45, 0), new Vec3(-1, 0, 0), 2.5, a, b, 0.6));
        assertNull(RopeSlide.pick(new Vec3(-1, 5.45, 0), Vec3.ZERO, 2.5, a, b, 0.6), "no look direction");
    }
}
