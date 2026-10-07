package com.richardsenger.piratesnships.ship.hull.pump;

import com.richardsenger.piratesnships.ship.hull.CellKind;
import com.richardsenger.piratesnships.ship.hull.HullAnalysis;
import com.richardsenger.piratesnships.ship.hull.HullAnalyzer;
import com.richardsenger.piratesnships.ship.hull.HullGrid;
import org.junit.jupiter.api.Test;

import static com.richardsenger.piratesnships.ship.hull.TestHulls.closedBox;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PumpIntakeTest {

    /** Two-deck hull: hold y 1..2 (compartment of the lower box), deck y 3, a closed cabin y 4..5 above, roof y 6. */
    private static HullAnalysis twoDecks() {
        HullGrid.Builder b = HullGrid.builder(5, 7, 5);
        closedBox(b, 0, 0, 0, 4, 6, 4);
        b.fill(1, 3, 1, 3, 3, 3, CellKind.SOLID); // the deck between hold and cabin
        return HullAnalyzer.analyze(b.build());
    }

    @Test
    void pumpInARoomDrainsThatRoom() {
        HullAnalysis a = twoDecks();
        int hold = a.compartmentAt(2, 1, 2), cabin = a.compartmentAt(2, 4, 2);
        assertEquals(hold, PumpIntake.find(a, 2, 1, 2, 4, c -> true));
        assertEquals(cabin, PumpIntake.find(a, 2, 4, 2, 4, c -> true));
    }

    @Test
    void pumpOnTheDeckReachesTheFloodedHoldBelowThroughTheDeck() {
        HullAnalysis a = twoDecks();
        int hold = a.compartmentAt(2, 1, 2), cabin = a.compartmentAt(2, 4, 2);
        // a pump standing on the deck is in the cabin: with a dry cabin it draws from the flooded hold
        assertEquals(hold, PumpIntake.find(a, 2, 4, 2, 4, c -> c == hold));
        // the highest flooded compartment comes first
        assertEquals(cabin, PumpIntake.find(a, 2, 4, 2, 4, c -> true));
        // nothing flooded: the first compartment it reaches is reported (the "dry bilge" message)
        assertEquals(cabin, PumpIntake.find(a, 2, 4, 2, 4, c -> false));
    }

    @Test
    void reachLimitsTheIntake() {
        HullAnalysis a = twoDecks();
        int hold = a.compartmentAt(2, 1, 2), cabin = a.compartmentAt(2, 4, 2);
        // a pump at y 5 in the cabin: y 4 cabin, y 3 deck, y 2 the hold's top layer, 3 cells below
        assertEquals(cabin, PumpIntake.find(a, 2, 5, 2, 2, c -> c == hold), "reach 2 ends in the deck");
        assertEquals(hold, PumpIntake.find(a, 2, 5, 2, 3, c -> c == hold));
        // a pump in the roof (solid, y 6) with no reach finds nothing
        assertEquals(-1, PumpIntake.find(a, 2, 6, 2, 0, c -> true));
        assertEquals(cabin, PumpIntake.find(a, 2, 6, 2, 1, c -> true));
    }

    @Test
    void noCompartmentInReachIsMinusOne() {
        assertEquals(-1, PumpIntake.choose(d -> -1, 4, c -> true));
        assertEquals(-1, PumpIntake.choose(d -> -1, -3, c -> true));
    }

    @Test
    void crewTicksEstimateTheTimeToEmpty() {
        assertEquals(0, PumpIntake.crewTicks(0, 0.05, 100));
        assertEquals(0, PumpIntake.crewTicks(PumpIntake.DRY / 2, 0.05, 100));
        assertEquals(-1, PumpIntake.crewTicks(5, 0, 100));
        assertEquals(20, PumpIntake.crewTicks(1, 0.05, 100));
        assertEquals(1, PumpIntake.crewTicks(0.001, 0.05, 100));
        assertEquals(100, PumpIntake.crewTicks(50, 0.05, 100));
    }
}
