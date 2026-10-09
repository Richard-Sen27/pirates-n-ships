package com.richardsenger.piratesnships.hazards.waves.client.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.hazards.waves.WaveField;
import com.richardsenger.piratesnships.hazards.waves.WaveSpectrum;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class SurfaceMesherTest {

    static final double BASE = SurfaceRules.stillTop(63);
    static final WaveField STORM = new WaveField(1.2, 137.0, WaveSpectrum.components(6, 34.0), WaveField.Origin.NONE,
            new WaveField.Groups(0.35, 1200.0));

    /** Synthetic world: a depth per column. */
    record Sea(IntBinaryOperator depthAt) implements SurfaceMesher.Cells {
        @Override
        public int depth(int x, int z) {
            return depthAt.applyAsInt(x, z);
        }

        @Override
        public int minDepth(int x, int z, int size) {
            int min = Integer.MAX_VALUE;
            for (int dz = 0; dz < size; dz++) {
                for (int dx = 0; dx < size; dx++) {
                    min = Math.min(min, depth(x + dx, z + dz));
                }
            }
            return min;
        }

        @Override
        public boolean anySea(int x, int z, int size) {
            for (int dz = 0; dz < size; dz++) {
                for (int dx = 0; dx < size; dx++) {
                    if (depth(x + dx, z + dz) > 0) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public int color(int x, int z) {
            return 0x3F76E4;
        }

        @Override
        public int light(int x, int z) {
            return 0xF000F0;
        }
    }

    record V(double x, double y, double z, boolean up) {
    }

    /** Collects quads. */
    static final class Quads implements SurfaceMesher.Sink {
        final List<V[]> quads = new ArrayList<>();
        private final List<V> open = new ArrayList<>();

        @Override
        public void vertex(double x, double y, double z, int rgb, float u, float v, int light, boolean up) {
            open.add(new V(x, y, z, up));
            if (open.size() == 4) {
                quads.add(open.toArray(new V[0]));
                open.clear();
            }
        }

        /** y component of the quad's normal (from its winding). */
        static double normalY(V[] q) {
            double ax = q[1].x - q[0].x, az = q[1].z - q[0].z, bx = q[2].x - q[0].x, bz = q[2].z - q[0].z;
            return az * bx - ax * bz;
        }
    }

    static SurfaceMesher.Params params(boolean underside) {
        return new SurfaceMesher.Params(BASE, 4, underside, 0f, 0f, 1f, 1f);
    }

    @Test
    void deepFlatSeaIsOneLevelQuadPerCell() {
        SurfaceLod.Layout l = SurfaceLod.layout(0.5, 0.5, 32, 128, 16, 192);
        Quads out = new Quads();
        SurfaceMesher.Stats s = new SurfaceMesher().build(l, new Sea((x, z) -> 10), new SurfaceWaves().prepare(WaveField.FLAT, 0, 0, 0),
                params(false), SurfaceMesher.Visibility.ALL, out);
        assertEquals(l.cellCount(), s.cells());
        assertEquals(0, s.seams(), "a flat sea has no slits to close");
        assertEquals(4 * l.cellCount(), s.vertices());
        for (V[] q : out.quads) {
            for (V v : q) {
                assertEquals(BASE, v.y, 1e-12);
                assertTrue(v.up);
            }
            assertTrue(Quads.normalY(q) > 0, "top quads face up");
        }
    }

    @Test
    void undersideFacesDown() {
        SurfaceLod.Layout l = SurfaceLod.layout(0, 0, 8, 32, 16, 32);
        Quads out = new Quads();
        SurfaceMesher.Stats s = new SurfaceMesher().build(l, new Sea((x, z) -> 10), new SurfaceWaves().prepare(WaveField.FLAT, 0, 0, 0),
                params(true), SurfaceMesher.Visibility.ALL, out);
        assertEquals(8 * l.cellCount(), s.vertices());
        int up = 0, down = 0;
        for (V[] q : out.quads) {
            if (q[0].up) {
                up++;
                assertTrue(Quads.normalY(q) > 0);
            } else {
                down++;
                assertTrue(Quads.normalY(q) < 0);
            }
        }
        assertEquals(up, down);
    }

    @Test
    void deepVerticesFollowTheField() {
        SurfaceLod.Layout l = SurfaceLod.layout(100.3, -40.2, 32, 128, 16, 192);
        SurfaceWaves w = new SurfaceWaves().prepare(STORM, 90, -30, 4321.5);
        SurfaceMesher m = new SurfaceMesher();
        m.build(l, new Sea((x, z) -> 12), w, params(false), SurfaceMesher.Visibility.ALL, new Quads());
        SurfaceLod.Square near = l.near().outer();
        for (int z = near.minZ(); z <= near.maxZ(); z += 3) {
            for (int x = near.minX(); x <= near.maxX(); x += 3) {
                assertEquals(BASE + w.height(x, z), m.nearCornerHeight(x, z), 1e-9);
            }
        }
    }

    /** A beach: land for x < 0, the sea deepening by one block per block from x = 0. */
    @Test
    void wavesFadeOutTowardTheBeachAndNeverCoverLand() {
        SurfaceLod.Layout l = SurfaceLod.layout(0, 0, 32, 64, 16, 64);
        SurfaceWaves w = new SurfaceWaves().prepare(STORM, 0, 0, 777);
        Sea beach = new Sea((x, z) -> x < 0 ? 0 : Math.min(16, x + 1));
        SurfaceMesher m = new SurfaceMesher();
        Quads out = new Quads();
        m.build(l, beach, w, params(false), SurfaceMesher.Visibility.ALL, out);
        for (int z = -20; z <= 20; z++) {
            assertEquals(BASE, m.nearCornerHeight(0, z), 1e-12, "the shoreline lies still");
            assertEquals(BASE, m.nearCornerHeight(1, z), 1e-12, "depth 1 next to it: fade 0");
            // x = 2: cells at depth 2 and 3 -> fade 1/3
            assertEquals(BASE + w.height(2, z) / 3.0, m.nearCornerHeight(2, z), 1e-9);
            assertEquals(BASE + w.height(5, z), m.nearCornerHeight(5, z), 1e-9, "full from four blocks");
        }
        for (V[] q : out.quads) {
            for (V v : q) {
                if (v.x < 0) {
                    // only coarse cells reach over the land, and those lie flat at the still top
                    assertEquals(BASE, v.y, 1e-12);
                }
            }
        }
        // the near ring has no cell over land
        for (V[] q : out.quads) {
            double minX = Math.min(Math.min(q[0].x, q[1].x), Math.min(q[2].x, q[3].x));
            double maxX = Math.max(Math.max(q[0].x, q[1].x), Math.max(q[2].x, q[3].x));
            if (maxX - minX == 1 && Math.abs(q[0].z) < 30 && maxX <= 30) {
                assertTrue(minX >= 0, "1-block cell over land at x " + minX);
            }
        }
    }

    /** Every slit between a fine ring edge and the coarse edge next to it is closed by a skirt with exactly its outline. */
    @Test
    void seamSkirtsCloseEverySlit() {
        for (double[] cam : new double[][] {{0.5, 0.5}, {13.7, -29.2}, {-1000.4, 777.9}}) {
            SurfaceLod.Layout l = SurfaceLod.layout(cam[0], cam[1], 16, 64, 16, 160);
            SurfaceWaves w = new SurfaceWaves().prepare(STORM, cam[0], cam[1], 1000);
            Sea sea = new Sea((x, z) -> 9);
            Quads out = new Quads();
            SurfaceMesher.Stats s = new SurfaceMesher().build(l, sea, w, params(false), SurfaceMesher.Visibility.ALL, out);
            assertEquals(l.seamCount(), s.seams(), "a stormy all-sea frame skirts every fine boundary edge");

            List<V[]> skirts = new ArrayList<>();
            for (V[] q : out.quads) {
                boolean vertical = q[0].x == q[1].x && q[1].x == q[2].x || q[0].z == q[1].z && q[1].z == q[2].z;
                if (vertical) {
                    skirts.add(q);
                }
            }
            assertEquals(2 * l.seamCount(), skirts.size(), "two faces per skirt");
            // check each fine boundary edge of the near square against the coarse line
            check(l.near(), l.mid(), w, skirts);
            check(l.mid(), l.far(), w, skirts);
        }
    }

    private static void check(SurfaceLod.Ring fine, SurfaceLod.Ring coarse, SurfaceWaves w, List<V[]> skirts) {
        SurfaceLod.Square b = fine.outer();
        int s = fine.cell(), big = coarse.cell();
        // west side only (the four sides share the code); the others are counted above
        int x = b.minX();
        for (int z = b.minZ(); z < b.maxZ(); z += s) {
            double hf0 = BASE + w.height(x, z), hf1 = BASE + w.height(x, z + s);
            int cz = Math.floorDiv(z, big) * big;
            double hc0 = BASE + w.height(x, cz), hc1 = BASE + w.height(x, cz + big);
            double l0 = hc0 + (hc1 - hc0) * (z - cz) / (double) big, l1 = hc0 + (hc1 - hc0) * (z + s - cz) / (double) big;
            boolean found = false;
            for (V[] q : skirts) {
                if (q[0].x == x && q[1].x == x && q[0].z == z && q[1].z == z + s && q[0].up
                        && Math.abs(q[0].y - hf0) < 1e-9 && Math.abs(q[1].y - hf1) < 1e-9
                        && Math.abs(q[2].y - l1) < 1e-9 && Math.abs(q[3].y - l0) < 1e-9) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "skirt at x " + x + ", z " + z);
        }
    }

    @Test
    void hiddenTilesAndDryCellsAreSkipped() {
        SurfaceLod.Layout l = SurfaceLod.layout(0, 0, 16, 32, 16, 32);
        Sea sea = new Sea((x, z) -> 9);
        // only x >= 0 visible
        SurfaceMesher.Stats s = new SurfaceMesher().build(l, sea, new SurfaceWaves().prepare(WaveField.FLAT, 0, 0, 0), params(false),
                (x0, z0, x1, z1) -> x0 >= 0, new Quads());
        assertEquals(l.cellCount() / 2, s.cells());
        SurfaceMesher.Cells dry = new SurfaceMesher.Cells() {
            @Override public int depth(int x, int z) { return 9; }
            @Override public int minDepth(int x, int z, int size) { return 9; }
            @Override public boolean anySea(int x, int z, int size) { return true; }
            @Override public int color(int x, int z) { return 0; }
            @Override public int light(int x, int z) { return 0; }
            @Override public boolean dry(int x, int z) { return x >= 0 && x < 4 && z >= 0 && z < 10; }
        };
        SurfaceMesher.Stats d = new SurfaceMesher().build(l, dry, new SurfaceWaves().prepare(WaveField.FLAT, 0, 0, 0), params(false),
                SurfaceMesher.Visibility.ALL, new Quads());
        assertEquals(l.cellCount() - 40, d.cells(), "the 4×10 cells inside the hull are not drawn");
    }

    /**
     * Headless cost estimate (printed for the hand-back): a storm over an all-sea world at the defaults, render distance
     * 12 and 32 chunks, with the underside; build time on this machine (CPU only, no upload) and vertex counts.
     */
    @Test
    void frameCostEstimate() {
        for (int rd : new int[] {12, 32}) {
            SurfaceLod.Layout l = SurfaceLod.layout(5000.5, -3000.5, 32, 128, 16, rd * 16);
            SurfaceMesher.Cells sea = new SurfaceMesher.Cells() { // constant time, like the client cache's aggregates
                @Override public int depth(int x, int z) { return 12; }
                @Override public int minDepth(int x, int z, int size) { return 12; }
                @Override public boolean anySea(int x, int z, int size) { return true; }
                @Override public int color(int x, int z) { return 0x3F76E4; }
                @Override public int light(int x, int z) { return 0xF000F0; }
            };
            SurfaceMesher m = new SurfaceMesher();
            SurfaceWaves w = new SurfaceWaves();
            long[] count = {0};
            SurfaceMesher.Sink sink = (x, y, z, rgb, u, v, light, up) -> count[0]++;
            SurfaceMesher.Stats s = null;
            for (int i = 0; i < 400; i++) { // warm-up
                s = m.build(l, sea, w.prepare(STORM, 5000, -3000, i), params(true), SurfaceMesher.Visibility.ALL, sink);
            }
            int runs = 200;
            long t0 = System.nanoTime();
            for (int i = 0; i < runs; i++) {
                s = m.build(l, sea, w.prepare(STORM, 5000, -3000, 100 + i), params(true), SurfaceMesher.Visibility.ALL, sink);
            }
            double ms = (System.nanoTime() - t0) / 1e6 / runs;
            System.out.printf("[SurfaceMesher] render distance %d: %d cells, %d skirts, %d vertices (%.2f MB), %.3f ms per frame "
                    + "to build (all sea, storm, underside on, frustum off)%n", rd, s.cells(), s.seams(), s.vertices(),
                    s.vertices() * 28 / 1e6, ms);
            assertTrue(s.vertices() <= SurfaceLod.maxVertices(l, true));
        }
    }
}
