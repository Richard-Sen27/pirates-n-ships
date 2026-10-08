package com.richardsenger.piratesnships.ship.hull.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.ship.hull.CellKind;
import com.richardsenger.piratesnships.ship.hull.HullAnalysis;
import com.richardsenger.piratesnships.ship.hull.HullAnalyzer;
import com.richardsenger.piratesnships.ship.hull.HullGrid;
import com.richardsenger.piratesnships.ship.hull.HullVec;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodParams;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodSimulation;
import org.junit.jupiter.api.Test;

/** FLD1b: when an eye is under a compartment's flood water, who keeps breathing, and how the air runs out. */
class FloodBreathTest {

    private static final int OX = 29_000_000;

    /** A closed box at plot x {@code OX}: one 3×2×3 compartment at x OX+2..4, y 2..3, z 2..4 (floor at y=2). */
    private static FloodSimulation box() {
        HullGrid.Builder b = HullGrid.builder(7, 6, 7).origin(OX, 0, 0);
        b.fill(0, 0, 0, 6, 5, 6, CellKind.AIR);
        b.fill(1, 1, 1, 5, 4, 5, CellKind.SOLID);
        b.fill(2, 2, 2, 4, 3, 4, CellKind.AIR);
        HullAnalysis a = HullAnalyzer.analyze(b.build());
        return new FloodSimulation(a, FloodParams.DEFAULTS);
    }

    @Test
    void eyeBelowTheLevelInAStillDryCellIsUnder() {
        FloodSimulation sim = box();
        sim.setVolume(0, 12.6); // level 1.4 above the floor: the top layer's centre (1.5) is still dry
        assertEquals(2 + 1.4, sim.level(0), 1e-9);
        assertFalse(sim.floodedCells(0).get(sim.analysis().grid().index(3, 3, 3)), "the top cell is dry for the server");
        // a crouching eye (1.27 above the floor) in the top cell, far out in plot space
        assertEquals(0, FloodBreath.floodedCompartmentAt(sim, OX + 3.5, 2 + 1.27, 3.5));
        // a standing eye (1.62) is above the water
        assertEquals(-1, FloodBreath.floodedCompartmentAt(sim, OX + 3.5, 2 + 1.62, 3.5));
    }

    @Test
    void levelBelowTheEyeOrOutsideTheCompartmentDrownsNobody() {
        FloodSimulation sim = box();
        sim.setVolume(0, 9.0); // level 1.0: the bottom layer full
        assertEquals(-1, FloodBreath.floodedCompartmentAt(sim, OX + 3.5, 2 + 1.27, 3.5), "eye above the water");
        assertEquals(0, FloodBreath.floodedCompartmentAt(sim, OX + 3.5, 2 + 0.9, 3.5), "eye in the bottom layer");
        assertEquals(-1, FloodBreath.floodedCompartmentAt(sim, OX + 1.5, 2 + 0.5, 3.5), "in the hull wall");
        assertEquals(-1, FloodBreath.floodedCompartmentAt(sim, OX + 0.5, 2 + 0.5, 3.5), "outside the hull");
        assertEquals(-1, FloodBreath.floodedCompartmentAt(sim, OX + 100, 2 + 0.5, 3.5), "outside the grid");
    }

    @Test
    void aFilmOfWaterDrownsNobody() {
        FloodSimulation sim = box();
        sim.setVolume(0, FloodBreath.MIN_VOLUME / 2);
        assertEquals(-1, FloodBreath.floodedCompartmentAt(sim, OX + 3.5, 2.0001, 3.5));
        sim.setVolume(0, 0);
        assertEquals(-1, FloodBreath.floodedCompartmentAt(sim, OX + 3.5, 2.0001, 3.5));
    }

    @Test
    void levelIsMeasuredAlongUp() {
        HullVec tilted = new HullVec(0.6, 0.8, 0);
        // up · (1, 2, 0) = 0.6 + 1.6 = 2.2
        assertTrue(FloodBreath.isBelow(tilted, 1, 2, 0, 2.3));
        assertFalse(FloodBreath.isBelow(tilted, 1, 2, 0, 2.2), "on the surface is not under");
        assertFalse(FloodBreath.isBelow(tilted, 1, 2, 0, 2.1));
    }

    @Test
    void whoKeepsBreathing() {
        assertFalse(FloodBreath.canBreathe(false, false, false, 0, false), "a plain player drowns");
        assertTrue(FloodBreath.canBreathe(true, false, false, 0, false), "#can_breathe_under_water");
        assertTrue(FloodBreath.canBreathe(false, true, false, 10_000, false), "Water Breathing or Conduit Power");
        assertTrue(FloodBreath.canBreathe(false, false, false, 0, true), "creative and spectator");
        // the turtle helmet's renewed effect lasts its 10 seconds under the flood water, as in the sea
        assertTrue(FloodBreath.canBreathe(false, true, true, FloodBreath.TURTLE_HELMET_TICKS - 1, false));
        assertFalse(FloodBreath.canBreathe(false, true, true, FloodBreath.TURTLE_HELMET_TICKS, false));
    }

    @Test
    void neverDrainsTwice() {
        assertTrue(FloodBreath.drains(true, false, false));
        assertFalse(FloodBreath.drains(true, true, false), "vanilla already drains in the world's water");
        assertFalse(FloodBreath.drains(false, false, false), "head above the water");
        assertFalse(FloodBreath.drains(true, false, true), "can breathe");
    }

    @Test
    void respirationRollMatchesVanilla() {
        assertTrue(FloodBreath.consumes(0, 0.99), "no bonus: every tick costs air");
        // Respiration III: oxygen bonus 3, a tick costs air with probability 1/4
        assertTrue(FloodBreath.consumes(3, 0.24));
        assertFalse(FloodBreath.consumes(3, 0.25));
    }

    @Test
    void airUndoesTheRefillAndDrownsAtMinusTwenty() {
        FloodBreath.Step s = FloodBreath.step(300, FloodBreath.NO_LAST, true);
        assertEquals(new FloodBreath.Step(299, false), s);
        // vanilla refilled 4 since (it sees a dry eye): carried on from 299
        assertEquals(new FloodBreath.Step(298, false), FloodBreath.step(300, 299, true));
        // something else took more air: kept
        assertEquals(new FloodBreath.Step(99, false), FloodBreath.step(100, 299, true));
        assertEquals(new FloodBreath.Step(298, false), FloodBreath.step(302, 298, false), "a Respiration tick costs nothing");
        // spent: down to -19, then -20 hurts and resets to 0, like vanilla
        assertEquals(new FloodBreath.Step(-19, false), FloodBreath.step(-14, -18, true));
        assertEquals(new FloodBreath.Step(0, true), FloodBreath.step(-15, -19, true));
        assertEquals(new FloodBreath.Step(-1, false), FloodBreath.step(4, 0, true));
    }

    @Test
    void fromFullAirTheFirstHitComesAfterVanillasThreeHundredAndTwentyTicks() {
        int air = 300, last = FloodBreath.NO_LAST, t = 0;
        while (true) {
            t++;
            FloodBreath.Step s = FloodBreath.step(Math.min(air + 4, 300), last, true); // vanilla's refill each tick
            air = last = s.air();
            if (s.drown()) {
                break;
            }
        }
        assertEquals(320, t);
    }
}
