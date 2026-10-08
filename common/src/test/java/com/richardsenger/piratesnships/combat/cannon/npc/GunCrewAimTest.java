package com.richardsenger.piratesnships.combat.cannon.npc;

import com.richardsenger.piratesnships.combat.cannon.CannonRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** WS4a: the pure gun-crew aim (world barrel, ballistics, arc, range, elevation choice, lead). */
class GunCrewAimTest {

    private static final double EPS = 1e-9;
    /** The cannon's defaults: 3 blocks/tick, gravity 0.03, 200 ticks; 6 steps from −5° to 20°. */
    private static final GunCrewAim.Ballistics BALL = new GunCrewAim.Ballistics(3.0, 0.03, 200);
    private static final GunCrewAim.Rules RULES = new GunCrewAim.Rules(15.0, 64.0, 0.5, 0.5);

    /** The six elevation steps of a gun at the origin's pivot with its barrel toward +x (east), standing still. */
    private static List<GunCrewAim.Shot> eastGun(Vec3 pivot) {
        List<GunCrewAim.Shot> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double e = Math.toRadians(CannonRules.elevationDegrees(i, 6, -5, 20));
            Vec3 dir = new Vec3(Math.cos(e), Math.sin(e), 0);
            out.add(new GunCrewAim.Shot(pivot.add(dir.scale(CannonRules.MUZZLE_LENGTH)), dir, Vec3.ZERO));
        }
        return out;
    }

    /** A 5×4×5 hull whose bounds centre is at {@code c}. */
    private static AABB hull(Vec3 c) {
        return new AABB(c.x - 2.5, c.y - 2, c.z - 2.5, c.x + 2.5, c.y + 2, c.z + 2.5);
    }

    private static GunCrewAim.Solution solve(Vec3 target, Vec3 velocity) {
        return GunCrewAim.solve(eastGun(Vec3.ZERO), new GunCrewAim.Target(hull(target), velocity), BALL, RULES);
    }

    // ---- world barrel ----

    @Test
    void worldShotTurnsTheBarrelWithTheShipAndAddsItsVelocityPerTick() {
        UnaryOperator<Vec3> shift = p -> p.add(100, 60, -20);
        GunCrewAim.Shot still = GunCrewAim.worldShot(new Vec3(1, 2, 3), new Vec3(0, 0, -1), shift, new Quaterniond(), Vec3.ZERO);
        assertEquals(new Vec3(101, 62, -17), still.muzzle());
        assertEquals(-1.0, still.direction().z, EPS);
        // a ship turned 90° about +y (JOML: counter-clockwise seen from above) points a north barrel west
        Quaterniond yaw = new Quaterniond().rotateY(Math.toRadians(90));
        GunCrewAim.Shot turned = GunCrewAim.worldShot(Vec3.ZERO, new Vec3(0, 0, -1), p -> p, yaw, new Vec3(20, 0, 0));
        assertEquals(-1.0, turned.direction().x, EPS);
        assertEquals(0.0, turned.direction().z, EPS);
        assertEquals(1.0, turned.carrierPerTick().x, EPS, "20 m/s is one block per tick");
        // a heeled ship (roll 10° about the barrel's side axis) raises the barrel's world elevation
        Quaterniond heel = new Quaterniond().rotateZ(Math.toRadians(10));
        GunCrewAim.Shot heeled = GunCrewAim.worldShot(Vec3.ZERO, new Vec3(1, 0, 0), p -> p, heel, Vec3.ZERO);
        assertEquals(Math.sin(Math.toRadians(10)), heeled.direction().y, 1e-9);
    }

    // ---- ballistics ----

    @Test
    void thePathFollowsVanillasThrownProjectileStep() {
        GunCrewAim.Shot s = new GunCrewAim.Shot(Vec3.ZERO, new Vec3(1, 0, 0), new Vec3(0, 0, 0.5));
        List<Vec3> path = GunCrewAim.relativePath(s, Vec3.ZERO, BALL, 2);
        assertEquals(Vec3.ZERO, path.get(0));
        Vec3 v0 = new Vec3(3, 0, 0.5);
        assertEquals(v0, path.get(1), "moves by the start velocity first");
        Vec3 v1 = v0.scale(0.99).subtract(0, 0.03, 0);
        Vec3 p2 = v0.add(v1);
        assertEquals(p2.x, path.get(2).x, EPS);
        assertEquals(p2.y, path.get(2).y, EPS, "then drag and gravity");
        // relative to a target moving +z at 0.5 blocks/tick the sideways drift cancels
        List<Vec3> rel = GunCrewAim.relativePath(s, new Vec3(0, 0, 0.5), BALL, 2);
        assertEquals(0.0, rel.get(1).z, EPS);
    }

    @Test
    void closestAndPassesThroughUseTheSegments() {
        List<Vec3> path = List.of(new Vec3(0, 0, 0), new Vec3(10, 0, 0));
        assertEquals(2.0, GunCrewAim.closest(path, new Vec3(5, 2, 0)), EPS);
        assertTrue(GunCrewAim.passesThrough(path, new AABB(4, -1, -1, 6, 1, 1)), "the segment crosses the box between its points");
        assertFalse(GunCrewAim.passesThrough(path, new AABB(4, 1.5, -1, 6, 3, 1)));
    }

    // ---- solutions ----

    @Test
    void aLevelTargetDeadAheadIsInArcInRangeAndHit() {
        GunCrewAim.Solution s = solve(new Vec3(24, -1.5, 0), Vec3.ZERO);
        assertTrue(s.inArc() && s.inRange() && s.hits(), "solution " + s);
        assertTrue(s.fires());
        assertTrue(s.miss() < 1.5, "the best step passes within a block and a half: " + s);
        assertEquals(0.0, s.offDegrees(), 1e-6);
    }

    @Test
    void aRaisedTargetNeedsAHigherStep() {
        int level = solve(new Vec3(24, -1.5, 0), Vec3.ZERO).bestStep();
        int raised = solve(new Vec3(24, 6, 0), Vec3.ZERO).bestStep();
        assertTrue(raised > level, "raised " + raised + " vs level " + level);
        assertEquals(1, GunCrewAim.stepToward(level, raised));
        assertEquals(-1, GunCrewAim.stepToward(raised, level));
        assertEquals(0, GunCrewAim.stepToward(level, level));
    }

    @Test
    void aFarTargetNeedsAHigherStepThanANearOne() {
        int near = solve(new Vec3(16, -1, 0), Vec3.ZERO).bestStep();
        int far = solve(new Vec3(60, -1, 0), Vec3.ZERO).bestStep();
        assertTrue(far > near, "far " + far + " vs near " + near);
    }

    @Test
    void aTargetAbeamIsOutsideTheArc() {
        GunCrewAim.Solution s = solve(new Vec3(0, -1, 24), Vec3.ZERO);
        assertFalse(s.inArc(), "abeam: " + s);
        assertFalse(s.fires());
        // measured from the muzzle, 1.5 blocks ahead of the pivot: atan2(24, −1.5) = 93.6°
        assertEquals(Math.toDegrees(Math.atan2(24, -CannonRules.MUZZLE_LENGTH * Math.cos(Math.toRadians(-5)))), s.offDegrees(), 0.5);
        // just inside and just outside 15° (seen from the muzzle: 14.9° and 17.1°)
        double in = Math.toRadians(14), out = Math.toRadians(16);
        assertTrue(solve(new Vec3(24 * Math.cos(in), -1, 24 * Math.sin(in)), Vec3.ZERO).inArc());
        assertFalse(solve(new Vec3(24 * Math.cos(out), -1, 24 * Math.sin(out)), Vec3.ZERO).inArc());
    }

    @Test
    void aTargetOffTheBarrelInsideTheArcIsEngagedButMissed() {
        double a = Math.toRadians(12); // 5 blocks off the line at 24: past a 5-wide hull
        GunCrewAim.Solution s = solve(new Vec3(24 * Math.cos(a), -1.5, 24 * Math.sin(a)), Vec3.ZERO);
        assertTrue(s.engageable());
        assertFalse(s.hits(), "the ball cannot turn: " + s);
    }

    @Test
    void aTargetBeyondTheRangeIsNotEngaged() {
        GunCrewAim.Solution s = solve(new Vec3(80, -1, 0), Vec3.ZERO);
        assertFalse(s.inRange());
        assertFalse(s.fires());
    }

    @Test
    void aMovingTargetIsLed() {
        // a target crossing the barrel at 6 m/s (0.3 blocks/tick): the ball needs about 8 ticks, so a target
        // dead ahead now is hit only when it starts about 2.4 blocks before the line
        Vec3 cross = new Vec3(0, 0, 0.3);
        GunCrewAim.Solution ahead = solve(new Vec3(24, -1.5, 0), cross);
        GunCrewAim.Solution early = solve(new Vec3(24, -1.5, -2.4), cross);
        assertTrue(early.miss() < ahead.miss(), "led " + early + " vs dead ahead " + ahead);
        assertTrue(early.offDegrees() < 1.5, "the led point of the early target lies on the barrel: " + early);
        assertTrue(ahead.offDegrees() > 4.0, "the led point of a target ahead now has passed the barrel: " + ahead);
        // a receding target needs a higher step than a still one at the same place
        int still = solve(new Vec3(40, -1.5, 0), Vec3.ZERO).bestStep();
        int receding = solve(new Vec3(40, -1.5, 0), new Vec3(0.6, 0, 0)).bestStep();
        assertTrue(receding >= still, "receding " + receding + " vs still " + still);
    }

    @Test
    void aimPointSitsAtTheAimHeight() {
        AABB b = new AABB(0, 10, 0, 4, 20, 6);
        assertEquals(new Vec3(2, 15, 3), GunCrewAim.aimPoint(b, 0.5));
        assertEquals(10.0, GunCrewAim.aimPoint(b, 0).y, EPS);
        assertEquals(20.0, GunCrewAim.aimPoint(b, 7).y, EPS, "clamped");
    }
}
