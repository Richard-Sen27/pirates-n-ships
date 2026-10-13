package com.richardsenger.piratesnships.combat.grapple;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GR7, GR8: the ranges of a hook thrown by hand at the default {@code hand_throw_velocity} 0.65 and
 * {@code hand_throw_gravity} 0.05 (GR8, the human: "it kinda falls down after a certain range, which doesn't make it
 * look natural, because it starts off way too fast": one plain arc like a thrown trident, a 45 degree throw landing 8
 * to 10 blocks away), and the musket's unchanged flight ({@code grapple.gravity} 0.02 times 0.5).
 */
class HookFlightTest {

    private static final double HAND = 0.65;
    private static final double HAND_GRAVITY = 0.05;
    /** GR7's hand throw, for the comparison. */
    private static final double GR7_HAND = 0.7;
    private static final double GRAVITY = 0.02;

    @Test
    void theFlightModelMatchesTheMeasuredGr4Throw() {
        // GR4 measured a level throw at 3.0 landing 39.4 blocks away in a GameTest: the model reproduces it
        assertEquals(39.4, HookFlight.landingDistance(3.0, 0, GRAVITY, HookFlight.THROW_START_HEIGHT), 0.3);
    }

    @Test
    void aFortyFiveDegreeHandThrowLandsEightToTenBlocksAway() {
        double lob = HookFlight.landingDistance(HAND, 45, HAND_GRAVITY, HookFlight.THROW_START_HEIGHT);
        assertTrue(lob >= 8.0 && lob <= 10.0, "a 45 degree hand throw lands " + lob + " blocks away");
        double level = HookFlight.landingDistance(HAND, 0, HAND_GRAVITY, HookFlight.THROW_START_HEIGHT);
        assertTrue(level >= 4.5 && level <= 6.5, "a level hand throw lands " + level + " blocks away");
    }

    @Test
    void theFarthestLobStaysWellInsideTheRope() {
        double far = HookFlight.farthest(HAND, HAND_GRAVITY, HookFlight.THROW_START_HEIGHT);
        assertTrue(far >= 8.0 && far <= 11.0, "the farthest hand lob lands " + far + " blocks away");
        assertTrue(far < 32.0 * 0.5, "a hand lob reaches half the 32-block rope: " + far);
    }

    @Test
    void theHandThrowNoLongerFloats() {
        // GR7's throw (0.7 at the musket's 0.02) hung in the air for 2.5 s on a 45 degree lob and flew 20 blocks;
        // GR8's comes down in about a second
        int gr7 = ticksToLand(GR7_HAND, 45, GRAVITY);
        int gr8 = ticksToLand(HAND, 45, HAND_GRAVITY);
        assertTrue(gr7 >= 45, "GR7's lob landed after " + gr7 + " ticks");
        assertTrue(gr8 >= 15 && gr8 <= 26, "GR8's lob lands after " + gr8 + " ticks");
    }

    @Test
    void theArcIsOneParabolaWithOneApexAndNoCutOff() {
        double[][] p = HookFlight.path(HAND, 45, HAND_GRAVITY, 60);
        int apexes = 0;
        double prevVx = Double.POSITIVE_INFINITY;
        double prevVy = Double.POSITIVE_INFINITY;
        double[] prev = {0.0, 0.0};
        double prevDvy = Double.NaN;
        for (int t = 0; t < p.length; t++) {
            double vx = p[t][0] - prev[0];
            double vy = p[t][1] - prev[1];
            assertTrue(vx < prevVx && vx > 0.0, "tick " + t + ": the horizontal speed " + vx + " did not shrink smoothly");
            assertTrue(vy < prevVy, "tick " + t + ": the vertical speed " + vy + " did not fall");
            if (t > 0) {
                double dvy = vy - prevVy;
                if (!Double.isNaN(prevDvy)) {
                    // every tick loses about the same: gravity plus 1 % drag, never a sudden drop
                    assertEquals(prevDvy, dvy, 0.01, "tick " + t + ": the fall changed its pace");
                }
                prevDvy = dvy;
            }
            if (t > 0 && prevVy > 0.0 && vy <= 0.0) apexes++;
            prevVx = vx;
            prevVy = vy;
            prev = p[t];
        }
        assertEquals(1, apexes, "the arc has " + apexes + " apexes");
    }

    @Test
    void aThrowAimedAboveARailTwoUpAndFourAwayDropsOntoIt() {
        double dy = 2.0 - HookFlight.THROW_START_HEIGHT;
        double pitch = HookFlight.aimPitch(HAND, 4.0, dy, HAND_GRAVITY);
        assertFalse(Double.isNaN(pitch), "no throw reaches a rail 2 up and 4 away");
        double straight = Math.toDegrees(Math.atan2(dy, 4.0));
        assertTrue(pitch > straight + 3.0 && pitch < 40.0, "aim " + pitch + " degrees for a rail seen at " + straight);
    }

    @Test
    void aLobReachesARailFromAlongside() {
        // a ship lying alongside, its rail 6 blocks off and 2 above the thrower's feet
        double lob = HookFlight.landingDistance(HAND, 40, HAND_GRAVITY, HookFlight.THROW_START_HEIGHT - 2.0);
        assertTrue(lob >= 6.0, "a 40 degree lob comes down " + lob + " blocks out, short of a rail 6 blocks away");
    }

    @Test
    void theMusketShotStillFliesPastItsRope() {
        double shot = GrappleLaunch.speed(GrappleLaunch.Mode.MUSKET, HAND, 3.0, 1.6);
        double level = HookFlight.landingDistance(shot, 0, GrappleLaunch.gravity(GrappleLaunch.Mode.MUSKET, HAND_GRAVITY, GRAVITY, 0.5),
                HookFlight.THROW_START_HEIGHT);
        assertEquals(83.4, level, 0.6);
        assertTrue(level > 64.0 * 1.1, "the musket shot falls short of its 64-block rope: " + level);
    }

    @Test
    void aSlowerThrowFallsShorter() {
        double prev = 0;
        for (double v = 0.3; v <= 1.5; v += 0.2) {
            double d = HookFlight.landingDistance(v, 0, HAND_GRAVITY, HookFlight.THROW_START_HEIGHT);
            assertTrue(d > prev, "range does not grow with speed at " + v);
            prev = d;
        }
    }

    private static int ticksToLand(double speed, double pitch, double gravity) {
        double[][] p = HookFlight.path(speed, pitch, gravity, HookFlight.MAX_TICKS);
        for (int t = 0; t < p.length; t++) {
            if (p[t][1] <= -HookFlight.THROW_START_HEIGHT) return t + 1;
        }
        return HookFlight.MAX_TICKS;
    }
}
