package com.richardsenger.piratesnships.station.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.pump.PumpOrder;
import com.richardsenger.piratesnships.station.pump.PumpStation;
import com.richardsenger.piratesnships.station.winch.SailOrder;
import com.richardsenger.piratesnships.station.winch.WinchStation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The order ↔ station kind mapping and the command ids of the crew orders (G7). */
class CrewOrderTest {

    @Test
    void allHoldsTheSailOrdersThenPump() {
        assertEquals(List.of(SailOrder.HOIST, SailOrder.REEF, SailOrder.FURL, PumpOrder.PUMP), CrewOrder.all());
        assertEquals(List.of("hoist", "reef", "furl", "pump"), CrewOrder.ids());
    }

    @Test
    void byIdFindsEveryOrderAndNothingElse() {
        for (CrewOrder o : CrewOrder.all()) {
            assertEquals(o, CrewOrder.byId(o.id()).orElseThrow());
        }
        assertTrue(CrewOrder.byId("bail").isEmpty());
        assertTrue(CrewOrder.byId("PUMP").isEmpty()); // the command lower-cases first
        assertTrue(CrewOrder.byId("").isEmpty());
    }

    @Test
    void idsAndKeysAreUnique() {
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        Set<String> acks = new HashSet<>();
        for (CrewOrder o : CrewOrder.all()) {
            assertTrue(ids.add(o.id()), "duplicate id " + o.id());
            assertTrue(names.add(o.nameKey()), "duplicate name key " + o.nameKey());
            assertTrue(acks.add(o.ackKey()), "duplicate ack key " + o.ackKey());
        }
        // the pump answers differently from the winch
        assertFalse(PumpOrder.PUMP.nothingToDoKey().equals(SailOrder.HOIST.nothingToDoKey()));
        assertFalse(PumpOrder.PUMP.unableKey().equals(SailOrder.HOIST.unableKey()));
    }

    @Test
    void eachOrderBelongsToExactlyOneStationKind() {
        List<StationKind<?>> kinds = List.of(WinchStation.INSTANCE, PumpStation.INSTANCE);
        for (CrewOrder o : CrewOrder.all()) {
            long n = kinds.stream().filter(k -> k.accepts(o)).count();
            assertEquals(1, n, o + " is taken by " + n + " station kinds");
        }
        assertTrue(WinchStation.INSTANCE.accepts(SailOrder.FURL));
        assertFalse(WinchStation.INSTANCE.accepts(PumpOrder.PUMP));
        assertTrue(PumpStation.INSTANCE.accepts(PumpOrder.PUMP));
        assertFalse(PumpStation.INSTANCE.accepts(SailOrder.HOIST));
        assertFalse(PumpStation.INSTANCE.accepts("pump"));
    }
}
