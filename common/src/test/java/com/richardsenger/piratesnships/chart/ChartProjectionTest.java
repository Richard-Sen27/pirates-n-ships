package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.chart.render.ChartProjection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The chart screen's projection (MAP1): cells to screen and back at each zoom, panning, zooming, clamping. */
class ChartProjectionTest {

    private static final int L = 10;
    private static final int T = 20;
    private static final int W = 300;
    private static final int H = 200;
    private static final double EPS = 1e-9;

    @Test
    void cellsMapToScreenAtEveryZoom() {
        for (int zoom = 0; zoom < ChartProjection.PIXELS_PER_CELL.length; zoom++) {
            ChartProjection p = new ChartProjection(100, -40, zoom, 4);
            int px = ChartProjection.PIXELS_PER_CELL[zoom];
            assertEquals(4.0 / px, p.blocksPerPixel(), EPS);
            // the centre block lands in the middle of the view
            assertEquals(L + W / 2.0, p.screenX(100, L, W), EPS);
            assertEquals(T + H / 2.0, p.screenY(-40, T, H), EPS);
            // one cell to the east is px pixels to the right, one cell south px pixels down
            assertEquals(px, p.cellScreenX(26, L, W) - p.cellScreenX(25, L, W), EPS);
            assertEquals(px, p.cellScreenY(-9, T, H) - p.cellScreenY(-10, T, H), EPS);
            // and back: the cell under a screen point
            for (int cx = 20; cx < 30; cx++) {
                double sx = p.cellScreenX(cx, L, W) + px / 2.0;
                assertEquals(cx, p.cellX(sx, L, W), "zoom " + zoom + " cell " + cx);
            }
            for (int cz = -15; cz < -5; cz++) {
                double sy = p.cellScreenY(cz, T, H) + px / 2.0;
                assertEquals(cz, p.cellZ(sy, T, H), "zoom " + zoom + " cell z " + cz);
            }
            assertEquals(123.25, p.blockX(p.screenX(123.25, L, W), L, W), EPS);
        }
    }

    @Test
    void negativeCellsRoundDown() {
        ChartProjection p = new ChartProjection(0, 0, 2, 4);
        assertEquals(-1, p.cellX(L + W / 2.0 - 0.5, L, W));
        assertEquals(0, p.cellX(L + W / 2.0, L, W));
    }

    @Test
    void zoomIsClamped() {
        assertEquals(0, new ChartProjection(0, 0, -3, 4).zoom());
        assertEquals(ChartProjection.PIXELS_PER_CELL.length - 1, new ChartProjection(0, 0, 99, 4).zoom());
    }

    @Test
    void panFollowsTheMouse() {
        ChartProjection p = new ChartProjection(0, 0, 0, 4);
        double before = p.screenX(500, L, W);
        ChartProjection moved = p.pan(12, -7);
        assertEquals(before + 12, moved.screenX(500, L, W), EPS, "the map moves with the drag");
        assertEquals(p.screenY(500, T, H) - 7, moved.screenY(500, T, H), EPS);
    }

    @Test
    void zoomKeepsTheBlockUnderTheCursor() {
        ChartProjection p = new ChartProjection(50, 50, 0, 4);
        double sx = L + 37;
        double sy = T + 151;
        double bx = p.blockX(sx, L, W);
        double bz = p.blockZ(sy, T, H);
        for (int z = 0; z < 3; z++) {
            ChartProjection q = p.zoomed(z, sx, sy, L, T, W, H);
            assertEquals(bx, q.blockX(sx, L, W), 1e-6);
            assertEquals(bz, q.blockZ(sy, T, H), 1e-6);
        }
    }

    @Test
    void panIsClampedToTheChartedAreaWithAMargin() {
        ChartProjection.Bounds known = new ChartProjection.Bounds(-256, 0, 256, 512);
        ChartProjection far = new ChartProjection(10_000, -10_000, 1, 4);
        ChartProjection c = far.clamped(known, 100);
        assertEquals(356, c.centerX(), EPS);
        assertEquals(-100, c.centerZ(), EPS);
        ChartProjection inside = new ChartProjection(0, 100, 1, 4);
        assertSame(inside, inside.clamped(known, 100));
        assertSame(far, far.clamped(null, 100), "nothing charted: no clamping");
    }
}
