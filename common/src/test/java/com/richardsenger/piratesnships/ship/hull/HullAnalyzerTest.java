package com.richardsenger.piratesnships.ship.hull;

import org.junit.jupiter.api.Test;

import static com.richardsenger.piratesnships.ship.hull.TestHulls.*;
import static org.junit.jupiter.api.Assertions.*;

class HullAnalyzerTest {

    @Test
    void closedBoxIsOneEnclosedCompartment() {
        HullAnalysis a = HullAnalyzer.analyze(box5().build());
        assertEquals(1, a.compartments().size());
        Compartment c = a.compartments().get(0);
        assertEquals(27, c.volume());
        assertTrue(c.ports().isEmpty());
        assertEquals(Double.POSITIVE_INFINITY, a.spillHeight(a.grid().index(2, 2, 2)));
        assertEquals(1, c.minX());
        assertEquals(3, c.maxY());
        assertEquals(1.0, c.profile().bottom(), 1e-12);
        assertEquals(4.0, c.profile().top(), 1e-12);
    }

    @Test
    void openHullHoldsWaterBelowItsRim() {
        HullAnalysis a = HullAnalyzer.analyze(openHull5().build());
        assertEquals(1, a.compartments().size());
        Compartment c = a.compartments().get(0);
        assertEquals(27, c.volume(), "layers y 1..3 are basin, the rim layer y 4 is outside air");
        assertEquals(-1, a.compartmentAt(2, 4, 2));
        assertEquals(1, c.ports().size());
        OutsidePort rim = c.ports().get(0);
        assertTrue(rim.isPourPoint());
        assertEquals(9, rim.area());
        assertEquals(4.0, rim.sill(), 1e-12);
    }

    @Test
    void textDslMatchesBuilder() {
        String wall = "#####/#####/#####/#####/#####";
        String room = "#####/#...#/#...#/#...#/#####";
        HullGrid parsed = parse(wall, room, room, room, wall);
        assertEquals(box5().build(), parsed);
    }

    @Test
    void airHoleInAWallMakesEverythingAboveItOutsideAir() {
        HullAnalysis a = HullAnalyzer.analyze(box5().set(0, 3, 2, CellKind.AIR).build());
        Compartment c = a.compartments().get(0);
        assertEquals(18, c.volume(), "cells below the hole are a basin, the hole layer is outside");
        assertEquals(1, c.ports().size());
        assertEquals(3.0, c.ports().get(0).sill(), 1e-12);
        assertEquals(9, c.ports().get(0).area());
    }

    @Test
    void holeInTheFloorLeavesNoBasin() {
        HullAnalysis a = HullAnalyzer.analyze(box5().set(2, 0, 2, CellKind.AIR).build());
        assertTrue(a.compartments().isEmpty());
    }

    @Test
    void breachIsAPortNotOutsideAir() {
        HullGrid g = box5().breach(0, 1, 2).build();
        HullAnalysis a = HullAnalyzer.analyze(g);
        Compartment c = a.compartments().get(0);
        assertEquals(27, c.volume());
        assertEquals(1, c.ports().size());
        OutsidePort p = c.ports().get(0);
        assertEquals(g.index(0, 1, 2), p.openingCell());
        assertEquals(1.0, p.sill(), 1e-12);
    }

    @Test
    void diagonalGapIsNotALeak() {
        // Remove an edge block of the shell: the interior touches it only along an edge, never a face.
        HullAnalysis a = HullAnalyzer.analyze(box5().set(0, 2, 0, CellKind.AIR).set(4, 4, 4, CellKind.AIR).build());
        assertEquals(1, a.compartments().size());
        assertEquals(27, a.compartments().get(0).volume());
        assertTrue(a.compartments().get(0).ports().isEmpty());
    }

    @Test
    void twoRoomsWithADoorAreTwoLinkedCompartments() {
        HullGrid.Builder b = closedBox(HullGrid.builder(9, 5, 5), 0, 0, 0, 8, 4, 4);
        b.fill(4, 1, 1, 4, 3, 3, CellKind.SOLID);
        b.set(4, 1, 2, CellKind.OPENING, false).set(4, 2, 2, CellKind.OPENING, false);
        HullAnalysis closed = HullAnalyzer.analyze(b.build());
        assertEquals(2, closed.compartments().size());
        assertEquals(27, closed.compartments().get(0).volume());
        assertEquals(27, closed.compartments().get(1).volume());
        assertEquals(2, closed.links().size());
        CompartmentLink low = closed.links().get(0);
        assertEquals(0, low.a());
        assertEquals(1, low.b());
        assertEquals(1.0, low.sill(), 1e-12);

        b.set(4, 1, 2, CellKind.OPENING, true).set(4, 2, 2, CellKind.OPENING, true);
        HullAnalysis open = HullAnalyzer.analyze(b.build());
        assertEquals(closed.links(), open.links(), "door state never changes the analysis");
        assertEquals(closed.compartments().get(0).cells(), open.compartments().get(0).cells());
    }

    @Test
    void hatchInADeckLinksTwoLevels() {
        HullGrid.Builder b = closedBox(HullGrid.builder(5, 9, 5), 0, 0, 0, 4, 8, 4);
        b.fill(1, 4, 1, 3, 4, 3, CellKind.SOLID).set(2, 4, 2, CellKind.OPENING, false);
        HullAnalysis a = HullAnalyzer.analyze(b.build());
        assertEquals(2, a.compartments().size());
        assertEquals(1, a.links().size());
        assertEquals(4.0, a.links().get(0).sill(), 1e-12);
        assertEquals(a.compartmentAt(2, 1, 2), a.links().get(0).a());
        assertEquals(a.compartmentAt(2, 7, 2), a.links().get(0).b());
    }

    @Test
    void hatchToTheOutsideIsAnOpeningPort() {
        HullGrid g = box5().set(2, 4, 2, CellKind.OPENING, true).build();
        HullAnalysis a = HullAnalyzer.analyze(g);
        assertEquals(27, a.compartments().get(0).volume(), "an open hatch does not turn the room into outside air");
        OutsidePort p = a.compartments().get(0).ports().get(0);
        assertEquals(g.index(2, 4, 2), p.openingCell());
        assertEquals(4.0, p.sill(), 1e-12);
    }

    @Test
    void lShapedRoomIsOneCompartment() {
        HullGrid g = parse(
                "######/######/######/######",
                "######/#....#/#.####/######",
                "######/#....#/#.####/######",
                "######/######/######/######");
        HullAnalysis a = HullAnalyzer.analyze(g);
        assertEquals(1, a.compartments().size());
        assertEquals(10, a.compartments().get(0).volume());
    }

    @Test
    void nestedBoxesAreSeparateCompartments() {
        HullGrid.Builder b = closedBox(HullGrid.builder(9, 9, 9), 0, 0, 0, 8, 8, 8);
        closedBox(b, 2, 2, 2, 6, 6, 6);
        HullAnalysis a = HullAnalyzer.analyze(b.build());
        assertEquals(2, a.compartments().size());
        assertEquals(7 * 7 * 7 - 5 * 5 * 5, a.compartments().get(0).volume());
        assertEquals(27, a.compartments().get(1).volume());
    }

    @Test
    void tiltedUpVectorPutsTheLowRimSideFirst() {
        // Heel 40 degrees: the +x rim is lower, so the pour point drops and the basin shrinks.
        HullVec up = new HullVec(-Math.sin(Math.toRadians(40)), Math.cos(Math.toRadians(40)), 0);
        HullAnalysis level = HullAnalyzer.analyze(openHull5().build());
        HullAnalysis tilted = HullAnalyzer.analyze(openHull5().build(), up);
        assertTrue(tilted.compartments().get(0).volume() < level.compartments().get(0).volume());
        assertTrue(tilted.tiltExceeds(HullVec.UP, 30));
        assertFalse(tilted.tiltExceeds(up, 1));
    }

    @Test
    void analysisIsDeterministicAndIndependentOfHowTheGridWasBuilt() {
        HullGrid direct = box5().set(0, 3, 2, CellKind.AIR).build();
        HullGrid edited = box5().build().toBuilder().set(0, 3, 2, CellKind.AIR).build();
        assertTrue(HullAnalyzer.analyze(direct).sameStructure(HullAnalyzer.analyze(edited)));
    }
}
