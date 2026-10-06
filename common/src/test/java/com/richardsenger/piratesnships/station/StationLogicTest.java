package com.richardsenger.piratesnships.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.station.winch.SailOrder;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StationLogicTest {

    private static final StationState.Occupant A = new StationState.Occupant(UUID.randomUUID(), false);
    private static final StationState.Occupant B = new StationState.Occupant(UUID.randomUUID(), true);

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void freeOccupiedOperatingDone() {
        StationState<String> s = new StationState<>();
        assertEquals(StationState.Phase.FREE, s.phase());
        assertFalse(s.start("x", 3), "a free station takes no order");
        assertEquals(StationState.OccupyResult.OCCUPIED, s.occupy(A));
        assertEquals(StationState.Phase.OCCUPIED, s.phase());
        assertTrue(s.start("hoist", 3));
        assertEquals(StationState.Phase.OPERATING, s.phase());
        assertEquals(3, s.remaining());
        assertNull(s.tick());
        assertNull(s.tick());
        assertEquals("hoist", s.tick(), "done on the third tick");
        assertEquals(StationState.Phase.OCCUPIED, s.phase());
        assertNull(s.tick(), "nothing more to do");
    }

    @Test
    void oneOccupantAtATime() {
        StationState<String> s = new StationState<>();
        s.occupy(A);
        assertEquals(StationState.OccupyResult.TAKEN, s.occupy(B));
        assertEquals(StationState.OccupyResult.ALREADY, s.occupy(A));
        assertFalse(s.release(B.id()), "only the occupant releases");
        assertTrue(s.isOccupiedBy(A.id()));
        assertTrue(s.release(A.id()));
        assertEquals(StationState.Phase.FREE, s.phase());
        assertEquals(StationState.OccupyResult.OCCUPIED, s.occupy(B));
    }

    @Test
    void releaseAndInterruptDropTheOrder() {
        StationState<String> s = new StationState<>();
        s.occupy(A);
        s.start("hoist", 5);
        s.tick();
        assertEquals("hoist", s.interrupt());
        assertEquals(StationState.Phase.OCCUPIED, s.phase());
        assertEquals(0, s.remaining());
        s.start("furl", 2);
        assertTrue(s.release(A.id()));
        assertNull(s.order());
        assertNull(s.tick(), "a released station finishes nothing");
        s.occupy(B);
        s.start("reef", 2);
        s.clear();
        assertEquals(StationState.Phase.FREE, s.phase());
    }

    @Test
    void newOrderReplacesTheOneInProgress() {
        StationState<String> s = new StationState<>();
        s.occupy(A);
        s.start("hoist", 4);
        s.tick();
        s.start("furl", 2);
        assertNull(s.tick());
        assertEquals("furl", s.tick());
        s.start("zero", 0);
        assertEquals("zero", s.tick(), "a zero duration still takes one tick");
    }

    @Test
    void orderToTrimAndTiming() {
        assertEquals(SailTrim.FULL, SailOrder.HOIST.target());
        assertEquals(SailTrim.HALF, SailOrder.REEF.target());
        assertEquals(SailTrim.FURLED, SailOrder.FURL.target());
        assertEquals(2, SailOrder.HOIST.steps(SailTrim.FURLED));
        assertEquals(80, SailOrder.HOIST.durationTicks(SailTrim.FURLED, 40));
        assertEquals(40, SailOrder.HOIST.durationTicks(SailTrim.HALF, 40));
        assertEquals(0, SailOrder.HOIST.durationTicks(SailTrim.FULL, 40));
        assertEquals(40, SailOrder.REEF.durationTicks(SailTrim.FULL, 40));
        assertEquals(40, SailOrder.REEF.durationTicks(SailTrim.FURLED, 40));
        assertEquals(60, SailOrder.FURL.durationTicks(SailTrim.FULL, 30));
        assertEquals(0, SailOrder.FURL.durationTicks(SailTrim.FULL, -5), "negative step time clamps to 0");
        assertEquals(SailOrder.REEF, SailOrder.HOIST.next());
        assertEquals(SailOrder.HOIST, SailOrder.FURL.next());
    }

    @Test
    void assignmentAndOrderCodecsRoundTrip() {
        StationRef ref = new StationRef(UUID.randomUUID(), new BlockPos(20_000_123, 70, -20_000_456));
        Tag t = StationRef.CODEC.encodeStart(NbtOps.INSTANCE, ref).getOrThrow();
        assertEquals(ref, StationRef.CODEC.parse(NbtOps.INSTANCE, t).getOrThrow());
        for (SailOrder o : SailOrder.values()) {
            Tag ot = SailOrder.CODEC.encodeStart(NbtOps.INSTANCE, o).getOrThrow();
            assertEquals(o, SailOrder.CODEC.parse(NbtOps.INSTANCE, ot).getOrThrow());
        }
    }

    @Test
    void spotBesideTheStationWithFloorElseOnTop() {
        BlockPos st = new BlockPos(0, 10, 0);
        // north is blocked, east is free with deck below
        Set<BlockPos> solid = Set.of(st, st.north(), st.below(), st.east().below(), st.south().below());
        BlockPos spot = StationSpot.choose(st, p -> !solid.contains(p), solid::contains);
        assertEquals(st.east(), spot);
        // nothing beside has a floor: on top
        assertEquals(st.above(), StationSpot.choose(st, p -> true, p -> false));
    }
}
