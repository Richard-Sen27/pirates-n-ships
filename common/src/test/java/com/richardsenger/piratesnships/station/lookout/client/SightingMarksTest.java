package com.richardsenger.piratesnships.station.lookout.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.ship.hull.client.ShipHudMarks;
import com.richardsenger.piratesnships.station.lookout.SightingPayload;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** LKO2: the client's marks of the lookout's calls, their fade, the cue queue and where the hail sounds. */
class SightingMarksTest {

    private static final UUID SHIP = UUID.randomUUID();

    private static SightingPayload call(UUID ship, SightingPayload.What what, float compass, int key, int fresh) {
        return new SightingPayload(ship, what, "", 0f, compass, 100, 0, 80, 0, key, fresh);
    }

    @BeforeEach
    @AfterEach
    void reset() {
        SightingMarks.reset();
    }

    @Test
    void markIsFullThenFadesOverTheLastThird() {
        assertEquals(1f, SightingMarks.alpha(0, 600));
        assertEquals(1f, SightingMarks.alpha(400, 600));
        assertEquals(0.5f, SightingMarks.alpha(500, 600), 1e-4);
        assertTrue(SightingMarks.alpha(590, 600) > 0f && SightingMarks.alpha(590, 600) < 0.1f);
        assertEquals(0f, SightingMarks.alpha(600, 600));
        assertEquals(0f, SightingMarks.alpha(10, 0));
        float last = 1f;
        for (int t = 0; t <= 600; t += 10) {
            float a = SightingMarks.alpha(t, 600);
            assertTrue(a <= last, "the mark brightens at " + t);
            last = a;
        }
    }

    @Test
    void markStaysItsTimeOnItsShipAndIsReplacedByANewCall() {
        SightingMarks.accept(call(SHIP, SightingPayload.What.NAVY, 90f, 7, 100));
        List<ShipHudMarks.Mark> marks = SightingMarks.marks(SHIP);
        assertEquals(1, marks.size());
        assertEquals(90.0, marks.get(0).bearing(), 1e-6);
        assertEquals(SightingPayload.What.NAVY.tint(), marks.get(0).tint());
        assertTrue(SightingMarks.marks(UUID.randomUUID()).isEmpty(), "another ship shows the mark");
        for (int i = 0; i < 80; i++) SightingMarks.tick();
        assertTrue(SightingMarks.marks(SHIP).get(0).alpha() < 1f, "not fading at 80 of 100 ticks");
        SightingMarks.accept(call(SHIP, SightingPayload.What.NAVY, 100f, 7, 100));
        marks = SightingMarks.marks(SHIP);
        assertEquals(1, marks.size(), "the same sighting twice");
        assertEquals(100.0, marks.get(0).bearing(), 1e-6);
        assertEquals(1f, marks.get(0).alpha());
        for (int i = 0; i < 100; i++) SightingMarks.tick();
        assertTrue(SightingMarks.marks(SHIP).isEmpty(), "the mark outlived its time");
    }

    @Test
    void callsOfOneScanAreCuedOneAfterTheOther() {
        for (int i = 0; i < 6; i++) SightingMarks.accept(call(SHIP, SightingPayload.What.SHARK, i * 10f, i, 600));
        assertEquals(6, SightingMarks.marks(SHIP).size(), "every call is marked");
        SightingPayload first = SightingMarks.nextCue();
        assertNotNull(first);
        assertEquals(2, first.key(), "only the last " + SightingMarks.MAX_PENDING + " wait");
        assertNull(SightingMarks.nextCue(), "two cues at once");
        for (int i = 0; i < SightingMarks.CUE_GAP_TICKS - 1; i++) SightingMarks.tick();
        assertNull(SightingMarks.nextCue(), "a cue before the gap");
        SightingMarks.tick();
        assertEquals(3, SightingMarks.nextCue().key());
    }

    @Test
    void hailComesFromTheNestOrFromItsDirection() {
        Vec3 ear = new Vec3(0, 64, 0);
        Vec3 near = new Vec3(3, 70, 4);
        assertEquals(near, SightingMarks.hailAt(ear, near));
        Vec3 far = new Vec3(0, 64, 100);
        Vec3 at = SightingMarks.hailAt(ear, far);
        assertEquals(SightingMarks.HAIL_REACH, at.distanceTo(ear), 1e-9);
        assertEquals(0.0, at.x, 1e-9);
        assertTrue(at.z > 0);
    }
}
