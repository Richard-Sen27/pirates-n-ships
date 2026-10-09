package com.richardsenger.piratesnships.hazards.waves.client.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SurfaceLodTest {

    @Test
    void farCellIsAPowerOfTwoFrom4To64() {
        assertEquals(16, SurfaceLod.farCell(16));
        assertEquals(16, SurfaceLod.farCell(20));
        assertEquals(4, SurfaceLod.farCell(1));
        assertEquals(64, SurfaceLod.farCell(500));
        assertEquals(8, SurfaceLod.farCell(12));
    }

    @Test
    void defaultsAt12Chunks() {
        SurfaceLod.Layout l = SurfaceLod.layout(0.5, 0.5, 32, 128, 16, 192);
        assertEquals(new SurfaceLod.Square(-32, -32, 32, 32), l.near().outer());
        assertEquals(new SurfaceLod.Square(-128, -128, 128, 128), l.mid().outer());
        assertNotNull(l.far());
        assertEquals(new SurfaceLod.Square(-192, -192, 192, 192), l.far().outer());
        assertEquals(64 * 64, l.near().cellCount());
        assertEquals(64 * 64 - 16 * 16, l.mid().cellCount());
        assertEquals(24 * 24 - 16 * 16, l.far().cellCount());
    }

    @Test
    void noFarRingWhenTheMidRingReachesTheRenderDistance() {
        SurfaceLod.Layout l = SurfaceLod.layout(10, 10, 32, 128, 16, 96);
        assertNull(l.far());
        assertEquals(96, l.mid().outer().maxX() - 0, "the mid ring is clipped to the render distance");
    }

    /** The rings tile the outermost square exactly once, for many cameras and settings, and stay aligned. */
    @Test
    void ringsTileTheAreaWithoutGapsOrOverlap() {
        int[][] settings = {{32, 128, 16, 192}, {8, 32, 4, 64}, {96, 512, 64, 512}, {33, 100, 12, 130}, {32, 128, 32, 512},
                {8, 512, 16, 64}, {64, 64, 8, 200}};
        double[][] cams = {{0.5, 0.5}, {-0.01, 17.9}, {1234.7, -987.3}, {-15.99, -16.01}, {31.0, 63.99}, {1e6 + 0.3, -2e6 + 0.7}};
        for (int[] s : settings) {
            for (double[] c : cams) {
                SurfaceLod.Layout l = SurfaceLod.layout(c[0], c[1], s[0], s[1], s[2], s[3]);
                SurfaceLod.Square outer = l.far() != null ? l.far().outer() : l.mid().outer();
                int size = outer.size();
                int[] cover = new int[size * size];
                for (SurfaceLod.Ring r : l.far() == null ? new SurfaceLod.Ring[] {l.near(), l.mid()}
                        : new SurfaceLod.Ring[] {l.near(), l.mid(), l.far()}) {
                    assertEquals(0, Math.floorMod(r.outer().minX(), r.cell()), "outer aligned to its cell");
                    assertEquals(0, r.outer().size() % r.cell());
                    if (r.hole() != null) {
                        assertEquals(0, Math.floorMod(r.hole().minX(), r.cell()), "hole aligned to the coarse cell");
                        assertEquals(0, Math.floorMod(r.hole().minZ(), r.cell()));
                        assertEquals(0, r.hole().size() % r.cell());
                    }
                    int n = 0;
                    for (int z = r.outer().minZ(); z < r.outer().maxZ(); z += r.cell()) {
                        for (int x = r.outer().minX(); x < r.outer().maxX(); x += r.cell()) {
                            if (!r.hasCell(x, z)) {
                                continue;
                            }
                            n++;
                            for (int dz = 0; dz < r.cell(); dz++) {
                                for (int dx = 0; dx < r.cell(); dx++) {
                                    cover[(z + dz - outer.minZ()) * size + (x + dx - outer.minX())]++;
                                }
                            }
                        }
                    }
                    assertEquals(r.cellCount(), n);
                }
                for (int i = 0; i < cover.length; i++) {
                    assertEquals(1, cover[i], "column covered once");
                }
                // the near square holds the camera
                assertTrue(l.near().outer().contains((int) Math.floor(c[0]), (int) Math.floor(c[1])));
                assertTrue(l.near().outer().size() >= 2 * s[0]);
            }
        }
    }

    /** Vertex budget per setting (printed for the hand-back), and it never exceeds the estimate's simple formula. */
    @Test
    void vertexEstimate() {
        int[] distances = {8, 12, 16, 32};
        int[][] settings = {{32, 128, 16}, {16, 64, 16}, {48, 192, 32}};
        for (int[] s : settings) {
            for (int rd : distances) {
                SurfaceLod.Layout l = SurfaceLod.layout(0, 0, s[0], s[1], s[2], rd * 16);
                long top = SurfaceLod.maxVertices(l, false), both = SurfaceLod.maxVertices(l, true);
                System.out.printf("[SurfaceLod] near %d, mid %d, far cell %d, render distance %d: %d cells, %d seams, "
                                + "%d vertices (top only) / %d (with underside), %.1f / %.1f MB%n",
                        s[0], s[1], s[2], rd, l.cellCount(), l.seamCount(), top, both, top * 28 / 1e6, both * 28 / 1e6);
                assertTrue(both > top);
                assertEquals(4L * (l.cellCount() + 2L * l.seamCount()), top);
            }
        }
        // the default at 32 chunks stays under 100k vertices with the underside
        assertTrue(SurfaceLod.maxVertices(SurfaceLod.layout(0, 0, 32, 128, 16, 512), true) < 100_000);
    }
}
