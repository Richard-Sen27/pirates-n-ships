package com.richardsenger.piratesnships.station.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.combat.cannon.CannonStation;
import com.richardsenger.piratesnships.combat.cannon.CannonStation.CannonOrder;
import com.richardsenger.piratesnships.combat.cannon.SwivelStation;
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
    void allHoldsTheSailOrdersThenPumpThenTheGunOrders() {
        assertEquals(List.of(SailOrder.HOIST, SailOrder.REEF, SailOrder.FURL, PumpOrder.PUMP, CannonOrder.FIRE, CannonOrder.LOAD,
                        CannonOrder.FIRE_AT_WILL),
                CrewOrder.all());
        assertEquals(List.of("hoist", "reef", "furl", "pump", "fire", "load", "fire_at_will"), CrewOrder.ids());
    }

    @Test
    void fireOrderHasTheCannonKeys() {
        assertEquals(CannonOrder.FIRE, CrewOrder.byId("fire").orElseThrow());
        assertEquals("cannon_order.pirates_n_ships.fire", CannonOrder.FIRE.nameKey());
        assertEquals("message.pirates_n_ships.crew.ack.fire", CannonOrder.FIRE.ackKey());
        assertEquals("message.pirates_n_ships.crew.cannon_not_loaded", CannonOrder.FIRE.nothingToDoKey());
        assertEquals("message.pirates_n_ships.crew.cannon_unable", CannonOrder.FIRE.unableKey());
    }

    @Test
    void loadOrderHasItsOwnKeys() {
        assertEquals(CannonOrder.LOAD, CrewOrder.byId("load").orElseThrow());
        assertEquals("cannon_order.pirates_n_ships.load", CannonOrder.LOAD.nameKey());
        assertEquals("message.pirates_n_ships.crew.ack.load", CannonOrder.LOAD.ackKey());
        assertEquals("message.pirates_n_ships.crew.cannon_already_loaded", CannonOrder.LOAD.nothingToDoKey());
        assertEquals("message.pirates_n_ships.crew.cannon_no_supply", CannonOrder.LOAD.unableKey());
        assertTrue(CannonStation.INSTANCE.accepts(CannonOrder.LOAD));
    }

    /** WS4a: "Fire at will" has its own name and acknowledgement, shares the fire answers, and is a cannon order only. */
    @Test
    void fireAtWillIsACannonOrderOnly() {
        assertEquals(CannonOrder.FIRE_AT_WILL, CrewOrder.byId("fire_at_will").orElseThrow());
        assertEquals("cannon_order.pirates_n_ships.fire_at_will", CannonOrder.FIRE_AT_WILL.nameKey());
        assertEquals("message.pirates_n_ships.crew.ack.fire_at_will", CannonOrder.FIRE_AT_WILL.ackKey());
        assertEquals(CannonOrder.FIRE.unableKey(), CannonOrder.FIRE_AT_WILL.unableKey());
        assertEquals(CannonOrder.FIRE.nothingToDoKey(), CannonOrder.FIRE_AT_WILL.nothingToDoKey());
        assertTrue(CannonStation.INSTANCE.accepts(CannonOrder.FIRE_AT_WILL));
        assertFalse(SwivelStation.INSTANCE.accepts(CannonOrder.FIRE_AT_WILL));
        assertTrue(SwivelStation.INSTANCE.accepts(CannonOrder.FIRE));
        assertTrue(SwivelStation.INSTANCE.accepts(CannonOrder.LOAD));
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
        // and the cannon from both
        Set<String> nothing = new HashSet<>();
        Set<String> unable = new HashSet<>();
        for (CrewOrder o : List.of(SailOrder.HOIST, PumpOrder.PUMP, CannonOrder.FIRE, CannonOrder.LOAD)) {
            assertTrue(nothing.add(o.nothingToDoKey()), "shared nothing-to-do key " + o.nothingToDoKey());
            assertTrue(unable.add(o.unableKey()), "shared unable key " + o.unableKey());
        }
    }

    @Test
    void eachOrderBelongsToExactlyOneStationKind() {
        List<StationKind<?>> kinds = List.of(WinchStation.INSTANCE, PumpStation.INSTANCE, CannonStation.INSTANCE);
        for (CrewOrder o : CrewOrder.all()) {
            long n = kinds.stream().filter(k -> k.accepts(o)).count();
            assertEquals(1, n, o + " is taken by " + n + " station kinds");
        }
        assertTrue(WinchStation.INSTANCE.accepts(SailOrder.FURL));
        assertFalse(WinchStation.INSTANCE.accepts(PumpOrder.PUMP));
        assertTrue(PumpStation.INSTANCE.accepts(PumpOrder.PUMP));
        assertFalse(PumpStation.INSTANCE.accepts(SailOrder.HOIST));
        assertFalse(PumpStation.INSTANCE.accepts("pump"));
        assertTrue(CannonStation.INSTANCE.accepts(CannonOrder.FIRE));
        assertFalse(CannonStation.INSTANCE.accepts(PumpOrder.PUMP));
        assertFalse(WinchStation.INSTANCE.accepts(CannonOrder.FIRE));
        assertFalse(PumpStation.INSTANCE.accepts(CannonOrder.FIRE));
    }
}
