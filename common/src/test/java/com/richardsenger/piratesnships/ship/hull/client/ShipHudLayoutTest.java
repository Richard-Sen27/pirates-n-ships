package com.richardsenger.piratesnships.ship.hull.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.ship.hull.client.ShipHudLayout.Corner;
import com.richardsenger.piratesnships.ship.hull.client.ShipHudLayout.Placed;
import com.richardsenger.piratesnships.ship.hull.client.ShipHudLayout.Placement;
import com.richardsenger.piratesnships.ship.hull.client.ShipHudLayout.Rect;
import com.richardsenger.piratesnships.ship.hull.client.ShipHudLayout.Screen;
import com.richardsenger.piratesnships.ship.hull.client.ShipHudLayout.Span;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Placement of the ship HUD's two panels per corner, GUI size and scale, clear of vanilla's HUD; the strip's cells and
 * the units (HUD1, HUD2).
 */
class ShipHudLayoutTest {

    private static final int GW = 480, GH = 270;
    private static final int W = ShipHudLayout.W, CH = ShipHudLayout.COMPASS_H, HH = ShipHudLayout.HULL_H,
            M = ShipHudLayout.MARGIN;
    /** Vanilla's default chat: 320 wide (+ backing), 90 high closed, 180 open, chat scale 1. */
    private static final int CHAT_W = 332, CHAT_H = 90, CHAT_OPEN_H = 180;
    /** GUI sizes of 1920×1080, 1280×720 and 854×480 windows at GUI scale 1 to 4 (as far as each allows). */
    private static final int[][] GUIS = {
            {1920, 1080}, {960, 540}, {640, 360}, {480, 270},
            {1280, 720}, {427, 240},
            {854, 480}};

    private static Screen empty(int gw, int gh) {
        return new Screen(gw, gh, List.of());
    }

    /** The HUD of a survival player: chat (closed or open), hotbar with status rows, stamina bar on the right. */
    private static Screen survival(int gw, int gh, boolean chatOpen, int rowsAbove) {
        List<Rect> o = new ArrayList<>(ShipHudLayout.chat(gw, gh, CHAT_W, chatOpen ? CHAT_OPEN_H : CHAT_H, chatOpen));
        o.addAll(ShipHudLayout.hotbar(gw, gh, true, rowsAbove));
        // StaminaHudLayout.ABOVE_HOTBAR_TIGHT in survival: right-aligned with the hotbar, 2 px above the food row
        o.add(new Rect(gw / 2 + 91 - 82, gh - 39 - 10 * rowsAbove - 2 - 7, 82, 7));
        return new Screen(gw, gh, o);
    }

    private static void assertClear(Rect panel, Screen screen, String what) {
        for (Rect o : screen.obstacles()) {
            assertFalse(panel.intersects(o), what + " " + panel + " overlaps " + o);
        }
        assertTrue(panel.x() >= 0 && panel.y() >= 0 && panel.right() <= screen.guiWidth()
                && panel.bottom() <= screen.guiHeight(), what + " " + panel + " is off screen");
    }

    @Test
    void eachCornerAtScaleOne() {
        Screen e = empty(GW, GH);
        for (Corner c : Corner.values()) {
            Corner other = c == Corner.TOP_LEFT ? Corner.BOTTOM_RIGHT : Corner.TOP_LEFT;
            Rect compass = ShipHudLayout.place(c, other, e, 1.0).compass().rect();
            Rect hull = ShipHudLayout.place(other, c, e, 1.0).hull().rect();
            int x = c.right() ? GW - M - W : M;
            assertEquals(new Rect(x, c.top() ? M : GH - M - CH, W, CH), compass, "compass at " + c);
            assertEquals(new Rect(x, c.top() ? M : GH - M - HH, W, HH), hull, "hull at " + c);
        }
    }

    @Test
    void sameCornerStacksCompassOverHull() {
        Screen e = empty(GW, GH);
        for (Corner c : Corner.values()) {
            Placement p = ShipHudLayout.place(c, c, e, 1.0);
            Rect compass = p.compass().rect(), hull = p.hull().rect();
            assertEquals(compass.x(), hull.x(), "one column at " + c);
            assertEquals(compass.bottom(), hull.y(), "the strip right under the compass at " + c);
            assertEquals(c.top() ? M : GH - M, c.top() ? compass.y() : hull.bottom(), "the block sits at " + c);
            assertEquals(CH + HH, hull.bottom() - compass.y(), "the HUD1 look, 105 px high");
        }
    }

    @Test
    void scaleGrowsAwayFromTheCorner() {
        for (double scale : new double[] {0.5, 1.5, 2.0}) {
            int w = (int) Math.round(W * scale), ch = (int) Math.round(CH * scale), hh = (int) Math.round(HH * scale);
            Placement p = ShipHudLayout.place(Corner.TOP_LEFT, Corner.BOTTOM_RIGHT, empty(GW, GH), scale);
            Rect tl = p.compass().rect(), br = p.hull().rect();
            assertEquals(new Rect(M, M, w, ch), tl, "top left stays at the corner");
            assertEquals(new Rect(GW - M - w, GH - M - hh, w, hh), br, "bottom right keeps its edges");
            assertEquals(scale, ShipHudLayout.factor(tl), 0.01);
            assertEquals(scale, ShipHudLayout.factor(br), 0.01);
        }
    }

    @Test
    void topRightMovesBelowTheEffectIcons() {
        Screen icons = new Screen(GW, GH, ShipHudLayout.effects(GW, 26));
        assertEquals(26 + M, ShipHudLayout.place(Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT, icons, 1.0).compass().rect().y());
        Screen two = new Screen(GW, GH, ShipHudLayout.effects(GW, 52));
        assertEquals(M, ShipHudLayout.place(Corner.TOP_LEFT, Corner.BOTTOM_RIGHT, two, 1.0).compass().rect().y(),
                "only the top right");
    }

    @Test
    void compassSitsAboveTheChat() {
        for (int[] gui : GUIS) {
            int gw = gui[0], gh = gui[1];
            Screen s = survival(gw, gh, false, 0);
            Placed compass = ShipHudLayout.place(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, s, 1.0).compass();
            assertTrue(compass.clear(), "compass not clear at " + gw + "x" + gh);
            assertClear(compass.rect(), s, "compass at " + gw + "x" + gh);
            assertEquals(gh - ShipHudLayout.CHAT_BOTTOM - CHAT_H - M - CH, compass.rect().y(),
                    "right above the chat's top line at " + gw + "x" + gh);
            assertEquals(M, compass.rect().x());
        }
        // chat hidden (no lines): the compass goes down to the corner
        Screen noChat = new Screen(GW, GH, ShipHudLayout.chat(GW, GH, 0, 0, false));
        assertEquals(GH - M - CH, ShipHudLayout.place(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, noChat, 1.0).compass().rect().y());
    }

    @Test
    void openChatLiftsTheCompassOrHidesIt() {
        for (int[] gui : GUIS) {
            int gw = gui[0], gh = gui[1];
            Screen s = survival(gw, gh, true, 0);
            Placement p = ShipHudLayout.place(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, s, 1.0);
            boolean fits = gh - ShipHudLayout.CHAT_BOTTOM - CHAT_OPEN_H - M - CH >= 0;
            assertEquals(fits, p.compass().clear(), "compass above the open chat at " + gw + "x" + gh);
            if (fits) assertClear(p.compass().rect(), s, "compass with the chat open at " + gw + "x" + gh);
            if (p.hull().clear()) assertClear(p.hull().rect(), s, "hull with the chat open at " + gw + "x" + gh);
        }
        // 640x360 (1080p at GUI scale 3) fits the compass above the open chat, 480x270 (scale 4) does not
        assertTrue(ShipHudLayout.place(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, survival(640, 360, true, 0), 1.0)
                .compass().clear());
        assertFalse(ShipHudLayout.place(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, survival(480, 270, true, 0), 1.0)
                .compass().clear());
    }

    @Test
    void hullKeepsClearOfHotbarAndStaminaBar() {
        for (int[] gui : GUIS) {
            int gw = gui[0], gh = gui[1];
            for (int rows = 0; rows <= 2; rows++) {
                Screen s = survival(gw, gh, false, rows);
                Placed hull = ShipHudLayout.place(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, s, 1.0).hull();
                assertTrue(hull.clear(), "hull not clear at " + gw + "x" + gh);
                assertClear(hull.rect(), s, "hull at " + gw + "x" + gh + " with " + rows + " rows");
                assertEquals(gw - M, hull.rect().right(), "the hull panel keeps to the right edge");
                if (hull.rect().x() >= gw / 2 + 91 + 29) {
                    assertEquals(gh - M, hull.rect().bottom(), "in the corner when the screen is wide enough at " + gw);
                }
            }
        }
        // 854x480 at GUI scale 2 (427x240): the panel overlaps the hotbar's column and rises above the stamina bar
        Rect hull = ShipHudLayout.place(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, survival(427, 240, false, 0), 1.0)
                .hull().rect();
        assertTrue(hull.bottom() <= 240 - 48 - M, "above the stamina bar: " + hull);
        // creative (no status rows): above the hotbar alone, and the stamina bar 2 px above it
        List<Rect> creative = new ArrayList<>(ShipHudLayout.hotbar(300, 200, false, 0));
        creative.add(new Rect(150 - 41, 200 - 22 - 2 - 7, 82, 7));
        Rect c = ShipHudLayout.place(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, new Screen(300, 200, creative), 1.0)
                .hull().rect();
        assertEquals(200 - 31 - M, c.bottom(), "clear of the creative stamina bar: " + c);
    }

    @Test
    void everyGuiScaleAtEveryHudScale() {
        for (int[] gui : GUIS) {
            for (double scale : new double[] {0.5, 1.0, 1.5}) {
                Screen s = survival(gui[0], gui[1], false, 0);
                Placement p = ShipHudLayout.place(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, s, scale);
                for (Placed panel : List.of(p.compass(), p.hull())) {
                    Rect r = panel.rect();
                    assertTrue(r.x() >= 0 && r.y() >= 0 && r.right() <= gui[0] && r.bottom() <= gui[1],
                            r + " off screen at " + gui[0] + "x" + gui[1] + " scale " + scale);
                    if (panel.clear()) assertClear(r, s, "panel at scale " + scale);
                }
                assertFalse(p.compass().rect().intersects(p.hull().rect()), "the panels overlap");
            }
        }
    }

    @Test
    void panelsOnOneSideKeepApart() {
        // a small screen: the hull panel at the top left keeps clear of the compass panel at the bottom left
        Placement p = ShipHudLayout.place(Corner.BOTTOM_LEFT, Corner.TOP_LEFT, empty(200, 120), 1.0);
        assertFalse(p.compass().rect().intersects(p.hull().rect()));
    }

    @Test
    void blockLargerThanTheScreenStaysOnIt() {
        Placement p = ShipHudLayout.place(Corner.BOTTOM_RIGHT, Corner.BOTTOM_RIGHT, empty(200, 150), 3.0);
        assertEquals(0, p.compass().rect().x());
        assertEquals(0, p.compass().rect().y());
        assertFalse(p.compass().clear());
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
    void partsFitThePanels() {
        assertTrue(ShipHudLayout.ROSE_CY + ShipHudLayout.ARROW_TIP_R + ShipHudLayout.ARROW_MAX <= ShipHudLayout.SPEED_Y + 2,
                "the wind arrow stays clear of the speed line");
        assertTrue(ShipHudLayout.ROSE_CY - ShipHudLayout.ARROW_TIP_R - ShipHudLayout.ARROW_MAX >= -2, "and of the top");
        assertTrue(ShipHudLayout.NAME_Y + 9 <= ShipHudLayout.COMPASS_H, "the name fits the compass panel");
        assertTrue(ShipHudLayout.STRIP_Y >= 2, "the breach tick (2 px above the strip) fits the hull panel");
        assertTrue(ShipHudLayout.STRIP_Y + ShipHudLayout.STRIP_H <= ShipHudLayout.HULL_H, "the strip fits the hull panel");
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
