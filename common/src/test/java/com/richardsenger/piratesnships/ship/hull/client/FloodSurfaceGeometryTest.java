package com.richardsenger.piratesnships.ship.hull.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.ship.hull.runtime.CellSet;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** FLD1: cells → surface polygons at a level, clipping to the compartment, quads, easing, the below test. */
class FloodSurfaceGeometryTest {

    private static final double EPS = 1e-9;

    /** A cell set of size sx×sy×sz at plot (100, 60, -40) with the given local cells set ({x, y, z} triples). */
    private static CellSet set(int sx, int sy, int sz, int[]... cells) {
        BitSet b = new BitSet();
        for (int[] c : cells) {
            b.set(c[0] + sx * (c[2] + sz * c[1]));
        }
        return new CellSet(100, 60, -40, sx, sy, sz, b);
    }

    /** A full box. */
    private static CellSet box(int sx, int sy, int sz) {
        BitSet b = new BitSet();
        b.set(0, sx * sy * sz);
        return new CellSet(100, 60, -40, sx, sy, sz, b);
    }

    private record Poly(int x, int y, int z, double[] xyz) {
        int count() {
            return xyz.length / 3;
        }
    }

    private static List<Poly> polys(CellSet s, double nx, double ny, double nz, double d) {
        List<Poly> out = new ArrayList<>();
        int n = FloodSurfaceGeometry.polygons(s, nx, ny, nz, d, (x, y, z, xyz, count) -> out.add(new Poly(x, y, z, java.util.Arrays.copyOf(xyz, 3 * count))));
        assertEquals(out.size(), n);
        return out;
    }

    /** Area of a planar polygon via the cross-product sum, projected on n (positive when counter-clockwise about n). */
    private static double signedArea(double[] p, double nx, double ny, double nz) {
        double cx = 0, cy = 0, cz = 0;
        int k = p.length / 3;
        for (int i = 0; i < k; i++) {
            int j = (i + 1) % k;
            cx += p[3 * i + 1] * p[3 * j + 2] - p[3 * i + 2] * p[3 * j + 1];
            cy += p[3 * i + 2] * p[3 * j] - p[3 * i] * p[3 * j + 2];
            cz += p[3 * i] * p[3 * j + 1] - p[3 * i + 1] * p[3 * j];
        }
        return 0.5 * (cx * nx + cy * ny + cz * nz);
    }

    private static void assertInsideCellAndOnPlane(Poly p, double nx, double ny, double nz, double d) {
        for (int i = 0; i < p.count(); i++) {
            double x = p.xyz()[3 * i], y = p.xyz()[3 * i + 1], z = p.xyz()[3 * i + 2];
            assertEquals(d, nx * x + ny * y + nz * z, 1e-9, "vertex off the plane");
            assertTrue(x >= p.x() - EPS && x <= p.x() + 1 + EPS && y >= p.y() - EPS && y <= p.y() + 1 + EPS
                    && z >= p.z() - EPS && z <= p.z() + 1 + EPS, "vertex outside its cell " + p);
        }
    }

    @Test
    void uprightSurfaceIsOneUnitSquarePerColumnFacingUp() {
        CellSet hold = box(3, 2, 3);
        List<Poly> ps = polys(hold, 0, 1, 0, 0.5);
        assertEquals(9, ps.size());
        for (Poly p : ps) {
            assertEquals(0, p.y(), "the half-full bottom layer carries the surface");
            assertEquals(4, p.count());
            assertEquals(1.0, signedArea(p.xyz(), 0, 1, 0), EPS, "a unit square, counter-clockwise seen from above");
            assertInsideCellAndOnPlane(p, 0, 1, 0, 0.5);
        }
    }

    @Test
    void wholeLevelBelongsToTheLowerCellOnly() {
        List<Poly> ps = polys(box(3, 2, 3), 0, 1, 0, 1.0);
        assertEquals(9, ps.size(), "no doubled faces between the layers");
        assertTrue(ps.stream().allMatch(p -> p.y() == 0), "drawn as the top face of the bottom layer");
        assertTrue(polys(box(3, 2, 3), 0, 1, 0, 0.0).isEmpty(), "an empty compartment has no surface");
        assertTrue(polys(box(3, 2, 3), 0, 1, 0, 2.0).size() == 9, "a full one has it on the top layer's top face");
        assertTrue(polys(box(3, 2, 3), 0, 1, 0, 2.5).isEmpty(), "above the compartment");
    }

    @Test
    void surfaceIsClippedToTheCompartmentsCells() {
        // an L-shaped bottom layer and a one-cell shaft above its corner (a hatch column)
        CellSet l = set(3, 2, 3, new int[] {0, 0, 0}, new int[] {1, 0, 0}, new int[] {2, 0, 0}, new int[] {0, 0, 1},
                new int[] {0, 0, 2}, new int[] {0, 1, 0});
        List<Poly> low = polys(l, 0, 1, 0, 0.7);
        assertEquals(5, low.size(), "only the L's five cells");
        for (Poly p : low) {
            assertTrue(l.contains(100 + p.x(), 60 + p.y(), -40 + p.z()), "polygon in a cell outside the set: " + p);
        }
        List<Poly> high = polys(l, 0, 1, 0, 1.4);
        assertEquals(1, high.size(), "only the shaft once the water is above the L");
        assertEquals(1, high.get(0).y());
    }

    @Test
    void tiltedSurfaceCoversTheFootprintWithoutGaps() {
        double a = Math.toRadians(20);
        double nx = Math.sin(a), ny = Math.cos(a);
        CellSet hold = box(6, 6, 4);
        double d = nx * 3 + ny * 3; // through the box centre
        List<Poly> ps = polys(hold, nx, ny, 0, d);
        double area = 0;
        for (Poly p : ps) {
            assertTrue(p.count() >= 3 && p.count() <= 6);
            assertInsideCellAndOnPlane(p, nx, ny, 0, d);
            double s = signedArea(p.xyz(), nx, ny, 0);
            assertTrue(s > 0, "counter-clockwise about the normal");
            area += s;
        }
        // the plane stays inside the box vertically, so its area is the footprint over cos(tilt)
        assertEquals(6 * 4 / Math.cos(a), area, 1e-9);
        assertTrue(ps.size() > 24, "a tilted plane crosses more than one cell per column");
    }

    @Test
    void diagonalPlaneMakesHexagonsAndCornerTouchesAreSkipped() {
        double r = 1 / Math.sqrt(3);
        List<Poly> mid = polys(box(1, 1, 1), r, r, r, 1.5 * r);
        assertEquals(1, mid.size());
        assertEquals(6, mid.get(0).count(), "the cube's middle cut is a hexagon");
        assertEquals(Math.sqrt(3) * 3 / 4, signedArea(mid.get(0).xyz(), r, r, r), 1e-9);
        assertTrue(polys(box(1, 1, 1), r, r, r, 3 * r).isEmpty(), "the plane only touches the top corner");
    }

    @Test
    void quadsFanOutWithARepeatedVertexForOddCounts() {
        assertArrayEquals(new int[] {0, 1, 2, 2}, FloodSurfaceGeometry.quadIndices(3));
        assertArrayEquals(new int[] {0, 1, 2, 3}, FloodSurfaceGeometry.quadIndices(4));
        assertArrayEquals(new int[] {0, 1, 2, 3, 0, 3, 4, 4}, FloodSurfaceGeometry.quadIndices(5));
        assertArrayEquals(new int[] {0, 1, 2, 3, 0, 3, 4, 5}, FloodSurfaceGeometry.quadIndices(6));
        assertEquals(0, FloodSurfaceGeometry.quadIndices(2).length);
    }

    @Test
    void anchorIsTheSurfaceCentre() {
        assertArrayEquals(new double[] {1.5, 0.5, 1.5}, FloodSurfaceGeometry.anchor(box(3, 2, 3), 0, 1, 0, 0.5), EPS);
        // nothing crossed: the plane point above the box centre
        assertArrayEquals(new double[] {1.5, 0.0, 1.5}, FloodSurfaceGeometry.anchor(box(3, 2, 3), 0, 1, 0, 0.0), EPS);
    }

    @Test
    void belowTestsTheCellAndThePlane() {
        CellSet l = set(3, 2, 3, new int[] {0, 0, 0}, new int[] {1, 0, 0});
        assertTrue(FloodSurfaceGeometry.below(l, 0.5, 0.3, 0.5, 0, 1, 0, 0.6));
        assertFalse(FloodSurfaceGeometry.below(l, 0.5, 0.7, 0.5, 0, 1, 0, 0.6), "above the surface");
        assertFalse(FloodSurfaceGeometry.below(l, 2.5, 0.3, 0.5, 0, 1, 0, 0.6), "not a cell of the compartment");
        assertFalse(FloodSurfaceGeometry.below(l, -0.5, 0.3, 0.5, 0, 1, 0, 0.6), "outside the box");
    }

    @Test
    void uvTilesOncePerBlockOnTheFacingFace() {
        double[] uv = new double[2];
        FloodSurfaceGeometry.uv(2.25, 0.5, 1.75, 2, 0, 1, 0, 1, 0, uv);
        assertArrayEquals(new double[] {0.25, 0.75}, uv, EPS);
        FloodSurfaceGeometry.uv(2.0, 0.4, 1.9, 2, 0, 1, 0.9, 0.1, 0, uv);
        assertArrayEquals(new double[] {0.9, 0.4}, uv, EPS, "a surface facing x maps z and y");
    }

    @Test
    void easeIsLinearAndClamped() {
        assertEquals(1.0, FloodSurfaceGeometry.ease(1, 2, 10, 10, 5), EPS);
        assertEquals(1.25, FloodSurfaceGeometry.ease(1, 2, 10, 10, 12.5), EPS);
        assertEquals(2.0, FloodSurfaceGeometry.ease(1, 2, 10, 10, 20), EPS);
        assertEquals(2.0, FloodSurfaceGeometry.ease(1, 2, 10, 0, 10), EPS);
    }

    @Test
    void basisIsRightHanded() {
        double a = Math.toRadians(35);
        double nx = Math.sin(a) * 0.6, ny = Math.cos(a), nz = Math.sin(a) * 0.8;
        double[] b = FloodSurfaceGeometry.basis(nx, ny, nz);
        // u × v = n
        assertEquals(nx, b[1] * b[5] - b[2] * b[4], 1e-12);
        assertEquals(ny, b[2] * b[3] - b[0] * b[5], 1e-12);
        assertEquals(nz, b[0] * b[4] - b[1] * b[3], 1e-12);
    }
}
