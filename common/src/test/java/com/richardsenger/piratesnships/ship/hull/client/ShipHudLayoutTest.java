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
 * the units (HUD1, HUD2); only the chat lines vanilla draws count (HUD3), and only for the hull panel: the compass panel
 * stays at the margin whatever the chat shows (HUD4).
 */
class ShipHudLayoutTest {

    private static final int GW = 480, GH = 270;
    private static final int W = ShipHudLayout.W, CH = ShipHudLayout.COMPASS_H, HH = ShipHudLayout.HULL_H,
            M = ShipHudLayout.DEFAULT_MARGIN, GAP = ShipHudLayout.GAP;
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

    private static Placement place(Corner compass, Corner hull, Screen screen, double scale) {
        return ShipHudLayout.place(compass, hull, screen, scale, M);
    }

    /** The HUD of a survival player: chat (closed or open), hotbar with status rows, stamina bar on the right. */
    private static Screen survival(int gw, int gh, boolean chatOpen, int rowsAbove) {
        return survival(gw, gh, chatOpen ? CHAT_OPEN_H : CHAT_H, chatOpen, rowsAbove);
    }

    private static Screen survival(int gw, int gh, int chatLinesHeight, boolean chatOpen, int rowsAbove) {
        List<Rect> o = new ArrayList<>(ShipHudLayout.hotbar(gw, gh, true, rowsAbove));
        // StaminaHudLayout.ABOVE_HOTBAR_TIGHT in survival: right-aligned with the hotbar, 2 px above the food row
        o.add(new Rect(gw / 2 + 91 - 82, gh - 39 - 10 * rowsAbove - 2 - 7, 82, 7));
        return new Screen(gw, gh, o, ShipHudLayout.chat(gw, gh, CHAT_W, chatLinesHeight, chatOpen));
    }

    /** What the hull panel keeps clear of: the static obstacles and the chat. */
    private static List<Rect> withChat(Screen s) {
        List<Rect> all = new ArrayList<>(s.obstacles());
        all.addAll(s.chat());
        return all;
    }

    private static void assertClear(Rect panel, List<Rect> obstacles, Screen screen, String what) {
        for (Rect o : obstacles) {
            assertFalse(panel.intersects(o), what + " " + panel + " overlaps " + o);
        }
        assertTrue(panel.x() >= 0 && panel.y() >= 0 && panel.right() <= screen.guiWidth()
                && panel.bottom() <= screen.guiHeight(), what + " " + panel + " is off screen");
    }

    /**
     * Where the compass panel belongs at the bottom left of a survival HUD: above the status rows when it reaches into
     * their column, above the hotbar (with its offhand slot) when it reaches that far, the corner otherwise.
     */
    private static int compassY(int gw, int gh, int rowsAbove) {
        if (M + W > gw / 2 - 91) return gh - 39 - 10 * rowsAbove - GAP - CH;
        if (M + W > gw / 2 - 91 - 29) return gh - 23 - GAP - CH;
        return gh - M - CH;
    }

    @Test
    void eachCornerAtScaleOne() {
        Screen e = empty(GW, GH);
        for (Corner c : Corner.values()) {
            Corner other = c == Corner.TOP_LEFT ? Corner.BOTTOM_RIGHT : Corner.TOP_LEFT;
            Rect compass = place(c, other, e, 1.0).compass().rect();
            Rect hull = place(other, c, e, 1.0).hull().rect();
            int x = c.right() ? GW - M - W : M;
            assertEquals(new Rect(x, c.top() ? M : GH - M - CH, W, CH), compass, "compass at " + c);
            assertEquals(new Rect(x, c.top() ? M : GH - M - HH, W, HH), hull, "hull at " + c);
        }
    }

    @Test
    void configMarginMovesBothPanels() {
        assertEquals(8, ShipHudLayout.DEFAULT_MARGIN, "HUD4: a little spacing from the edges by default");
        for (int margin : new int[] {0, 4, ShipHudLayout.MAX_MARGIN}) {
            Placement p = ShipHudLayout.place(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, empty(GW, GH), 1.0, margin);
            assertEquals(new Rect(margin, GH - margin - CH, W, CH), p.compass().rect(), "compass at margin " + margin);
            assertEquals(new Rect(GW - margin - W, GH - margin - HH, W, HH), p.hull().rect(), "hull at margin " + margin);
            Placement top = ShipHudLayout.place(Corner.TOP_RIGHT, Corner.TOP_LEFT, empty(GW, GH), 1.0, margin);
            assertEquals(new Rect(GW - margin - W, margin, W, CH), top.compass().rect());
            assertEquals(new Rect(margin, margin, W, HH), top.hull().rect());
        }
        // the margin is from the edges only; past an obstacle it is the fixed gap
        Screen icons = new Screen(GW, GH, ShipHudLayout.effects(GW, 26));
        assertEquals(26 + GAP, ShipHudLayout.place(Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT, icons, 1.0, 20).compass().rect().y());
    }

    @Test
    void sameCornerStacksCompassOverHull() {
        Screen e = empty(GW, GH);
        for (Corner c : Corner.values()) {
            Placement p = place(c, c, e, 1.0);
            Rect compass = p.compass().rect(), hull = p.hull().rect();
            assertEquals(compass.x(), hull.x(), "one column at " + c);
            assertEquals(compass.bottom(), hull.y(), "the strip right under the compass at " + c);
            assertEquals(c.top() ? M : GH - M, c.top() ? compass.y() : hull.bottom(), "the block sits at " + c);
            assertEquals(CH + HH, hull.bottom() - compass.y(), "the HUD1 look, compass over strip");
        }
        // HUD4: stacked at the bottom left the block stays in the corner under the chat lines, like the compass
        Placement p = place(Corner.BOTTOM_LEFT, Corner.BOTTOM_LEFT, survival(GW, GH, true, 0), 1.0);
        assertEquals(GH - M, p.hull().rect().bottom());
        assertEquals(M, p.compass().rect().x());
    }

    @Test
    void scaleGrowsAwayFromTheCorner() {
        for (double scale : new double[] {0.5, 1.5, 2.0}) {
            int w = (int) Math.round(W * scale), ch = (int) Math.round(CH * scale), hh = (int) Math.round(HH * scale);
            Placement p = place(Corner.TOP_LEFT, Corner.BOTTOM_RIGHT, empty(GW, GH), scale);
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
        assertEquals(26 + GAP, place(Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT, icons, 1.0).compass().rect().y());
        Screen two = new Screen(GW, GH, ShipHudLayout.effects(GW, 52));
        assertEquals(M, place(Corner.TOP_LEFT, Corner.BOTTOM_RIGHT, two, 1.0).compass().rect().y(),
                "only the top right");
    }

    // ------------------------------------------------------------------ HUD4: the compass ignores the chat

    @Test
    void compassStaysAtTheMarginWhateverTheChatShows() {
        for (int[] gui : GUIS) {
            int gw = gui[0], gh = gui[1];
            for (int lines : new int[] {0, 27, CHAT_H}) {
                Screen s = survival(gw, gh, lines, false, 0);
                Placed compass = place(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, s, 1.0).compass();
                assertEquals(new Rect(M, compassY(gw, gh, 0), W, CH), compass.rect(),
                        "static at " + gw + "x" + gh + " with " + lines + " px of chat lines");
                assertTrue(compass.clear(), "the chat is no obstacle at " + gw + "x" + gh);
                assertClear(compass.rect(), s.obstacles(), s, "compass at " + gw + "x" + gh);
            }
        }
        // 1080p at GUI scale 3 (where HUD3's compass went to the top) and 1080p at scale 4: the corner, under the chat
        assertEquals(new Rect(M, 360 - M - CH, W, CH),
                place(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, survival(640, 360, CHAT_H, false, 0), 1.0).compass().rect());
        assertEquals(new Rect(M, 270 - M - CH, W, CH),
                place(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, survival(480, 270, CHAT_H, false, 0), 1.0).compass().rect());
        // the hotbar still counts: 427x240 (854x480 at scale 2) lifts it above the hotbar's offhand slot
        assertEquals(240 - 23 - GAP - CH,
                place(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, survival(427, 240, 0, false, 0), 1.0).compass().rect().y());
    }

    @Test
    void openChatLeavesTheCompassInPlaceAndShown() {
        for (int[] gui : GUIS) {
            int gw = gui[0], gh = gui[1];
            Placement closed = place(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, survival(gw, gh, false, 0), 1.0);
            Screen s = survival(gw, gh, true, 0);
            Placement open = place(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, s, 1.0);
            assertEquals(closed.compass(), open.compass(), "the compass does not move when the chat opens at " + gw + "x" + gh);
            assertTrue(open.compass().clear(), "and is never hidden");
            if (open.hull().clear()) assertClear(open.hull().rect(), withChat(s), s, "hull with the chat open at " + gw + "x" + gh);
            // where the open chat leaves no room it still keeps clear of the hotbar and the stamina bar (drawn under the chat)
            assertClear(open.hull().rect(), s.obstacles(), s, "hull with the chat open at " + gw + "x" + gh);
        }
    }

    @Test
    void hullPanelInTheChatCornerStillKeepsClearOfTheChat() {
        // the hull panel's HUD2/HUD3 behaviour at the bottom left: under the closed chat's lines (40 px up) it fits the
        // corner; the open chat's input line lifts it, still under the open chat's lines
        Placed closed = place(Corner.TOP_RIGHT, Corner.BOTTOM_LEFT, survival(640, 360, 27, false, 0), 1.0).hull();
        assertEquals(new Rect(M, 360 - M - HH, W, HH), closed.rect(), "in the corner under the chat lines");
        Screen open = survival(640, 360, true, 0);
        Placed hull = place(Corner.TOP_RIGHT, Corner.BOTTOM_LEFT, open, 1.0).hull();
        assertTrue(hull.clear());
        assertEquals(360 - ShipHudLayout.CHAT_INPUT_H - GAP - HH, hull.rect().y(), "just above the input line");
        assertClear(hull.rect(), withChat(open), open, "hull with the chat open");
        // a block of 60 px of chat lines down to the bottom lifts it over them
        Screen tall = new Screen(640, 360, List.of(), List.of(new Rect(0, 300, 332, 60)));
        assertEquals(300 - GAP - HH, place(Corner.TOP_RIGHT, Corner.BOTTOM_LEFT, tall, 1.0).hull().rect().y());
    }

    // ------------------------------------------------------------------ HUD3: only the chat lines vanilla draws

    /** Vanilla's line height at the default line spacing 0, and the default page (180 / 9). */
    private static final int LINE = 9, PAGE = 20;

    @Test
    void chatRowsCountWhatVanillaDraws() {
        assertEquals(0, ShipHudLayout.chatRows(new int[] {200, 450, 9000, 12000}, PAGE, false), "faded lines are not drawn");
        assertEquals(0, ShipHudLayout.chatHeight(0, LINE, 1.0));
        assertEquals(0, ShipHudLayout.chatRows(new int[0], PAGE, false), "nothing received at all");
        int rows = ShipHudLayout.chatRows(new int[] {5, 40, 120, 300, 900}, PAGE, false);
        assertEquals(3, rows);
        assertEquals(27, ShipHudLayout.chatHeight(rows, LINE, 1.0));
        // at chat scale 0.5 the lines take half the height (rounded up)
        assertEquals(14, ShipHudLayout.chatHeight(rows, LINE, 0.5));
        // line spacing 1 (vanilla's getLineHeight = 9 × 2)
        assertEquals(54, ShipHudLayout.chatHeight(rows, 18, 1.0));
    }

    @Test
    void openChatCountsOnlyTheExistingLines() {
        assertEquals(2, ShipHudLayout.chatRows(new int[] {5000, 9000}, PAGE, true),
                "the open chat draws its two lines, however old, not a whole page");
        int[] many = new int[100];
        assertEquals(PAGE, ShipHudLayout.chatRows(many, PAGE, true));
        assertEquals(PAGE, ShipHudLayout.chatRows(many, PAGE, false), "and so is a closed chat full of new lines");
    }

    @Test
    void chatLinesFadeAfterTwoHundredTicks() {
        assertEquals(1, ShipHudLayout.chatRows(new int[] {199}, PAGE, false), "age 199 is still drawn");
        assertEquals(0, ShipHudLayout.chatRows(new int[] {200}, PAGE, false), "age 200 is not");
        assertEquals(1, ShipHudLayout.chatRows(new int[] {199, 200}, PAGE, false));
        assertEquals(0, ShipHudLayout.chatRows(new int[] {5}, 0, false), "no page, no lines");
    }

    @Test
    void hiddenChatStaysOutOfTheWay() {
        // ShipHud passes width and height 0 when chat visibility is HIDDEN: no line obstacle, open or closed
        assertEquals(List.of(), ShipHudLayout.chat(640, 360, 0, 0, false));
        assertEquals(List.of(new Rect(0, 360 - ShipHudLayout.CHAT_INPUT_H, 640, ShipHudLayout.CHAT_INPUT_H)),
                ShipHudLayout.chat(640, 360, 0, 0, true), "only the open chat's input box");
    }

    @Test
    void hullKeepsClearOfHotbarAndStaminaBar() {
        for (int[] gui : GUIS) {
            int gw = gui[0], gh = gui[1];
            for (int rows = 0; rows <= 2; rows++) {
                Screen s = survival(gw, gh, false, rows);
                Placed hull = place(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, s, 1.0).hull();
                assertTrue(hull.clear(), "hull not clear at " + gw + "x" + gh);
                assertClear(hull.rect(), withChat(s), s, "hull at " + gw + "x" + gh + " with " + rows + " rows");
                assertEquals(gw - M, hull.rect().right(), "the hull panel keeps to the right edge");
                if (hull.rect().x() >= gw / 2 + 91 + 29) {
                    assertEquals(gh - M, hull.rect().bottom(), "in the corner when the screen is wide enough at " + gw);
                }
            }
        }
        // 854x480 at GUI scale 2 (427x240): the panel overlaps the hotbar's column and rises above the stamina bar
        Rect hull = place(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, survival(427, 240, false, 0), 1.0).hull().rect();
        assertTrue(hull.bottom() <= 240 - 48 - GAP, "above the stamina bar: " + hull);
        // creative (no status rows): above the hotbar alone, and the stamina bar 2 px above it
        List<Rect> creative = new ArrayList<>(ShipHudLayout.hotbar(300, 200, false, 0));
        creative.add(new Rect(150 - 41, 200 - 22 - 2 - 7, 82, 7));
        Rect c = place(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, new Screen(300, 200, creative), 1.0).hull().rect();
        assertEquals(200 - 31 - GAP, c.bottom(), "clear of the creative stamina bar: " + c);
    }

    @Test
    void everyGuiScaleAtEveryHudScale() {
        for (int[] gui : GUIS) {
            for (double scale : new double[] {0.5, 1.0, 1.5}) {
                Screen s = survival(gui[0], gui[1], false, 0);
                Placement p = place(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, s, scale);
                for (Placed panel : List.of(p.compass(), p.hull())) {
                    Rect r = panel.rect();
                    assertTrue(r.x() >= 0 && r.y() >= 0 && r.right() <= gui[0] && r.bottom() <= gui[1],
                            r + " off screen at " + gui[0] + "x" + gui[1] + " scale " + scale);
                }
                if (p.compass().clear()) assertClear(p.compass().rect(), s.obstacles(), s, "compass at scale " + scale);
                if (p.hull().clear()) assertClear(p.hull().rect(), withChat(s), s, "hull at scale " + scale);
                assertFalse(p.compass().rect().intersects(p.hull().rect()), "the panels overlap");
            }
        }
    }

    @Test
    void panelsOnOneSideKeepApart() {
        // a small screen: the hull panel at the top left keeps clear of the compass panel at the bottom left
        Placement p = place(Corner.BOTTOM_LEFT, Corner.TOP_LEFT, empty(200, 120), 1.0);
        assertFalse(p.compass().rect().intersects(p.hull().rect()));
    }

    @Test
    void blockLargerThanTheScreenStaysOnIt() {
        Placement p = place(Corner.BOTTOM_RIGHT, Corner.BOTTOM_RIGHT, empty(200, 150), 3.0);
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
        assertEquals(ShipHudLayout.SPEED_Y + 10, ShipHudLayout.COMPASS_H,
                "HUD4: the speed line (with its backing) is the panel's last row, no empty band under it");
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
