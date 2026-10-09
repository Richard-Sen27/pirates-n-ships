package com.richardsenger.piratesnships.crew.npc;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ST5: the turn to face a station ({@link StationFacing}) and the default facing ({@link StationPoses#toward(Vec3, BlockPos, List)}). */
class StationFacingTest {

    private static final float EPS = 1e-4f;

    private static float off(float a, float b) {
        return Math.abs(Mth.wrapDegrees(a - b));
    }

    @Test
    void turnsAlongTheShorterArc() {
        // from 170 to -170 is 20 degrees through 180, not 340 back through 0
        StationFacing.Turn t = StationFacing.step(170f, 170f, -170f, 15f, 45f, true);
        assertEquals(185f, t.body(), EPS);
        t = StationFacing.step(-170f, -170f, 170f, 15f, 45f, true);
        assertEquals(-185f, t.body(), EPS);
    }

    @Test
    void theTurnIsCappedPerTickAndThenLocked() {
        float body = 0f;
        float head = 0f;
        float target = 180f - 1f; // nearly behind it: the longest turn
        int ticks = 0;
        while (off(body, target) > EPS) {
            StationFacing.Turn t = StationFacing.step(body, head, target, 15f, 45f, false);
            assertTrue(Math.abs(t.body() - body) <= 15f + EPS, "turned " + (t.body() - body) + " in one tick");
            body = t.body();
            head = t.head();
            ticks++;
            assertTrue(ticks < 100, "never arrives");
        }
        assertEquals(12, ticks, "179 degrees at 15 per tick");
        // locked: further ticks do not move it, and it reads as facing the station
        StationFacing.Turn t = StationFacing.step(body, head, target, 15f, 45f, false);
        assertEquals(body, t.body(), EPS);
        assertTrue(StationFacing.locked(t.body(), target));
    }

    @Test
    void theLockFollowsATurningShip() {
        float body = 90f;
        for (int i = 1; i <= 50; i++) {
            float target = 90f + 2f * i; // the ship turns 2 degrees a tick, slower than the crew turns
            body = StationFacing.step(body, body, target, 15f, 45f, true).body();
            assertEquals(0f, off(body, target), EPS, "fell behind the ship at tick " + i);
        }
    }

    @Test
    void theMaximumTurnsAtOnce() {
        StationFacing.Turn t = StationFacing.step(10f, 10f, -170f, 180f, 45f, true);
        assertEquals(0f, off(t.body(), -170f), EPS);
        assertEquals(t.body(), t.head(), EPS);
    }

    @Test
    void theHeadStaysWithin45DegreesOfTheBody() {
        // free head: a look 80 degrees to the right is cut back to 45, one 30 to the left stays
        assertEquals(45f, StationFacing.step(0f, 80f, 0f, 15f, 45f, false).head(), EPS);
        assertEquals(-30f, StationFacing.step(0f, -30f, 0f, 15f, 45f, false).head(), EPS);
        // across the wrap: body 170, head -150 (that is 210, 40 to the right) stays
        StationFacing.Turn t = StationFacing.step(170f, -150f, 170f, 15f, 45f, false);
        assertEquals(-150f, t.head(), EPS);
        // a held head follows the body
        t = StationFacing.step(0f, 80f, 90f, 15f, 45f, true);
        assertEquals(15f, t.body(), EPS);
        assertEquals(15f, t.head(), EPS);
        // during a turn the free head is dragged along within 45 of the body
        t = StationFacing.step(0f, -40f, 90f, 15f, 45f, false);
        assertEquals(-30f, t.head(), EPS);
    }

    @Test
    void resultsStayContinuous() {
        assertEquals(370f, StationFacing.continuous(360f, 10f), EPS);
        assertEquals(-350f, StationFacing.approach(-355f, 10f, 5f), EPS);
        assertEquals(725f, StationFacing.clampHead(725f, 720f, 45f), EPS);
    }

    @Test
    void theDefaultFacesTheBlockFromTheSeat() {
        BlockPos winch = new BlockPos(10, 64, 10);
        Vec3 seatSouth = new Vec3(10.5, 64, 11.5);
        Vec3 h = StationPoses.toward(seatSouth, winch, List.of(winch));
        assertNotNull(h);
        assertEquals(0.0, h.x, 1e-9);
        assertEquals(-1.0, h.z, 1e-9);
        // the plot yaw of a northward heading equals the yaw of facing north
        assertEquals(0f, off(StationPoses.yaw(Direction.NORTH, null), StationPoses.yaw(h, null)), 1e-3f);
    }

    @Test
    void theDefaultFacesTheBlockOfAFootprintBesideTheSeat() {
        // a two-block cannon: master at x 10, rear at x 11; the seat beside the rear faces the rear, not the master
        BlockPos master = new BlockPos(10, 64, 10);
        BlockPos rear = new BlockPos(11, 64, 10);
        Vec3 seat = new Vec3(11.5, 64, 11.5);
        Vec3 h = StationPoses.toward(seat, master, List.of(master, rear));
        assertNotNull(h);
        assertEquals(0f, off(StationPoses.yaw(Direction.NORTH, null), StationPoses.yaw(h, null)), 1e-3f);
    }

    @Test
    void aSeatAwayFromTheBlockFacesItsMiddle() {
        // a capstan seat walked round the drum to a diagonal: it faces the drum's axis
        BlockPos capstan = new BlockPos(0, 64, 0);
        Vec3 seat = new Vec3(1.6, 64, 1.6);
        Vec3 h = StationPoses.toward(seat, capstan, List.of(capstan));
        assertNotNull(h);
        assertEquals(h.x, h.z, 1e-9);
        assertTrue(h.x < 0);
    }

    @Test
    void aSeatInTheBlockHasNoDefault() {
        BlockPos nest = new BlockPos(3, 90, 3);
        assertNull(StationPoses.toward(new Vec3(3.5, 90, 3.5), nest, List.of(nest)));
    }
}
