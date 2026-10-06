package com.richardsenger.piratesnships.ship.hull.flooding;

import com.richardsenger.piratesnships.ship.hull.CellKind;
import com.richardsenger.piratesnships.ship.hull.HullAnalysis;
import com.richardsenger.piratesnships.ship.hull.HullAnalyzer;
import com.richardsenger.piratesnships.ship.hull.HullGrid;
import com.richardsenger.piratesnships.ship.hull.HullVec;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static com.richardsenger.piratesnships.ship.hull.TestHulls.*;
import static org.junit.jupiter.api.Assertions.*;

class FloodSimulationTest {

    private static FloodSimulation sim(HullGrid g) {
        return new FloodSimulation(HullAnalyzer.analyze(g), FloodParams.DEFAULTS);
    }

    private static FloodReport run(FloodSimulation s, FloodTickInput in, int ticks) {
        FloodReport r = null;
        for (int t = 0; t < ticks; t++) r = s.tick(in);
        return r;
    }

    @Test
    void closedBoxStaysDryAndDisplaces() {
        FloodSimulation s = sim(box5().build());
        FloodReport r = run(s, FloodTickInput.calm(10), 1000);
        assertEquals(0, r.floodVolume());
        assertEquals(27, r.submergedDryVolume(), 1e-9);
        assertEquals(new HullVec(2.5, 2.5, 2.5), r.dryCentroid());
        assertNull(r.floodCentroid());
        assertTrue(s.floodedCells(0).isEmpty());
        assertEquals(27, s.dryCells(0).cardinality());
    }

    @Test
    void partlyFloodedOutputs() {
        FloodSimulation s = sim(box5().build());
        s.setVolume(0, 9);
        FloodReport r = s.report(10);
        assertEquals(2.0, r.levels()[0], 1e-9);
        assertEquals(9, s.floodedCells(0).cardinality());
        assertEquals(18, s.dryCells(0).cardinality());
        assertEquals(18, r.submergedDryVolume(), 1e-9);
        assertEquals(1.5, r.floodCentroid().y(), 1e-9);
        assertEquals(3.0, r.dryCentroid().y(), 1e-9);
        // sea at mid-height: only the dry layer between water and sea counts
        assertEquals(9, s.report(3.0).submergedDryVolume(), 1e-9);
    }

    @Test
    void openHullAboveTheRimStaysDry() {
        FloodSimulation s = sim(openHull5().build());
        FloodReport r = run(s, FloodTickInput.calm(3.0), 1000);
        assertEquals(0, r.floodVolume());
        assertEquals(18, r.submergedDryVolume(), 1e-9);
        assertEquals(2.0, r.dryCentroid().y(), 1e-9);
    }

    @Test
    void openHullWithRimBelowTheSeaFloods() {
        FloodSimulation s = sim(openHull5().build());
        FloodReport r = run(s, FloodTickInput.calm(5.0), 2000);
        assertEquals(27, r.floodVolume(), 1e-9);
        assertEquals(0, r.submergedDryVolume(), 1e-9);
    }

    @Test
    void breachBelowTheWaterlineFillsToTheSeaAtTheOrificeRateThenStops() {
        FloodSimulation s = sim(box5().breach(0, 1, 2).build());
        FloodTickInput in = FloodTickInput.calm(3.0);
        FloodReport first = s.tick(in);
        assertEquals(0.05 * Math.sqrt(2), first.inflow(), 1e-12);
        run(s, in, 99);
        // Analytic solution of dV/dt = c·√(W − L), L = 1 + V/9: √(W − L) = √2 − c·t/18
        double root = Math.sqrt(2) - 0.05 * 100 / 18;
        double expected = 9 * (2 - root * root);
        assertEquals(expected, s.volume(0), 0.15);
        FloodReport end = run(s, in, 3000);
        assertEquals(3.0, end.levels()[0], 1e-9);
        assertEquals(18, end.floodVolume(), 1e-6);
        assertEquals(0, s.tick(in).inflow(), 1e-9);
    }

    @Test
    void airHoleBelowTheWaterlineFillsTheBasinBelowIt() {
        FloodSimulation s = sim(box5().set(0, 3, 2, CellKind.AIR).build());
        FloodReport first = s.tick(FloodTickInput.calm(3.5));
        assertEquals(0.05 * 9 * 0.5 * Math.sqrt(0.5), first.inflow(), 1e-12);
        assertEquals(18, run(s, FloodTickInput.calm(3.5), 2000).floodVolume(), 1e-9);
    }

    @Test
    void holeAboveTheWaterlineStaysDry() {
        FloodSimulation s = sim(box5().set(0, 3, 2, CellKind.AIR).build());
        assertEquals(0, run(s, FloodTickInput.calm(2.5), 2000).floodVolume());
        FloodSimulation b = sim(box5().breach(0, 3, 2).build());
        assertEquals(0, run(b, FloodTickInput.calm(2.9), 2000).floodVolume());
    }

    private static HullGrid twoRooms(boolean doorOpen) {
        HullGrid.Builder b = closedBox(HullGrid.builder(9, 5, 5), 0, 0, 0, 8, 4, 4);
        b.fill(4, 1, 1, 4, 3, 3, CellKind.SOLID);
        b.set(4, 1, 2, CellKind.OPENING, doorOpen).set(4, 2, 2, CellKind.OPENING, doorOpen);
        return b.build();
    }

    @Test
    void closedDoorKeepsRoomsApart() {
        FloodSimulation s = sim(twoRooms(false));
        s.setVolume(0, 27);
        FloodReport r = run(s, FloodTickInput.calm(0), 500);
        assertEquals(27, r.volumes()[0]);
        assertEquals(0, r.volumes()[1]);
    }

    @Test
    void openDoorEqualizesMonotonicallyWithoutOscillating() {
        FloodSimulation s = sim(twoRooms(true));
        s.setVolume(0, 27);
        double prevDiff = Double.POSITIVE_INFINITY;
        for (int t = 0; t < 3000; t++) {
            s.tick(FloodTickInput.calm(0));
            double diff = s.level(0) - s.level(1);
            assertTrue(diff >= -1e-9, "levels crossed at tick " + t);
            assertTrue(diff <= prevDiff + 1e-12, "difference grew at tick " + t);
            prevDiff = diff;
        }
        assertEquals(s.level(0), s.level(1), 1e-6);
        assertEquals(13.5, s.volume(0), 1e-5);
        assertEquals(27, s.totalVolume(), 1e-9);
    }

    @Test
    void doorToggleIsLiveAndNeedsNoReanalysis() {
        FloodSimulation s = sim(twoRooms(false));
        HullAnalysis before = s.analysis();
        s.setVolume(0, 27);
        assertTrue(s.setOpen(4, 1, 2, true));
        assertFalse(s.setOpen(3, 1, 2, true), "air is not an opening");
        run(s, FloodTickInput.calm(0), 3000);
        assertSame(before, s.analysis());
        assertEquals(s.level(0), s.level(1), 1e-6);
    }

    private static HullGrid twoDecks(boolean hatchOpen) {
        HullGrid.Builder b = closedBox(HullGrid.builder(5, 9, 5), 0, 0, 0, 4, 8, 4);
        return b.fill(1, 4, 1, 3, 4, 3, CellKind.SOLID).set(2, 4, 2, CellKind.OPENING, hatchOpen).build();
    }

    @Test
    void waterFallsThroughAnOpenHatch() {
        FloodSimulation s = sim(twoDecks(true));
        s.setVolume(1, 9);
        run(s, FloodTickInput.calm(0), 2000);
        assertEquals(0, s.volume(1), 1e-9);
        assertEquals(9, s.volume(0), 1e-9);
    }

    @Test
    void floodedLowerDeckPushesWaterUpThroughAnOpenHatch() {
        FloodSimulation s = sim(twoDecks(true).toBuilder().breach(0, 1, 2).build());
        run(s, FloodTickInput.calm(7.0), 30000);
        assertEquals(27, s.volume(0), 1e-6);
        assertEquals(7.0, s.level(1), 1e-3);
        FloodSimulation closed = sim(twoDecks(false).toBuilder().breach(0, 1, 2).build());
        run(closed, FloodTickInput.calm(7.0), 5000);
        assertEquals(0, closed.volume(1));
    }

    @Test
    void pumpFasterThanInflowKeepsTheHullDry() {
        FloodSimulation s = new FloodSimulation(HullAnalyzer.analyze(box5().breach(0, 1, 2).build()),
                new FloodParams(true, 1, 0.1));
        run(s, FloodTickInput.calm(3.0).withPumps(1), 3000);
        assertEquals(0, s.volume(0), 1e-12);
    }

    @Test
    void pumpSlowerThanInflowSettlesWhereInflowEqualsPumping() {
        FloodSimulation s = new FloodSimulation(HullAnalyzer.analyze(box5().breach(0, 1, 2).build()),
                new FloodParams(true, 1, 0.03));
        FloodReport r = run(s, FloodTickInput.calm(3.0).withPumps(1), 6000);
        // 0.05·√(3 − L) = 0.03  →  L = 2.64
        assertEquals(2.64, r.levels()[0], 0.02);
        assertEquals(r.inflow(), r.pumped(), 1e-3);
    }

    @Test
    void volumeIsConservedOverThousandsOfTicks() {
        // Four rooms in a row with open doors at different heights, a deck hatch, a heeled up vector.
        HullGrid.Builder b = closedBox(HullGrid.builder(17, 9, 5), 0, 0, 0, 16, 8, 4);
        for (int x : new int[]{4, 8, 12}) b.fill(x, 1, 1, x, 7, 3, CellKind.SOLID);
        b.fill(1, 4, 1, 3, 4, 3, CellKind.SOLID).set(2, 4, 2, CellKind.OPENING, true);
        b.set(4, 1, 2, CellKind.OPENING, true).set(8, 3, 2, CellKind.OPENING, true).set(12, 6, 1, CellKind.OPENING, true);
        HullVec up = new HullVec(0.15, 1, 0.05);
        FloodSimulation s = new FloodSimulation(HullAnalyzer.analyze(b.build(), up), FloodParams.DEFAULTS);
        Random rnd = new Random(42);
        for (int c = 0; c < s.analysis().compartments().size(); c++) {
            s.setVolume(c, rnd.nextDouble() * s.analysis().compartments().get(c).volume());
        }
        double total = s.totalVolume();
        for (int t = 0; t < 5000; t++) {
            FloodReport r = s.tick(FloodTickInput.calm(-100));
            assertEquals(0, r.inflow() + r.outflow() + r.pumped());
        }
        assertEquals(total, s.totalVolume(), 1e-9);
    }

    @Test
    void wavesSpillOverALowRimAndTheWaterStays() {
        FloodSimulation s = sim(openHull5().build());
        assertEquals(0, run(s, FloodTickInput.calm(3.8), 200).floodVolume());
        FloodReport wave = run(s, FloodTickInput.calm(3.8).withWaves(0.5), 20);
        assertTrue(wave.floodVolume() > 0.5, "a wave over the rim should spill water in");
        double after = wave.floodVolume();
        assertEquals(after, run(s, FloodTickInput.calm(3.8), 500).floodVolume(), 1e-12);
    }

    @Test
    void floodingDisabledLetsNothingIn() {
        FloodSimulation s = new FloodSimulation(HullAnalyzer.analyze(box5().breach(0, 1, 2).build()),
                new FloodParams(false, 1, 0));
        assertEquals(0, run(s, FloodTickInput.calm(3.0).withWaves(1), 1000).floodVolume());
        // draining still works
        s.setVolume(0, 27);
        run(s, FloodTickInput.calm(0), 5000);
        assertEquals(0, s.volume(0), 1e-6);
    }

    @Test
    void tiltedUpVectorPutsTheRimUnderWater() {
        HullVec heeled = new HullVec(-Math.sin(Math.toRadians(40)), Math.cos(Math.toRadians(40)), 0);
        FloodSimulation level = sim(openHull5().build());
        FloodSimulation tilted = new FloodSimulation(HullAnalyzer.analyze(openHull5().build(), heeled), FloodParams.DEFAULTS);
        double sill = tilted.analysis().compartments().get(0).ports().get(0).sill();
        assertTrue(sill < 4.0);
        double sea = sill + 0.3;
        assertEquals(0, run(level, FloodTickInput.calm(sea), 200).floodVolume());
        assertTrue(run(tilted, FloodTickInput.calm(sea), 200).floodVolume() > 0);
    }

    @Test
    void flowMultiplierScalesInflow() {
        FloodSimulation s = new FloodSimulation(HullAnalyzer.analyze(box5().breach(0, 1, 2).build()),
                new FloodParams(true, 2, 0));
        assertEquals(0.1 * Math.sqrt(2), s.tick(FloodTickInput.calm(3.0)).inflow(), 1e-12);
    }
}
