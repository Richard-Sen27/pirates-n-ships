package com.richardsenger.piratesnships.mob.squad;

import com.richardsenger.piratesnships.mob.squad.FileFollowing.Pace;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Walking in file (MOB2): 1.5 blocks behind the man in front, no stop-and-go, running beyond 6. */
class FileFollowingTest {

    @Test
    void aMemberStopsAtTheSpacingAndRunsWhenFarBehind() {
        assertEquals(Pace.STOP, FileFollowing.pace(1.0, true));
        assertEquals(Pace.STOP, FileFollowing.pace(1.5, true), "1.5 behind: in his place");
        assertEquals(Pace.WALK, FileFollowing.pace(1.6, true), "a walking member closes up to 1.5");
        assertEquals(Pace.WALK, FileFollowing.pace(6.0, false));
        assertEquals(Pace.RUN, FileFollowing.pace(6.01, false), "more than 6 behind: run");
        assertEquals(Pace.RUN, FileFollowing.pace(30, true));
    }

    @Test
    void aStandingMemberWaitsForSomeSlackBeforeSettingOff() {
        assertEquals(Pace.STOP, FileFollowing.pace(2.0, false), "no stop-and-go at every step of the man in front");
        assertEquals(Pace.WALK, FileFollowing.pace(2.0, true));
        assertEquals(Pace.WALK, FileFollowing.pace(FileFollowing.RESUME + 0.01, false));
    }

    @Test
    void theFileIsClosedUpWhenNoGapExceedsThreeBlocks() {
        List<Vec3> file = List.of(new Vec3(0, 64, 0), new Vec3(0, 64, 1.5), new Vec3(0, 64, 3.0), new Vec3(0, 64, 4.5));
        assertEquals(1.5, FileFollowing.maxGap(file), 1e-9);
        assertTrue(FileFollowing.closedUp(file));
        List<Vec3> straggler = List.of(new Vec3(0, 64, 0), new Vec3(0, 64, 1.5), new Vec3(0, 64, 4.6));
        assertEquals(3.1, FileFollowing.maxGap(straggler), 1e-9);
        assertFalse(FileFollowing.closedUp(straggler), "the gap counts between neighbours, not from the officer");
        assertTrue(FileFollowing.closedUp(List.of(new Vec3(5, 64, 5))), "an officer alone is closed up");
        assertEquals(0.0, FileFollowing.maxGap(List.of()));
    }

    @Test
    void theSpacingFitsTheGap() {
        assertTrue(FileFollowing.SPACING < FileFollowing.RESUME && FileFollowing.RESUME < FileFollowing.MAX_GAP
                && FileFollowing.MAX_GAP < FileFollowing.RUN_BEYOND, "stop < resume < closed-up gap < run");
        assertTrue(FileFollowing.RUN_SPEED > FileFollowing.WALK_SPEED);
    }
}
