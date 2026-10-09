package com.richardsenger.piratesnships.ship.hull;

import com.richardsenger.piratesnships.ship.hull.flooding.FloodParams;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodSimulation;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodTickInput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CMP1: the compartments of a hull are a property of its blocks, not of its tilt (docs/design.md §4.2).
 *
 * <p>The hull that flipped before the fix: a hold under a solid deck with an open hatchway (plain air, no hatch
 * block) at one end, and a bulkhead across the hold one block lower than the deck, so the layer under the deck joins
 * the two rooms. Upright, the hatchway is the hold's pour point and the whole hold is one compartment. Pitched with the
 * hatchway end down, the cells under the deck at the far end rise above the hatchway and stopped being basin cells:
 * at 6° the far room lost cells, at 10° the gap over the bulkhead dropped out and the hold split in two. The second
 * hull is the rolling case: the open deck well inside a bulwark one block high is a basin one layer deep, and a
 * deckhouse across it leaves a one-block passage at one side. Rolled 10° with that side up, the passage rose above the
 * low bulwark and the well split in two; rolled the other way it stayed one.
 */
class CompartmentTiltTest {

    static final double[] ANGLES = {0, 3, 6, 10};

    /** Hull 24 × 7 × 7: floor y 0, deck y 4, hatchway (air) at x 1 z 1 in the deck, bulkhead x 15 from y 1 to 2. */
    static HullGrid lowBulkheadHold() {
        HullGrid.Builder b = HullGrid.builder(24, 7, 7);
        b.fill(0, 0, 0, 23, 4, 6, CellKind.SOLID);
        b.fill(1, 1, 1, 22, 3, 5, CellKind.AIR);
        b.fill(15, 1, 1, 15, 2, 5, CellKind.SOLID); // bulkhead, one block lower than the deck
        b.set(1, 4, 1, CellKind.AIR); // open hatchway
        return b.build();
    }

    /** Deck well 17 × 5 × 11: deck y 0, bulwark one block high, deckhouse x 8 across z 1..8, passage at z 9. */
    static HullGrid deckWell() {
        HullGrid.Builder b = HullGrid.builder(17, 5, 11);
        b.fill(0, 0, 0, 16, 1, 10, CellKind.SOLID);
        b.fill(1, 1, 1, 15, 1, 9, CellKind.AIR);
        b.fill(8, 1, 1, 8, 2, 8, CellKind.SOLID); // deckhouse
        return b.build();
    }

    static HullVec pitch(double degrees) {
        double r = Math.toRadians(degrees);
        return new HullVec(Math.sin(r), Math.cos(r), 0); // the far end (+x) rises
    }

    static HullVec roll(double degrees) {
        double r = Math.toRadians(degrees);
        return new HullVec(0, Math.cos(r), Math.sin(r)); // the far side (+z) rises
    }

    /** The partition as text: per compartment its id and its cell set. */
    static List<String> partition(HullAnalysis a) {
        List<String> out = new ArrayList<>();
        for (Compartment c : a.compartments()) {
            out.add(c.id() + ":" + c.cells());
        }
        return out;
    }

    static String summary(HullAnalysis a) {
        StringBuilder s = new StringBuilder().append(a.compartments().size()).append(" compartments, cells");
        for (Compartment c : a.compartments()) s.append(' ').append(c.volume());
        return s.toString();
    }

    @Test
    void lowBulkheadHoldKeepsItsPartitionAtEveryPitchAndRoll() {
        HullAnalysis upright = HullAnalyzer.analyze(lowBulkheadHold());
        assertEquals(1, upright.compartments().size(), "the gap over the bulkhead joins the two rooms");
        assertEquals(22 * 3 * 5 - 2 * 5 + 1, upright.compartments().get(0).volume(), "the hold and the hatchway cell");
        assertStableUnderTilt(upright);
    }

    @Test
    void deckWellKeepsItsPartitionAtEveryPitchAndRoll() {
        HullAnalysis upright = HullAnalyzer.analyze(deckWell());
        assertEquals(1, upright.compartments().size(), "the passage beside the deckhouse joins the well");
        assertEquals(15 * 9 - 8, upright.compartments().get(0).volume());
        assertStableUnderTilt(upright);
    }

    private static void assertStableUnderTilt(HullAnalysis upright) {
        HullGrid grid = upright.grid();
        List<String> table = new ArrayList<>();
        for (double deg : ANGLES) {
            for (String axis : new String[]{"pitch", "roll"}) {
                for (double sign : new double[]{1, -1}) {
                    HullVec up = axis.equals("pitch") ? pitch(sign * deg) : roll(sign * deg);
                    table.add(axis + " " + sign * deg + "°: " + summary(HullAnalyzer.analyze(grid, up)));
                }
            }
        }
        System.out.println("CMP1 partitions\n" + String.join("\n", table));
        for (double deg : ANGLES) {
            for (HullVec up : new HullVec[]{pitch(deg), roll(deg), pitch(-deg), roll(-deg)}) {
                HullAnalysis tilted = HullAnalyzer.analyze(grid, up);
                assertEquals(partition(upright), partition(tilted), "partition at " + up);
                for (int i = 0; i < grid.cellCount(); i++) {
                    assertEquals(upright.compartmentOf(i), tilted.compartmentOf(i));
                }
                assertTrue(upright.samePartition(tilted));
                // a tilt refresh of the upright analysis is the same as a fresh analysis at that tilt
                assertTrue(upright.retilted(up).sameStructure(tilted), "retilt at " + up);
                assertEquals(up.normalized(), tilted.up());
            }
        }
    }

    @Test
    void heightsAndSillsStillFollowTheTilt() {
        HullAnalysis upright = HullAnalyzer.analyze(lowBulkheadHold());
        HullAnalysis pitched = upright.retilted(pitch(10));
        Compartment a = upright.compartments().get(0), b = pitched.compartments().get(0);
        assertEquals(a.cells(), b.cells());
        assertNotEquals(a.profile().top(), b.profile().top(), 1e-6);
        // the hatchway's pour point: one port upright, its sill moves with the pitch
        assertEquals(1, a.ports().size());
        double s = b.ports().get(0).sill();
        assertEquals(pitch(10).dot(new HullVec(1.5, 5.5, 1.5)) - 0.5, s, 1e-9);
    }

    @Test
    void tiltRefreshKeepsEveryCompartmentsWater() {
        HullGrid grid = lowBulkheadHold().toBuilder().fill(15, 3, 1, 15, 3, 5, CellKind.SOLID).build();
        FloodSimulation sim = new FloodSimulation(HullAnalyzer.analyze(grid), FloodParams.DEFAULTS);
        assertEquals(2, sim.analysis().compartments().size(), "a full bulkhead makes two rooms");
        sim.setVolume(0, 40.123456789);
        sim.setVolume(1, 7.5);
        for (double deg : new double[]{3, -6, 10, -10, 0}) {
            HullAnalysis before = sim.analysis();
            sim.retilt(before.retilted(roll(deg)));
            assertEquals(40.123456789, sim.volume(0));
            assertEquals(7.5, sim.volume(1));
            assertEquals(0, sim.rebind(HullAnalyzer.analyze(grid, pitch(deg))), "same partition: nothing lost");
            assertEquals(40.123456789, sim.volume(0));
            assertEquals(7.5, sim.volume(1));
            sim.tick(FloodTickInput.calm(-100));
        }
        assertThrows(IllegalArgumentException.class, () -> sim.retilt(HullAnalyzer.analyze(lowBulkheadHold())));
    }
}
