package com.richardsenger.piratesnships.combat.grapple;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** GR8: which rope pose the synced rope state picks ({@link RopePose#select}). */
class RopePoseTest {

    private static RopePose.Facts hanging(int ticksSincePull, double speed) {
        return new RopePose.Facts(false, false, true, true, ticksSincePull, speed);
    }

    @Test
    void hangingStillOnTheRopeIsTheHang() {
        assertEquals(RopePose.HANG, RopePose.select(hanging(-1, 0.0), RopePose.NONE));
        assertEquals(RopePose.HANG, RopePose.select(hanging(RopePose.PULL_TICKS, 0.02), RopePose.HANG), "a pull long ago");
        assertEquals("rope_hang", RopePose.HANG.animation());
    }

    @Test
    void eachPullWhileHangingIsAClimb() {
        assertEquals(RopePose.CLIMB, RopePose.select(hanging(0, 0.0), RopePose.HANG));
        assertEquals(RopePose.CLIMB, RopePose.select(hanging(RopePose.PULL_TICKS - 1, 0.4), RopePose.SWING), "a pull wins over the swing");
        assertEquals("rope_climb", RopePose.CLIMB.animation());
    }

    @Test
    void swingingFastIsTheSwingWithHysteresis() {
        assertEquals(RopePose.SWING, RopePose.select(hanging(-1, RopePose.SWING_SPEED), RopePose.HANG));
        assertEquals(RopePose.HANG, RopePose.select(hanging(-1, RopePose.SWING_SPEED * 0.9), RopePose.HANG), "not fast enough to start");
        assertEquals(RopePose.SWING, RopePose.select(hanging(-1, RopePose.SWING_SPEED * 0.6), RopePose.SWING), "slowing at a turning point");
        assertEquals(RopePose.HANG, RopePose.select(hanging(-1, RopePose.SWING_SPEED_OFF * 0.5), RopePose.SWING), "the swing has died out");
        assertEquals("rope_swing", RopePose.SWING.animation());
    }

    @Test
    void aPullWhileStandingIsThePull() {
        RopePose.Facts standing = new RopePose.Facts(false, false, false, true, 2, 0.0);
        assertEquals(RopePose.PULL, RopePose.select(standing, RopePose.NONE));
        assertEquals(RopePose.NONE, RopePose.select(new RopePose.Facts(false, false, false, true, RopePose.PULL_TICKS, 0.0), RopePose.PULL));
        assertEquals(RopePose.NONE, RopePose.select(new RopePose.Facts(false, false, false, false, 2, 0.0), RopePose.NONE),
                "no rope in hand (tied off): no pull");
        assertEquals("rope_pull", RopePose.PULL.animation());
    }

    @Test
    void slidingAndHaulingComeFirst() {
        assertEquals(RopePose.SLIDE, RopePose.select(new RopePose.Facts(true, true, true, true, 0, 1.0), RopePose.NONE));
        assertEquals(RopePose.HAUL, RopePose.select(new RopePose.Facts(false, true, true, true, 0, 1.0), RopePose.NONE));
        assertEquals(RopePose.NONE, RopePose.select(new RopePose.Facts(false, false, false, false, -1, 0.0), RopePose.SWING));
    }

    @Test
    void onlyTheHangPosesLeanAlongTheRope() {
        assertTrue(RopePose.HANG.hangs() && RopePose.CLIMB.hangs() && RopePose.SWING.hangs());
        assertFalse(RopePose.PULL.hangs() || RopePose.SLIDE.hangs() || RopePose.HAUL.hangs() || RopePose.NONE.hangs());
    }

    @Test
    void hangingNeedsTheOwnHeldRopeOffTheGround() {
        assertTrue(RopePose.hanging(true, false, 4.0, false, false));
        assertFalse(RopePose.hanging(false, false, 4.0, false, false), "not latched");
        assertFalse(RopePose.hanging(true, true, 4.0, false, false), "tied off");
        assertFalse(RopePose.hanging(true, false, Double.NaN, false, false), "the rope pays out");
        assertFalse(RopePose.hanging(true, false, 4.0, true, false), "on the ground");
        assertFalse(RopePose.hanging(true, false, 4.0, false, true), "riding");
    }

    @Test
    void theSwingSpeedIsTheMotionAcrossTheRope() {
        Vec3 below = new Vec3(0, -4, 0);
        assertEquals(0.3, RopePose.swingSpeed(below, new Vec3(0.3, 0, 0)), 1e-9);
        assertEquals(0.0, RopePose.swingSpeed(below, new Vec3(0, -0.4, 0)), 1e-9, "drawn up the rope is no swing");
        assertEquals(0.5, RopePose.swingSpeed(below, new Vec3(0.3, 0.2, 0.4)), 1e-9);
    }
}
