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
        return new SurfaceMesher.Params(BASE, 4, underside, 0, 0);
    }

    @Test
    void deepFlatSeaIsOneLevelQuadPerCell() {
        SurfaceLod.Layout l = SurfaceLod.layout(0.5, 0.5, 32, 128, 16, 192);
        Quads out = new Quads();
        SurfaceMesher.Stats s = new SurfaceMesher().build(l, new Sea((x, z) -> 10), new SurfaceWaves().prepare(WaveField.FLAT, 0),
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
        SurfaceMesher.Stats s = new SurfaceMesher().build(l, new Sea((x, z) -> 10), new SurfaceWaves().prepare(WaveField.FLAT, 0),
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
        SurfaceWaves w = new SurfaceWaves().prepare(STORM, 4321.5);
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
        SurfaceWaves w = new SurfaceWaves().prepare(STORM, 777);
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
                    // only coarse cells reach over the land, and their corners there touch land: still
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
            SurfaceWaves w = new SurfaceWaves().prepare(STORM, 1000);
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
        SurfaceMesher.Stats s = new SurfaceMesher().build(l, sea, new SurfaceWaves().prepare(WaveField.FLAT, 0), params(false),
                (x0, z0, x1, z1) -> x0 >= 0, new Quads());
        assertEquals(l.cellCount() / 2, s.cells());
        SurfaceMesher.Cells dry = new SurfaceMesher.Cells() {
            @Override public int depth(int x, int z) { return 9; }
            @Override public boolean anySea(int x, int z, int size) { return true; }
            @Override public int color(int x, int z) { return 0; }
            @Override public int light(int x, int z) { return 0; }
            @Override public boolean dry(int x, int z) { return x >= 0 && x < 4 && z >= 0 && z < 10; }
        };
        SurfaceMesher.Stats d = new SurfaceMesher().build(l, dry, new SurfaceWaves().prepare(WaveField.FLAT, 0), params(false),
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
                s = m.build(l, sea, w.prepare(STORM, i), params(true), SurfaceMesher.Visibility.ALL, sink);
            }
            int runs = 200;
            long t0 = System.nanoTime();
            for (int i = 0; i < runs; i++) {
                s = m.build(l, sea, w.prepare(STORM, 100 + i), params(true), SurfaceMesher.Visibility.ALL, sink);
            }
            double ms = (System.nanoTime() - t0) / 1e6 / runs;
            System.out.printf("[SurfaceMesher] render distance %d: %d cells, %d skirts, %d vertices (%.2f MB), %.3f ms per frame "
                    + "to build (all sea, storm, underside on, frustum off)%n", rd, s.cells(), s.seams(), s.vertices(),
                    s.vertices() * 28 / 1e6, ms);
            assertTrue(s.vertices() <= SurfaceLod.maxVertices(l, true));
        }
    }

    // ------------------------------------------------------------------ WAV5

    /** Every vertex of every ring and skirt maps to its world block: one texture tile per block whatever the cell size. */
    @Test
    void textureTilesOncePerBlockInEveryRing() {
        double camX = -36.4, camZ = 1201.7;
        int ox = (int) Math.floor(camX), oz = (int) Math.floor(camZ);
        SurfaceLod.Layout l = SurfaceLod.layout(camX, camZ, 16, 64, 16, 160);
        SurfaceWaves w = new SurfaceWaves().prepare(STORM, 1000);
        List<float[]> verts = new ArrayList<>();
        java.util.Set<Integer> spans = new java.util.TreeSet<>();
        float[][] quad = new float[4][];
        int[] n = {0};
        SurfaceMesher.Sink sink = (x, y, z, rgb, u, v, light, up) -> {
            float[] vx = {(float) x, (float) z, u, v};
            verts.add(vx);
            quad[n[0]++ & 3] = vx;
            if ((n[0] & 3) == 0) {
                float du = Math.abs(quad[2][2] - quad[0][2]);
                float dx = Math.abs(quad[2][0] - quad[0][0]);
                assertEquals(dx, du, 0f, "a quad's texture span equals its width in blocks");
                if (dx > 0) {
                    spans.add((int) dx);
                }
            }
        };
        SurfaceMesher.Stats s = new SurfaceMesher().build(l, new Sea((x, z) -> 9), w,
                new SurfaceMesher.Params(BASE, 4, true, ox, oz), SurfaceMesher.Visibility.ALL, sink);
        assertTrue(s.seams() > 0);
        for (float[] v : verts) {
            assertEquals(v[0] - ox, v[2], 0f, "u = x - origin");
            assertEquals(v[1] - oz, v[3], 0f, "v = z - origin");
        }
        assertTrue(spans.containsAll(List.of(1, 4, 16)), "cells of every ring were checked: " + spans);
        // the origin shifts the coordinates by whole tiles only
        assertEquals(5f, SurfaceMesher.textureU(5, -2) - SurfaceMesher.textureU(5, 3), 0f);
        assertEquals(17f, SurfaceMesher.textureU(30_000_017, 30_000_000), 0f, "small near the camera even far out");
    }

    /**
     * A 16-block far cell over a beach (land for x &lt; 0, the sea deepening one block per block): WAV3 laid it flat
     * because one of its columns is shallow; now each vertex takes its own corner's fade.
     */
    @Test
    void coarseCoastVerticesFollowTheirCornersDepth() {
        SurfaceLod.Layout l = SurfaceLod.layout(-200.5, 0.5, 16, 64, 16, 320);
        SurfaceWaves w = new SurfaceWaves().prepare(STORM, 555);
        Sea beach = new Sea((x, z) -> x < 0 ? 0 : Math.min(16, x + 1));
        SurfaceMesher m = new SurfaceMesher();
        m.build(l, beach, w, params(false), SurfaceMesher.Visibility.ALL, new Quads());
        assertEquals(16, l.far().cell());
        for (int z = -64; z <= 64; z += 16) {
            assertEquals(BASE, m.ringCornerHeight(2, 0, z), 1e-12, "the corner on the shoreline stays still");
            assertEquals(BASE + w.height(16, z), m.ringCornerHeight(2, 16, z), 1e-9, "16 blocks out the sea is deep: full waves");
            assertEquals(BASE + w.height(32, z), m.ringCornerHeight(2, 32, z), 1e-9);
            assertEquals(BASE, m.ringCornerHeight(2, -16, z), 1e-12, "over land: still");
        }
        // a 4-block mid cell with a shallow corner: (2, 2) has depths 2 and 3 around it -> fade min(1/3, 2/3)
        SurfaceLod.Layout near = SurfaceLod.layout(0.5, 0.5, 8, 64, 16, 160);
        m.build(near, new Sea((x, z) -> x < 0 ? 0 : Math.min(16, x / 4 + 1)), w, params(false), SurfaceMesher.Visibility.ALL, new Quads());
        assertEquals(BASE + w.height(8, 40) * SurfaceRules.cornerFade(2, 3, 2, 3, 4), m.ringCornerHeight(1, 8, 40), 1e-9);
        assertEquals(SurfaceRules.depthFade(2, 4), SurfaceRules.cornerFade(2, 3, 2, 3, 4), 0.0);
    }

    /** At a beach the ring borders show no step: a coarse ring's vertex lies exactly on the finer ring's vertex there. */
    @Test
    void ringBordersMatchAtABeach() {
        // a diagonal coast, not aligned to any cell, shelving to full depth over 12 blocks
        Sea coast = new Sea((x, z) -> {
            int d = x + Math.floorDiv(z, 2);
            return d < 0 ? 0 : Math.min(16, 1 + d / 3);
        });
        for (double[] cam : new double[][] {{0.5, 0.5}, {-20.3, 31.9}, {40.2, -60.7}}) {
            SurfaceLod.Layout l = SurfaceLod.layout(cam[0], cam[1], 16, 64, 16, 160);
            SurfaceWaves w = new SurfaceWaves().prepare(STORM, 4242);
            SurfaceMesher m = new SurfaceMesher();
            m.build(l, coast, w, new SurfaceMesher.Params(BASE, 6, false, 0, 0), SurfaceMesher.Visibility.ALL, new Quads());
            int matched = border(m, l.near(), l.mid(), 0, 1) + border(m, l.mid(), l.far(), 1, 2);
            assertTrue(matched > 40, "border vertices checked: " + matched);
            // and the heights are the near rule's wherever two rings share a vertex
            SurfaceLod.Square q = l.mid().outer();
            for (int z = q.minZ(); z <= q.maxZ(); z += l.mid().cell()) {
                for (int x = q.minX(); x <= q.maxX(); x += l.mid().cell()) {
                    double f = SurfaceRules.cornerFade(coast.depth(x - 1, z - 1), coast.depth(x, z - 1), coast.depth(x - 1, z),
                            coast.depth(x, z), 6);
                    assertEquals(BASE + w.height(x, z) * f, m.ringCornerHeight(1, x, z), 1e-9);
                }
            }
        }
    }

    /** Compares the fine ring's outer boundary with the coarse ring's vertices on it; returns how many were compared. */
    private static int border(SurfaceMesher m, SurfaceLod.Ring fine, SurfaceLod.Ring coarse, int fi, int ci) {
        SurfaceLod.Square b = fine.outer();
        int big = coarse.cell(), count = 0;
        for (int t = b.minX(); t <= b.maxX(); t++) {
            for (int[] p : new int[][] {{t, b.minZ()}, {t, b.maxZ()}, {b.minX(), t - b.minX() + b.minZ()}, {b.maxX(), t - b.minX() + b.minZ()}}) {
                if (Math.floorMod(p[0], big) != 0 || Math.floorMod(p[1], big) != 0) {
                    continue;
                }
                double hf = m.ringCornerHeight(fi, p[0], p[1]), hc = m.ringCornerHeight(ci, p[0], p[1]);
                if (Double.isNaN(hf) || Double.isNaN(hc)) {
                    continue;
                }
                assertEquals(hf, hc, 1e-12, "step at the ring border at " + p[0] + ", " + p[1]);
                count++;
            }
        }
        return count;
    }
}
