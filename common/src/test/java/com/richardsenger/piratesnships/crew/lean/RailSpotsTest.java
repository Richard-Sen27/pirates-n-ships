package com.richardsenger.piratesnships.crew.lean;

import com.richardsenger.piratesnships.crew.lean.RailSpots.Candidate;
import com.richardsenger.piratesnships.crew.lean.RailSpots.Spot;
import com.richardsenger.piratesnships.crew.npc.CrewPose;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RAIL5: the pure spot choice ({@link RailSpots}), the lean's timing and cap ({@link LeanRules}) and its pose priority. */
class RailSpotsTest {

    private static Candidate ok(int x, int z, Direction out) {
        return new Candidate(new BlockPos(x, 9, z), out, true, true, true, false);
    }

    /** A west rail at x 0 from z 0 to 10. */
    private static List<Candidate> westRail() {
        List<Candidate> out = new ArrayList<>();
        for (int z = 0; z <= 10; z++) out.add(ok(0, z, Direction.WEST));
        return out;
    }

    @Test
    void theNearestUsableSpotWins() {
        Spot s = RailSpots.choose(westRail(), new Vec3(3.5, 9, 6.5), 6, List.of());
        assertEquals(new Spot(new BlockPos(0, 9, 6), Direction.WEST), s);
    }

    @Test
    void aSpotNeedsFloorHeadroomAWayInAndNoStationNearby() {
        BlockPos c = new BlockPos(0, 9, 5);
        assertFalse(new Candidate(c, Direction.WEST, false, true, true, false).usable(), "no floor");
        assertFalse(new Candidate(c, Direction.WEST, true, false, true, false).usable(), "no headroom");
        assertFalse(new Candidate(c, Direction.WEST, true, true, false, false).usable(), "no way in");
        assertFalse(new Candidate(c, Direction.WEST, true, true, true, true).usable(), "a station beside it");
        assertTrue(ok(0, 5, Direction.WEST).usable());
        List<Candidate> only = List.of(new Candidate(c, Direction.WEST, true, true, true, true));
        assertNull(RailSpots.choose(only, new Vec3(2.5, 9, 5.5), 6, List.of()), "the helm's cell is no spot");
    }

    @Test
    void leanersKeepACellBetweenThem() {
        assertFalse(RailSpots.apart(new BlockPos(0, 9, 5), new BlockPos(0, 9, 5)), "one cell");
        assertFalse(RailSpots.apart(new BlockPos(0, 9, 5), new BlockPos(0, 9, 6)), "neighbours");
        assertFalse(RailSpots.apart(new BlockPos(0, 9, 5), new BlockPos(1, 9, 6)), "diagonal neighbours");
        assertTrue(RailSpots.apart(new BlockPos(0, 9, 5), new BlockPos(0, 9, 7)), "a cell between");
        Spot s = RailSpots.choose(westRail(), new Vec3(3.5, 9, 6.5), 6, List.of(new BlockPos(0, 9, 6)));
        assertTrue(s != null && (s.cell().getZ() == 4 || s.cell().getZ() == 8), "next free spot two cells off: " + s);
        Spot t = RailSpots.choose(westRail(), new Vec3(3.5, 9, 6.5), 6, List.of(new BlockPos(0, 9, 6), new BlockPos(0, 9, 4),
                new BlockPos(0, 9, 8)));
        assertTrue(t != null && (t.cell().getZ() == 2 || t.cell().getZ() == 10), "then further: " + t);
    }

    @Test
    void onlySpotsWithinTheRadius() {
        assertNull(RailSpots.choose(westRail(), new Vec3(9.5, 9, 6.5), 6, List.of()), "the rail is 9 blocks off");
        assertNull(RailSpots.choose(westRail(), new Vec3(3.5, 13, 6.5), 6, List.of()), "the rail is four decks down");
    }

    @Test
    void theLeanPointIsInTheRailingsCellAgainstTheRail() {
        Vec3 p = RailSpots.leanPoint(new Spot(new BlockPos(0, 9, 6), Direction.WEST));
        assertEquals(0.5 - LeanRules.LEAN_OFFSET, p.x, 1e-9);
        assertEquals(9.0, p.y, 1e-9);
        assertEquals(6.5, p.z, 1e-9);
        assertEquals(new BlockPos(1, 9, 6), RailSpots.approachCell(new Spot(new BlockPos(0, 9, 6), Direction.WEST)));
    }

    @Test
    void aLeanLastsBetweenItsBoundsInWholeSeconds() {
        assertEquals(30 * 20, LeanRules.durationTicks(30, 90, 0.0));
        assertEquals(90 * 20, LeanRules.durationTicks(30, 90, 0.9999999));
        assertEquals(60 * 20, LeanRules.durationTicks(30, 90, 30.5 / 61.0));
        assertEquals(45 * 20, LeanRules.durationTicks(45, 45, 0.7), "fixed length");
        assertEquals(30 * 20, LeanRules.durationTicks(90, 30, 0.0), "bounds in either order");
        for (int i = 0; i < 100; i++) {
            int d = LeanRules.durationTicks(30, 90, i / 100.0);
            assertTrue(d >= 600 && d <= 1800 && d % 20 == 0, "duration " + d);
        }
    }

    @Test
    void theCapCountsTheLeanersOfOneShip() {
        assertTrue(LeanRules.mayLean(0, 3));
        assertTrue(LeanRules.mayLean(2, 3));
        assertFalse(LeanRules.mayLean(3, 3));
        assertFalse(LeanRules.mayLean(0, 0), "a cap of 0: nobody leans");
    }

    @Test
    void leaningIsAPoseBelowStationsAndAboveWalking() {
        assertEquals(CrewPose.LEAN, CrewPose.choose(false, false, false, false, null, true));
        assertEquals(CrewPose.LEAN, CrewPose.choose(true, false, false, false, null, true), "a deck under way swings the legs");
        assertEquals(CrewPose.HELM, CrewPose.choose(false, false, false, false, CrewPose.HELM, true));
        assertEquals(CrewPose.WORK, CrewPose.choose(false, true, false, false, null, true));
        assertEquals(CrewPose.IDLE, CrewPose.choose(false, false, false, false, null, false));
        assertEquals("lean", CrewPose.LEAN.animation());
        assertTrue(CrewPose.LEAN.loops());
        assertFalse(CrewPose.LEAN.isStationPose(), "the lean is no synced station pose");
    }
}
