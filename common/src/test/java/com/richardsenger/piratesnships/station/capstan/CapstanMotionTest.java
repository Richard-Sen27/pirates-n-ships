package com.richardsenger.piratesnships.station.capstan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.math.Axis;
import com.richardsenger.piratesnships.crew.npc.CrewPose;
import com.richardsenger.piratesnships.crew.npc.StationPoses;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** CAP2's and CAP3's pure parts: the turning per order and per anchor phase, the bars' angle on the client, the seat's walk round the drum. */
class CapstanMotionTest {

    private static final double EPS = 1e-9;

    @Test
    void raisingAndDroppingTurnOppositeWays() {
        assertEquals(CapstanTurning.RAISING, CapstanTurning.of(AnchorOrder.RAISE_ANCHOR));
        assertEquals(CapstanTurning.DROPPING, CapstanTurning.of(AnchorOrder.DROP_ANCHOR));
        assertEquals(CapstanTurning.NONE, CapstanTurning.of(null));
        assertEquals(CapstanTurning.NONE, CapstanTurning.of("hoist"));
        assertEquals(-1, CapstanTurning.RAISING.sign(), "raising turns clockwise seen from above");
        assertEquals(1, CapstanTurning.DROPPING.sign());
        assertEquals(0, CapstanTurning.NONE.sign());
        for (CapstanTurning t : CapstanTurning.values()) {
            assertEquals(t, CapstanTurning.byId(t.ordinal()));
        }
        assertEquals(CapstanTurning.NONE, CapstanTurning.byId(-1));
        assertEquals(CapstanTurning.NONE, CapstanTurning.byId(99));
    }

    /** CAP3: the bars follow the anchor's motion, whoever moves it: the chain coming in, running out, or neither. */
    @Test
    void turningFollowsTheAnchorPhase() {
        assertEquals(CapstanTurning.RAISING, CapstanTurning.of(AnchorState.Phase.RAISING, false));
        assertEquals(CapstanTurning.RAISING, CapstanTurning.of(AnchorState.Phase.RAISING, true), "winding in a taut chain still turns");
        assertEquals(CapstanTurning.DROPPING, CapstanTurning.of(AnchorState.Phase.DROPPING, false));
        assertEquals(CapstanTurning.NONE, CapstanTurning.of(AnchorState.Phase.DROPPING, true), "hanging at the chain's end");
        assertEquals(CapstanTurning.NONE, CapstanTurning.of(AnchorState.Phase.HOLDING, false));
        assertEquals(CapstanTurning.NONE, CapstanTurning.of(AnchorState.Phase.HOLDING, true));
        assertEquals(CapstanTurning.NONE, CapstanTurning.of(AnchorState.Phase.RAISED, false));
        assertEquals(CapstanTurning.NONE, CapstanTurning.of((AnchorState.Phase) null, false), "no anchor on this capstan");
    }

    /** CAP3: a crew order in progress turns the bars by the order (a drop's work before the anchor goes), else the anchor does. */
    @Test
    void crewOrderComesBeforeTheAnchor() {
        assertEquals(CapstanTurning.DROPPING, CapstanTurning.of(AnchorOrder.DROP_ANCHOR, AnchorState.Phase.RAISED, false));
        assertEquals(CapstanTurning.DROPPING, CapstanTurning.of(AnchorOrder.DROP_ANCHOR, null, false));
        assertEquals(CapstanTurning.RAISING, CapstanTurning.of(AnchorOrder.RAISE_ANCHOR, AnchorState.Phase.RAISING, false));
        assertEquals(CapstanTurning.RAISING, CapstanTurning.of(null, AnchorState.Phase.RAISING, false), "a player's raise");
        assertEquals(CapstanTurning.DROPPING, CapstanTurning.of(null, AnchorState.Phase.DROPPING, false), "a player's drop");
        assertEquals(CapstanTurning.NONE, CapstanTurning.of(null, AnchorState.Phase.HOLDING, false));
        assertEquals(CapstanTurning.NONE, CapstanTurning.of(null, null, false));
    }

    /** The seat walks with the bars: its turn is the renderer's {@code Axis.YP} turn of the same angle. */
    @Test
    void walkTurnMatchesTheRenderersTurn() {
        for (double deg : new double[] {0, 6, 45, 90, 137, -30, -200}) {
            double[] p = CapstanWalk.turn(-1.0, 0.25, deg);
            Vector3f v = Axis.YP.rotationDegrees((float) deg).transform(new Vector3f(-1.0f, 0f, 0.25f));
            assertEquals(v.x, p[0], 1e-5, "x at " + deg);
            assertEquals(v.z, p[1], 1e-5, "z at " + deg);
        }
        // a positive turn is anticlockwise seen from above: east (+x) goes to north (-z)
        double[] east = CapstanWalk.turn(1, 0, 90);
        assertEquals(0, east[0], EPS);
        assertEquals(-1, east[1], EPS);
    }

    @Test
    void walkStepsOutGoesRoundAndComesBackTheShortWay() {
        CapstanWalk w = new CapstanWalk();
        assertTrue(w.home());
        double[] home = {-1.0, 0.0}; // the spot west of the capstan
        double[] at = w.offset(home[0], home[1], 1.1);
        assertEquals(-1.0, at[0], EPS);
        assertEquals(0.0, at[1], EPS);
        Set<Integer> octants = new HashSet<>();
        for (int tick = 1; tick <= 20; tick++) {
            double[] before = w.offset(home[0], home[1], 1.1);
            w.tick(-1, 6.0);
            assertEquals(-6.0 * tick, w.angle(), 1e-6, "angle at " + tick);
            double[] after = w.offset(home[0], home[1], 1.1);
            double r = Math.hypot(after[0], after[1]);
            assertEquals(1.0 + 0.1 * Math.min(1.0, tick * CapstanWalk.STEP_OUT_PER_TICK), r, 1e-6, "radius at " + tick);
            // the facing points the way it walks
            double[] h = CapstanWalk.heading(after, -1);
            double dx = after[0] - before[0], dz = after[1] - before[1];
            assertTrue(h[0] * dx + h[1] * dz > 0, "heading against the walk at " + tick);
            assertEquals(1.0, Math.hypot(h[0], h[1]), 1e-9);
            assertEquals(0.0, h[0] * after[0] + h[1] * after[1], 1e-9, "heading not along the circle");
            octants.add((int) Math.floor((Math.toDegrees(Math.atan2(after[1], after[0])) + 360 + 22.5) / 45) % 8);
        }
        assertTrue(octants.size() >= 3, "20 ticks at 6 degrees pass " + octants);
        assertEquals(-120.0, w.angle(), 1e-6);
        // back the short way: -120 goes up to 0, not down through -180
        double last = w.angle();
        int ticks = 0;
        while (!w.home()) {
            w.tick(0, 6.0);
            assertTrue(w.angle() >= last - EPS, "went the long way: " + w.angle());
            last = w.angle();
            assertTrue(++ticks <= 30, "not back after 30 ticks");
        }
        assertEquals(20, ticks);
        double[] back = w.offset(home[0], home[1], 1.1);
        assertEquals(-1.0, back[0], EPS);
        assertEquals(0.0, back[1], EPS);
    }

    @Test
    void walkWrapsAndPastHalfATurnReturnsForward() {
        CapstanWalk w = new CapstanWalk();
        for (int i = 0; i < 40; i++) w.tick(1, 6.0); // 240 degrees anticlockwise = -120
        assertEquals(-120.0, w.angle(), 1e-6);
        w.tick(0, 6.0);
        assertEquals(-114.0, w.angle(), 1e-6, "returns by the shorter arc, onwards");
        assertEquals(0.0, CapstanWalk.heading(new double[] {0, 0}, 1)[0], EPS);
        assertEquals(0.0, CapstanWalk.heading(new double[] {1, 0}, 0)[1], EPS);
    }

    @Test
    void walkingCrewFacesAlongTheCircle() {
        // west of the axis walking clockwise seen from above (raising): it walks north, Minecraft yaw 180
        double[] h = CapstanWalk.heading(new double[] {-1.1, 0}, CapstanTurning.RAISING.sign());
        assertEquals(0, h[0], EPS);
        assertEquals(-1, h[1], EPS);
        assertEquals(180f, Math.abs(StationPoses.yaw(new Vec3(h[0], 0, h[1]), null)), 1e-4f);
        assertEquals(0f, StationPoses.yaw(new Vec3(0, 0, 1), null), 1e-4f, "south is yaw 0");
        assertEquals(90f, StationPoses.yaw(new Vec3(-1, 0, 0), null), 1e-4f, "west is yaw 90");
        StationPoses.Shown s = CapstanPoses.shown(true, BlockPos.ZERO.west(), BlockPos.ZERO, new Vec3(h[0], 0, h[1]));
        assertEquals(CrewPose.CAPSTAN_PUSH, s.pose());
        assertTrue(s.faces());
        assertEquals(180f, Math.abs(StationPoses.yaw(s, null)), 1e-4f);
        StationPoses.Shown still = CapstanPoses.shown(true, BlockPos.ZERO.west(), BlockPos.ZERO, null);
        assertEquals(-90f, StationPoses.yaw(still, null), 1e-4f, "on the spot it faces the capstan (east, yaw -90)");
    }

    @Test
    void onlyASpotBesideTheCapstanWalks() {
        assertTrue(CapstanBlockEntity.canWalk(-1, 0, 0));
        assertTrue(CapstanBlockEntity.canWalk(0, 0, 1));
        assertFalse(CapstanBlockEntity.canWalk(0, 1, 0), "on top of the capstan");
        assertFalse(CapstanBlockEntity.canWalk(0.2, 0, 0.1));
        assertFalse(CapstanBlockEntity.canWalk(-1, 1, 0), "a level higher");
    }

    @Test
    void barsTurnAtTheRateWithPartialTicks() {
        CapstanSpin s = new CapstanSpin();
        assertEquals(0.0, s.angle(100), EPS);
        s.change(CapstanTurning.RAISING.sign(), 6.0, 100.0);
        assertEquals(0.0, s.angle(100), EPS);
        assertEquals(-3.0, s.angle(100.5), EPS);
        assertEquals(-60.0, s.angle(110), EPS);
        assertEquals(-123.0, s.angle(120.5), EPS);
        // a change of rate carries on from the angle reached
        s.change(-1, 3.0, 120.5);
        assertEquals(-123.0, s.angle(120.5), EPS);
        assertEquals(-126.0, s.angle(121.5), EPS);
    }

    @Test
    void barsSettleOnTheNextQuarterTurnWithoutAJump() {
        CapstanSpin s = new CapstanSpin();
        s.change(1, 6.0, 0.0);
        double stopAt = 10.0; // 60 degrees
        double before = s.angle(stopAt);
        s.change(0, 0.0, stopAt);
        assertEquals(before, s.angle(stopAt), EPS, "no jump at the stop");
        // still moving at the turning speed right after the stop, slowing down
        double v0 = (s.angle(stopAt + 0.01) - s.angle(stopAt)) / 0.01;
        assertEquals(6.0, v0, 0.05);
        double prev = s.angle(stopAt);
        for (double t = stopAt; t < stopAt + 20; t += 0.25) {
            double a = s.angle(t);
            assertTrue(a >= prev - EPS, "ran backwards at " + t);
            assertTrue(a <= 90.0 + EPS, "ran past the quarter turn at " + t);
            prev = a;
        }
        assertEquals(90.0, s.angle(stopAt + 10.0), EPS, "30 degrees take 10 ticks to settle");
        assertEquals(90.0, s.angle(1e6), EPS);
        assertEquals(0, s.sign());
        // turning clockwise it settles on the quarter below
        CapstanSpin c = new CapstanSpin();
        c.change(-1, 6.0, 0.0);
        c.change(0, 0.0, 2.0); // -12
        assertEquals(-90.0, c.angle(100), EPS);
        // a stop exactly on a quarter turn stays there
        CapstanSpin q = new CapstanSpin();
        q.change(1, 6.0, 0.0);
        q.change(0, 0.0, 15.0); // 90
        assertEquals(90.0, q.angle(15.0), EPS);
        assertEquals(90.0, q.angle(50.0), EPS);
        // restarting while settling carries on from the angle reached
        CapstanSpin r = new CapstanSpin();
        r.change(1, 6.0, 0.0);
        r.change(0, 0.0, 10.0);
        double mid = r.angle(13.0);
        r.change(-1, 6.0, 13.0);
        assertEquals(mid, r.angle(13.0), EPS);
        assertEquals(mid - 6.0, r.angle(14.0), EPS);
    }

    @Test
    void restAngleIsTheNextQuarterInTheTurningDirection() {
        assertEquals(90.0, CapstanSpin.restAngle(1.0, 1), EPS);
        assertEquals(0.0, CapstanSpin.restAngle(1.0, -1), EPS);
        assertEquals(-90.0, CapstanSpin.restAngle(-1.0, -1), EPS);
        assertEquals(180.0, CapstanSpin.restAngle(180.0, 1), EPS);
        assertEquals(270.0, CapstanSpin.restAngle(269.99, 1), EPS);
    }
}
