package com.richardsenger.piratesnships.combat.grapple;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GR7: the ranges of a hook thrown by hand at the default {@code hand_throw_velocity} 0.7 and {@code gravity} 0.02 (the
 * human: "when thrown with hand, it goes WAY too far"), and the musket's unchanged flight.
 */
class HookFlightTest {

    private static final double HAND = 0.7;
    private static final double GRAVITY = 0.02;

    @Test
    void theFlightModelMatchesTheMeasuredGr4Throw() {
        // GR4 measured a level throw at 3.0 landing 39.4 blocks away in a GameTest: the model reproduces it
        assertEquals(39.4, HookFlight.landingDistance(3.0, 0, GRAVITY, HookFlight.THROW_START_HEIGHT), 0.3);
    }

    @Test
    void aLevelHandThrowCarriesEightToTenBlocks() {
        double level = HookFlight.landingDistance(HAND, 0, GRAVITY, HookFlight.THROW_START_HEIGHT);
        assertTrue(level >= 8.0 && level <= 10.0, "a level hand throw lands " + level + " blocks away");
    }

    @Test
    void theFarthestLobStaysWellInsideTheRope() {
        double far = HookFlight.farthest(HAND, GRAVITY, HookFlight.THROW_START_HEIGHT);
        assertTrue(far >= 18.0 && far <= 22.0, "the farthest hand lob lands " + far + " blocks away");
        assertTrue(far < 32.0 * 0.75, "a hand lob reaches most of the 32-block rope: " + far);
    }

    @Test
    void aLobReachesARailFromAlongside() {
        // a ship lying alongside, its rail 6 blocks off and 2 above the thrower's feet: a 30 degree lob comes down past it
        double lob = HookFlight.landingDistance(HAND, 30, GRAVITY, HookFlight.THROW_START_HEIGHT - 2.0);
        assertTrue(lob >= 6.0, "a 30 degree lob comes down " + lob + " blocks out, short of a rail 6 blocks away");
    }

    @Test
    void aThrowAimedAtARailTwoUpAndFourAwayArrivesThere() {
        // aimed from the hand-throw start straight at a rail 2 blocks above the feet, 4 blocks away
        double dy = 2.0 - HookFlight.THROW_START_HEIGHT;
        double y = HookFlight.heightAtAimedPoint(HAND, 4.0, dy, GRAVITY);
        assertFalse(Double.isNaN(y), "the throw never gets to the rail");
        assertTrue(Math.abs(y - dy) < 0.5, "the throw passes the rail at " + y + ", aimed at " + dy);
    }

    @Test
    void theMusketShotStillFliesPastItsRope() {
        double shot = GrappleLaunch.speed(GrappleLaunch.Mode.MUSKET, HAND, 3.0, 1.6);
        double level = HookFlight.landingDistance(shot, 0, GRAVITY * 0.5, HookFlight.THROW_START_HEIGHT);
        assertEquals(83.4, level, 0.6);
        assertTrue(level > 64.0 * 1.1, "the musket shot falls short of its 64-block rope: " + level);
    }

    @Test
    void aSlowerThrowFallsShorter() {
        double prev = 0;
        for (double v = 0.3; v <= 1.5; v += 0.2) {
            double d = HookFlight.landingDistance(v, 0, GRAVITY, HookFlight.THROW_START_HEIGHT);
            assertTrue(d > prev, "range does not grow with speed at " + v);
            prev = d;
        }
    }
}
