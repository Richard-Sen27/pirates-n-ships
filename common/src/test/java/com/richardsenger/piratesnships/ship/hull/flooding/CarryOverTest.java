package com.richardsenger.piratesnships.ship.hull.flooding;

import com.richardsenger.piratesnships.ship.hull.CellKind;
import com.richardsenger.piratesnships.ship.hull.HullAnalyzer;
import com.richardsenger.piratesnships.ship.hull.HullGrid;
import org.junit.jupiter.api.Test;

import static com.richardsenger.piratesnships.ship.hull.TestHulls.*;
import static org.junit.jupiter.api.Assertions.*;

class CarryOverTest {

    private static HullGrid.Builder twoRooms() {
        HullGrid.Builder b = closedBox(HullGrid.builder(9, 5, 5), 0, 0, 0, 8, 4, 4);
        return b.fill(4, 1, 1, 4, 3, 3, CellKind.SOLID);
    }

    @Test
    void identicalLayoutKeepsVolumesExactly() {
        FloodSimulation s = new FloodSimulation(HullAnalyzer.analyze(twoRooms().build()), FloodParams.DEFAULTS);
        s.setVolume(0, 7.123456789);
        s.setVolume(1, 3.3);
        assertEquals(0, s.rebind(HullAnalyzer.analyze(twoRooms().build())));
        assertEquals(7.123456789, s.volume(0));
        assertEquals(3.3, s.volume(1));
    }

    @Test
    void mergeAddsTheWaterOfBothRooms() {
        FloodSimulation s = new FloodSimulation(HullAnalyzer.analyze(twoRooms().build()), FloodParams.DEFAULTS);
        s.setVolume(0, 10);
        s.setVolume(1, 5);
        double lost = s.rebind(HullAnalyzer.analyze(twoRooms().fill(4, 1, 1, 4, 3, 3, CellKind.AIR).build()));
        assertEquals(0, lost, 1e-12);
        assertEquals(1, s.analysis().compartments().size());
        assertEquals(63, s.analysis().compartments().get(0).volume());
        assertEquals(15, s.volume(0), 1e-9);
    }

    @Test
    void splitDividesByWhereTheWaterWasAndLosesWhatIsNowWall() {
        HullGrid.Builder one = closedBox(HullGrid.builder(9, 5, 5), 0, 0, 0, 8, 4, 4);
        FloodSimulation s = new FloodSimulation(HullAnalyzer.analyze(one.build()), FloodParams.DEFAULTS);
        s.setVolume(0, 21); // 7×3 bottom layer exactly: level 2.0
        double lost = s.rebind(HullAnalyzer.analyze(twoRooms().build()));
        assertEquals(3, lost, 1e-9, "the three bottom cells under the new wall");
        assertEquals(9, s.volume(0), 1e-9);
        assertEquals(9, s.volume(1), 1e-9);
    }

    @Test
    void waterInCellsThatBecameOutsideAirIsLost() {
        FloodSimulation s = new FloodSimulation(HullAnalyzer.analyze(box5().build()), FloodParams.DEFAULTS);
        s.setVolume(0, 27);
        // hole in the wall at y = 2: layers y 2 and 3 are outside air now
        double lost = s.rebind(HullAnalyzer.analyze(box5().set(0, 2, 2, CellKind.AIR).build()));
        assertEquals(18, lost, 1e-9);
        assertEquals(9, s.volume(0), 1e-9);
    }

    @Test
    void addedCellsKeepWaterAndGridsMayMove() {
        // Same box, but the snapshot grew by one block on every side (origin moved to -1).
        FloodSimulation s = new FloodSimulation(HullAnalyzer.analyze(box5().build()), FloodParams.DEFAULTS);
        s.setVolume(0, 12.5);
        HullGrid.Builder bigger = HullGrid.builder(7, 7, 7).origin(-1, -1, -1);
        closedBox(bigger, 1, 1, 1, 5, 5, 5);
        double lost = s.rebind(HullAnalyzer.analyze(bigger.build()));
        assertEquals(0, lost);
        assertEquals(12.5, s.volume(0), 1e-12);
        // Enlarging the room (cells added) keeps the water too.
        HullGrid.Builder taller = HullGrid.builder(7, 8, 7).origin(-1, -1, -1);
        closedBox(taller, 1, 1, 1, 5, 6, 5);
        assertEquals(0, s.rebind(HullAnalyzer.analyze(taller.build())), 1e-9);
        assertEquals(12.5, s.volume(0), 1e-9);
        assertEquals(36, s.analysis().compartments().get(0).volume());
    }
}
