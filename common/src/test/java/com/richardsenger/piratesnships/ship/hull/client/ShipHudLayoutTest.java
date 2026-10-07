package com.richardsenger.piratesnships.ship.hull.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.ship.hull.client.ShipHudLayout.Corner;
import com.richardsenger.piratesnships.ship.hull.client.ShipHudLayout.Rect;
import com.richardsenger.piratesnships.ship.hull.client.ShipHudLayout.Span;
import org.junit.jupiter.api.Test;

/** Placement of the ship HUD per corner and scale, the strip's cells and the units (HUD1). */
class ShipHudLayoutTest {

    private static final int GW = 480, GH = 270;

    @Test
    void eachCornerAtScaleOne() {
        int w = ShipHudLayout.W, h = ShipHudLayout.H, m = ShipHudLayout.MARGIN;
        assertEquals(new Rect(m, m, w, h), ShipHudLayout.place(Corner.TOP_LEFT, GW, GH, 1.0, 0));
        assertEquals(new Rect(GW - m - w, m, w, h), ShipHudLayout.place(Corner.TOP_RIGHT, GW, GH, 1.0, 0));
        assertEquals(new Rect(m, GH - m - h, w, h), ShipHudLayout.place(Corner.BOTTOM_LEFT, GW, GH, 1.0, 0));
        assertEquals(new Rect(GW - m - w, GH - m - h, w, h), ShipHudLayout.place(Corner.BOTTOM_RIGHT, GW, GH, 1.0, 0));
    }

    @Test
    void scaleGrowsAwayFromTheCorner() {
        for (double scale : new double[] {0.5, 1.5, 2.0}) {
            int w = (int) Math.round(ShipHudLayout.W * scale), h = (int) Math.round(ShipHudLayout.H * scale);
            Rect tl = ShipHudLayout.place(Corner.TOP_LEFT, GW, GH, scale, 0);
            Rect br = ShipHudLayout.place(Corner.BOTTOM_RIGHT, GW, GH, scale, 0);
            assertEquals(w, tl.w());
            assertEquals(h, tl.h());
            assertEquals(ShipHudLayout.MARGIN, tl.x(), "top left stays at the corner");
            assertEquals(ShipHudLayout.MARGIN, tl.y());
            assertEquals(GW - ShipHudLayout.MARGIN, br.x() + br.w(), "bottom right keeps its right edge");
            assertEquals(GH - ShipHudLayout.MARGIN, br.y() + br.h(), "and its bottom edge");
            assertEquals(scale, ShipHudLayout.factor(tl), 0.01);
        }
    }

    @Test
    void topRightMovesBelowTheEffectIcons() {
        assertEquals(ShipHudLayout.MARGIN + 26, ShipHudLayout.place(Corner.TOP_RIGHT, GW, GH, 1.0, 26).y());
        assertEquals(ShipHudLayout.MARGIN, ShipHudLayout.place(Corner.TOP_LEFT, GW, GH, 1.0, 52).y(), "only the top right");
    }

    @Test
    void panelLargerThanTheScreenStaysOnIt() {
        Rect r = ShipHudLayout.place(Corner.BOTTOM_RIGHT, 200, 150, 3.0, 0);
        assertEquals(0, r.x());
        assertEquals(0, r.y());
    }

    @Test
    void cellsShareTheStripByVolume() {
        int width = ShipHudLayout.STRIP_W;
        Span[] one = ShipHudLayout.cells(new int[] {18}, width);
        assertEquals(new Span(0, width), one[0]);
        Span[] cells = ShipHudLayout.cells(new int[] {10, 30, 10}, width);
        int total = 0;
        for (Span s : cells) total += s.w();
        assertEquals(width - 2 * ShipHudLayout.CELL_GAP, total, "the cells and gaps fill the strip exactly");
        assertTrue(cells[1].w() > cells[0].w(), "the large compartment gets the wide cell");
        assertEquals(cells[0].x() + cells[0].w() + ShipHudLayout.CELL_GAP, cells[1].x());
        Span[] sixteen = ShipHudLayout.cells(new int[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 500}, width);
        for (Span s : sixteen) assertTrue(s.w() >= ShipHudLayout.CELL_MIN_W, "every cell stays visible");
        Span last = sixteen[15];
        assertEquals(width, last.x() + last.w());
        assertEquals(0, ShipHudLayout.cells(new int[0], width).length);
    }

    @Test
    void waterAndWindPixels() {
        assertEquals(0, ShipHudLayout.waterPixels(0f, 12));
        assertEquals(1, ShipHudLayout.waterPixels(0.01f, 12), "any water shows");
        assertEquals(6, ShipHudLayout.waterPixels(0.5f, 12));
        assertEquals(12, ShipHudLayout.waterPixels(1.2f, 12));
        assertEquals(0, ShipHudLayout.arrowLength(0));
        assertEquals(ShipHudLayout.ARROW_MIN, ShipHudLayout.arrowLength(0.1), 1);
        assertEquals(ShipHudLayout.ARROW_MAX, ShipHudLayout.arrowLength(ShipHudLayout.ARROW_FULL_WIND));
        assertEquals(ShipHudLayout.ARROW_MAX, ShipHudLayout.arrowLength(50));
        assertTrue(ShipHudLayout.arrowLength(5) < ShipHudLayout.arrowLength(15));
    }

    @Test
    void partsFitThePanel() {
        assertTrue(ShipHudLayout.ROSE_CY + ShipHudLayout.ARROW_TIP_R + ShipHudLayout.ARROW_MAX <= ShipHudLayout.SPEED_Y + 2,
                "the wind arrow stays clear of the speed line");
        assertTrue(ShipHudLayout.ROSE_CY - ShipHudLayout.ARROW_TIP_R - ShipHudLayout.ARROW_MAX >= -2, "and of the top");
        assertTrue(ShipHudLayout.STRIP_Y + ShipHudLayout.STRIP_H <= ShipHudLayout.H);
        assertTrue(ShipHudLayout.NAME_Y + 9 <= ShipHudLayout.STRIP_Y - 1);
    }

    @Test
    void knots() {
        assertEquals(1.943844, ShipHudText.knots(1.0), 1e-5);
        assertEquals(10.0, ShipHudText.knots(ShipHudText.KNOT * 10), 1e-9);
        assertEquals("8.2", ShipHudText.speedNumber(ShipHudConfig.SpeedUnit.KNOTS, 4.2));
        assertEquals("4.2", ShipHudText.speedNumber(ShipHudConfig.SpeedUnit.BLOCKS, 4.2));
        assertEquals("0.0", ShipHudText.speedNumber(ShipHudConfig.SpeedUnit.KNOTS, -0.01));
        assertEquals(12, ShipHudText.rudderDegrees(-11.6));
        assertEquals(0, ShipHudText.rudderDegrees(0.4));
    }
}
