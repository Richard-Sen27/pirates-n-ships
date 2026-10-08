package com.richardsenger.piratesnships.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** {@link StationKind#begin} is called once per started order, after the start, and never for refused orders. */
class StationBeginTest {

    private static final StationRef REF = new StationRef(UUID.randomUUID(), BlockPos.ZERO);

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** A spy kind whose work time is the order's length; records each begin with the state's phase at that moment. */
    private static final class SpyKind implements StationKind<String> {
        final List<String> begun = new ArrayList<>();
        final List<StationState.Phase> phaseAtBegin = new ArrayList<>();
        final StationState<Object> state = new StationState<>();

        SpyKind() {
            state.occupy(new StationState.Occupant(UUID.randomUUID(), false));
        }

        @Override
        public String id() {
            return "spy";
        }

        @Override
        public Class<String> orderType() {
            return String.class;
        }

        @Override
        public int durationTicks(ServerLevel level, StationRef station, String order) {
            return order.equals("cannot") ? -1 : order.length();
        }

        @Override
        public void begin(ServerLevel level, StationRef station, String order) {
            begun.add(order);
            phaseAtBegin.add(state.phase());
        }

        @Override
        public void complete(ServerLevel level, StationRef station, String order) {
        }
    }

    @Test
    void beginOncePerStartedOrder() {
        SpyKind kind = new SpyKind();
        assertSame(Stations.OrderResult.STARTED, Stations.start(kind, kind.state, null, REF, "hoist"));
        assertEquals(List.of("hoist"), kind.begun);
        assertEquals(List.of(StationState.Phase.OPERATING), kind.phaseAtBegin, "begin runs after the start");
        assertEquals("hoist", kind.state.order());
        assertEquals(5, kind.state.remaining());
        assertSame(Stations.OrderResult.STARTED, Stations.start(kind, kind.state, null, REF, "furl"));
        assertEquals(List.of("hoist", "furl"), kind.begun);
    }

    @Test
    void noBeginForRefusedOrders() {
        SpyKind kind = new SpyKind();
        assertSame(Stations.OrderResult.NOTHING_TO_DO, Stations.start(kind, kind.state, null, REF, ""));
        assertSame(Stations.OrderResult.NOT_APPLICABLE, Stations.start(kind, kind.state, null, REF, "cannot"));
        assertSame(Stations.OrderResult.WRONG_STATION, Stations.start(kind, kind.state, null, REF, 42));
        assertEquals(List.of(), kind.begun);
    }
}
