package com.richardsenger.piratesnships.sailing.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.wind.WindOverride;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;

class BowFrameTest {

    private static final double EPS = 1e-9;

    /** Helm facings as (stepX, stepZ): north, east, south, west. */
    private static final int[][] FACINGS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private static void assertVec(double x, double y, double z, Vector3dc v) {
        assertEquals(x, v.x(), EPS, "x of " + v);
        assertEquals(y, v.y(), EPS, "y of " + v);
        assertEquals(z, v.z(), EPS, "z of " + v);
    }

    @Test
    void bowIsOppositeOfHelmFacingAndMapsToShipPlusZ() {
        for (int[] f : FACINGS) {
            BowFrame bow = BowFrame.fromHelmFacing(f[0], f[1]);
            assertEquals(-f[0], bow.dx());
            assertEquals(-f[1], bow.dz());
            // a unit vector along the bow (plot) is +Z in the ship frame
            assertVec(0, 0, 1, bow.toShip(new Vector3d(bow.dx(), 0, bow.dz()), new Vector3d()));
            // up stays up
            assertVec(0, 1, 0, bow.toShip(new Vector3d(0, 1, 0), new Vector3d()));
            // port (+X ship) is left of the bow: up × forward in plot
            Vector3d left = new Vector3d(0, 1, 0).cross(new Vector3d(bow.dx(), 0, bow.dz()));
            assertVec(1, 0, 0, bow.toShip(left, new Vector3d()));
        }
    }

    @Test
    void exactConversionsMatchTheQuaternionAndInvert() {
        Vector3d v = new Vector3d(0.3, -1.2, 2.5);
        for (int[] f : FACINGS) {
            BowFrame bow = BowFrame.fromHelmFacing(f[0], f[1]);
            Vector3d byQuat = bow.shipToPlot().transform(new Vector3d(v));
            Vector3d exact = bow.toPlot(v, new Vector3d());
            assertEquals(0, byQuat.distance(exact), 1e-9, bow.name());
            assertEquals(0, bow.toShip(exact, new Vector3d()).distance(v), EPS, bow.name());
            assertEquals(bow, BowFrame.byName(bow.name()));
        }
    }

    @Test
    void shipToWorldComposesThePose() {
        // ship yawed 90° counter-clockwise seen from above (plot +Z → world +X), bow east in the plot (→ world −Z)
        Quaterniond plotToWorld = new Quaterniond().rotateY(Math.PI / 2);
        BowFrame bow = new BowFrame(1, 0);
        Quaterniond q = bow.shipToWorld(plotToWorld, new Quaterniond());
        Vector3d worldBow = plotToWorld.transform(new Vector3d(1, 0, 0));
        assertEquals(0, q.transform(new Vector3d(0, 0, 1)).distance(worldBow), 1e-9);
    }

    @Test
    void lengthAlongBowAndBadInput() {
        assertEquals(7, new BowFrame(1, 0).lengthOf(7, 3));
        assertEquals(3, new BowFrame(0, -1).lengthOf(7, 3));
        assertThrows(IllegalArgumentException.class, () -> new BowFrame(1, 1));
        assertThrows(IllegalArgumentException.class, () -> new BowFrame(0, 0));
    }

    @Test
    void trimCycles() {
        assertEquals(SailTrim.HALF, SailTrim.FURLED.next());
        assertEquals(SailTrim.FULL, SailTrim.HALF.next());
        assertEquals(SailTrim.FURLED, SailTrim.FULL.next());
        assertEquals("half", SailTrim.HALF.getSerializedName());
    }

    @Test
    void windOverrideBlowsFromTheGivenBearingAndExpires() {
        String dim = "test:override";
        WindOverride.set(dim, 0.0, 6.0, 100);
        WindOverride.Entry e = WindOverride.get(dim, 50);
        WindSample s = e.sample();
        // from the north = toward +Z (south)
        assertEquals(0.0, s.dirX(), EPS);
        assertEquals(1.0, s.dirZ(), EPS);
        assertEquals(6.0, s.strength(), EPS);
        assertEquals(0.0, s.fromDegrees(), EPS);
        assertEquals(null, WindOverride.get(dim, 100));
        assertEquals(null, WindOverride.get(dim, 50), "expired entries are dropped");
        WindOverride.set(dim, 90.0, 3.0, WindOverride.FOREVER);
        assertEquals(-1.0, WindOverride.get(dim, Long.MAX_VALUE - 1).sample().dirX(), EPS, "from the east blows toward −X");
        WindOverride.clear(dim);
        assertEquals(null, WindOverride.get(dim, 0));
    }
}
