package com.richardsenger.piratesnships.station.lookout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The lookout's land rays (CN1). */
class LandSamplerTest {

    /** Open sea everywhere except the given half-plane x >= coastX (land). */
    private static LandSampler.Surface coastEast(int coastX) {
        return (x, z) -> x >= coastX ? LandSampler.Column.LAND : LandSampler.Column.WATER;
    }

    @Test
    void openSeaHasNoLand() {
        assertNull(LandSampler.nearest(0, 0, 160, 16, 16, 24, (x, z) -> LandSampler.Column.WATER));
    }

    @Test
    void coastToTheEastIsFoundOnTheEastRayAtItsDistance() {
        LandSampler.Land l = LandSampler.nearest(0.5, 0.5, 160, 16, 16, 0, coastEast(100));
        assertNotNull(l);
        assertEquals(112.0, l.distance(), 1e-9, "the first sample beyond x 100 (16 block steps)");
        // the east ray and the ray a sector north of it reach the coast at the same sample; the first ray wins
        assertEquals(90.0, Bearings.compass(l.x() - 0.5, l.z() - 0.5), 23.0);
    }

    @Test
    void landBeyondTheRangeIsNotSeen() {
        assertNull(LandSampler.nearest(0, 0, 90, 16, 16, 0, coastEast(100)));
    }

    @Test
    void landWithinTheMinimumDistanceIsIgnored() {
        // an island of radius 10 round the ship (the quay) and nothing else
        LandSampler.Surface quay = (x, z) -> x * x + z * z <= 100 ? LandSampler.Column.LAND : LandSampler.Column.WATER;
        assertNull(LandSampler.nearest(0, 0, 160, 16, 8, 24, quay));
        assertNotNull(LandSampler.nearest(0, 0, 160, 16, 8, 0, quay));
    }

    @Test
    void unknownColumnsEndTheirRayAndAreNeverQueriedBeyond() {
        Set<Long> asked = new HashSet<>();
        LandSampler.Surface fog = (x, z) -> {
            asked.add(((long) x << 32) ^ (z & 0xffffffffL));
            return Math.abs(x) > 40 || Math.abs(z) > 40 ? LandSampler.Column.UNKNOWN : LandSampler.Column.WATER;
        };
        assertNull(LandSampler.nearest(0, 0, 160, 8, 16, 0, fog));
        // per ray at most the samples up to the first unknown one: 3 water (16, 32, ... within 40) + 1 unknown
        assertEquals(true, asked.size() <= 8 * 4, "asked " + asked.size());
    }

    @Test
    void theNearestLandOfAllRaysWins() {
        LandSampler.Surface twoCoasts = (x, z) -> x >= 150 || z <= -60 ? LandSampler.Column.LAND : LandSampler.Column.WATER;
        LandSampler.Land l = LandSampler.nearest(0, 0, 160, 16, 16, 0, twoCoasts);
        assertNotNull(l);
        assertEquals(64.0, l.distance(), 1e-9, "the north coast at z -60 (first sample at 64)");
        assertEquals(0.0, Bearings.compass(l.x(), l.z()), 1.0);
    }

    @Test
    void regionKeysGroupACoastIntoSquares() {
        assertEquals("land:0,0", LandSampler.regionKey(5, 127, 128));
        assertEquals("land:-1,0", LandSampler.regionKey(-1, 0, 128));
        assertEquals("land:1,-2", LandSampler.regionKey(128, -129, 128));
    }
}
