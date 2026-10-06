package com.richardsenger.piratesnships.combat.melee.geometry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class HitGeometryTest {

    static final Vec EYE = new Vec(0, 1.6, 0);
    static final Vec EAST = new Vec(1, 0, 0);

    /** A player-sized box standing at (x, 0, z). */
    static HitGeometry.Target<String> mob(String key, double x, double z) {
        return new HitGeometry.Target<>(key, Box.standing(new Vec(x, 0, z), 0.6, 1.8));
    }

    static List<String> slash(Vec look, double reach, double arc, List<HitGeometry.Target<String>> t) {
        return HitGeometry.slash(EYE, look, reach, arc, 0.6, t);
    }

    @Test
    void slashHitsSeveralTargetsInArcNearestFirst() {
        var targets = List.of(mob("far", 2.3, 0), mob("near", 1.5, 0.3), mob("side", 1.5, -0.8));
        assertEquals(List.of("near", "side", "far"), slash(EAST, 2.5, 90, targets));
    }

    @Test
    void slashMissesTargetsBehindOutsideArcOrOutOfReach() {
        assertEquals(List.of(), slash(EAST, 2.5, 90, List.of(mob("behind", -1.5, 0))));
        assertEquals(List.of(), slash(EAST, 2.5, 90, List.of(mob("left", 0.3, 2.0))));
        // just out of reach: front face at 2.5 + 0.01
        assertEquals(List.of(), slash(EAST, 2.5, 90, List.of(mob("far", 2.81, 0))));
        assertEquals(List.of("edge"), slash(EAST, 2.5, 90, List.of(mob("edge", 2.79, 0))));
    }

    @Test
    void slashHitsTargetOnlyPartlyInsideTheArc() {
        // center at 52° off the look direction (outside a 90° arc), but the box reaches into the arc
        double a = Math.toRadians(52);
        var wide = new HitGeometry.Target<>("wide", Box.standing(new Vec(1.5 * Math.cos(a), 0, 1.5 * Math.sin(a)), 1.2, 1.8));
        assertEquals(List.of("wide"), slash(EAST, 2.5, 90, List.of(wide)));
        var small = new HitGeometry.Target<>("small", Box.standing(new Vec(1.5 * Math.cos(a), 0, 1.5 * Math.sin(a)), 0.1, 1.8));
        assertEquals(List.of(), slash(EAST, 2.5, 90, List.of(small)));
    }

    @Test
    void slashThroughBoxCrossingRadialEdgeOnly() {
        // a long thin wall across the arc edge whose corners and nearest point are all outside the pie
        var wall = new HitGeometry.Target<>("wall", new Box(0.5, 0, 1.0, 3.0, 2, 1.1));
        assertEquals(List.of("wall"), slash(EAST, 2.5, 90, List.of(wall)));
    }

    @Test
    void slashVerticalBandFollowsPitch() {
        var low = new HitGeometry.Target<>("low", new Box(1.0, -0.5, -0.3, 1.6, 0.2, 0.3));
        assertEquals(List.of(), slash(EAST, 2.5, 90, List.of(low)));
        Vec down = new Vec(1, -1, 0).normalize();
        assertEquals(List.of("low"), slash(down, 2.5, 90, List.of(low)));
    }

    @Test
    void attackerInsideTargetBoxAlwaysHits() {
        var around = new HitGeometry.Target<>("around", new Box(-1, 0, -1, 1, 2, 1));
        assertEquals(List.of("around"), slash(EAST, 2.5, 30, List.of(around)));
        assertEquals(Optional.of("around"), HitGeometry.thrust(EYE, EAST, 3, 0.2, List.of(around)));
    }

    @Test
    void zeroOrVerticalLookSlashesNothing() {
        var t = List.of(mob("front", 1.5, 0));
        assertEquals(List.of(), slash(Vec.ZERO, 2.5, 90, t));
        assertEquals(List.of(), slash(new Vec(0, 1, 0), 2.5, 90, t));
        assertEquals(Optional.empty(), HitGeometry.thrust(EYE, Vec.ZERO, 3, 0.2, t));
    }

    @Test
    void verticalThrustStillWorks() {
        var below = new HitGeometry.Target<>("below", new Box(-0.3, -1.0, -0.3, 0.3, 0.0, 0.3));
        assertEquals(Optional.of("below"), HitGeometry.thrust(EYE, new Vec(0, -1, 0), 3, 0.1, List.of(below)));
    }

    @Test
    void thrustHitsOnlyTheFirstTargetInLine() {
        var t = List.of(mob("second", 2.5, 0), mob("first", 1.5, 0));
        assertEquals(Optional.of("first"), HitGeometry.thrust(EYE, EAST, 3.6, 0.25, t));
    }

    @Test
    void thrustNearMissesAndThickness() {
        // box edge 0.4 beside the ray: missed by a thin ray, hit by a thick one
        var beside = new HitGeometry.Target<>("beside", new Box(1.0, 0, 0.4, 1.6, 2, 1.0));
        assertEquals(Optional.empty(), HitGeometry.thrust(EYE, EAST, 3, 0.2, List.of(beside)));
        assertEquals(Optional.of("beside"), HitGeometry.thrust(EYE, EAST, 3, 0.45, List.of(beside)));
        // behind and out of reach
        assertEquals(Optional.empty(), HitGeometry.thrust(EYE, EAST, 3, 0.2, List.of(mob("behind", -1.5, 0))));
        assertEquals(Optional.empty(), HitGeometry.thrust(EYE, EAST, 0.9, 0.2, List.of(mob("far", 1.5, 0))));
        // look vector length doesn't matter
        assertEquals(Optional.of("f"), HitGeometry.thrust(EYE, new Vec(10, 0, 0), 3, 0.2, List.of(mob("f", 1.5, 0))));
    }

    @Test
    void veryLargeAndVerySmallBoxes() {
        var huge = new HitGeometry.Target<>("huge", new Box(5, -100, -100, 200, 100, 100));
        assertEquals(List.of(), slash(EAST, 2.5, 90, List.of(huge)), "huge box beyond reach");
        var hugeNear = new HitGeometry.Target<>("hugeNear", new Box(2, -100, -100, 200, 100, 100));
        assertEquals(List.of("hugeNear"), slash(EAST, 2.5, 90, List.of(hugeNear)));
        assertEquals(Optional.of("hugeNear"), HitGeometry.thrust(EYE, EAST, 3, 0, List.of(hugeNear)));
        var tiny = new HitGeometry.Target<>("tiny", new Box(1.5, 1.6, 0, 1.5001, 1.6001, 0.0001));
        assertEquals(List.of("tiny"), slash(EAST, 2.5, 90, List.of(tiny)));
        assertEquals(Optional.of("tiny"), HitGeometry.thrust(EYE, EAST, 3, 0.01, List.of(tiny)));
        var point = new HitGeometry.Target<>("point", new Box(1.5, 1.7, 0.05, 1.5, 1.7, 0.05));
        assertEquals(Optional.empty(), HitGeometry.thrust(EYE, EAST, 3, 0.01, List.of(point)));
    }

    @Test
    void sameResultInAShipFrame() {
        // translating everything (a ship-relative frame) doesn't change hits
        Vec off = new Vec(1000.5, 64, -321.25);
        var t = List.of(new HitGeometry.Target<>("a", Box.standing(new Vec(1.5, 0, 0).add(off), 0.6, 1.8)));
        assertEquals(List.of("a"), HitGeometry.slash(EYE.add(off), EAST, 2.5, 90, 0.6, t));
        assertEquals(Optional.of("a"), HitGeometry.thrust(EYE.add(off), EAST, 3, 0.2, t));
    }

    @Test
    void inFrontUsesFullArcAngle() {
        Vec eye = Vec.ZERO;
        assertTrue(HitGeometry.inFront(eye, EAST, new Vec(2, 0, 0), 100));
        assertTrue(HitGeometry.inFront(eye, EAST, new Vec(2, 0, 2.3), 100)); // ~49°
        assertFalse(HitGeometry.inFront(eye, EAST, new Vec(2, 0, 2.6), 100)); // ~52°
        assertFalse(HitGeometry.inFront(eye, EAST, new Vec(-2, 0, 0), 180));
        assertTrue(HitGeometry.inFront(eye, EAST, new Vec(-2, 0, 0), 360));
        assertTrue(HitGeometry.inFront(eye, EAST, eye, 10), "no direction counts as frontal");
        assertTrue(HitGeometry.inFront(eye, new Vec(0, 1, 0), new Vec(-2, 0, 0), 10), "vertical look counts as frontal");
    }
}
