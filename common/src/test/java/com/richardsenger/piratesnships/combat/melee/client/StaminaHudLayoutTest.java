package com.richardsenger.piratesnships.combat.melee.client;

import com.richardsenger.piratesnships.combat.melee.client.StaminaHudLayout.Hud;
import com.richardsenger.piratesnships.combat.melee.client.StaminaHudLayout.Position;
import com.richardsenger.piratesnships.combat.melee.client.StaminaHudLayout.Rect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaminaHudLayoutTest {

    private static boolean overlaps(Rect r, int x0, int y0, int x1, int y1) {
        return r.x() < x1 && x0 < r.x() + r.w() && r.y() < y1 && y0 < r.y() + r.h();
    }

    @ParameterizedTest
    @CsvSource({"320,240", "427,240", "640,360", "960,540"})
    void tightInCreativeSitsTwoPixelsAboveTheHotbarCentred(int w, int h) {
        Rect r = StaminaHudLayout.place(Position.ABOVE_HOTBAR_TIGHT, new Hud(w, h, false, 0), 1.0, 0, 0);
        assertEquals(new Rect(w / 2 - 41, h - 22 - 2 - 7, 82, 7, false), r);
        assertEquals(h - 24, r.y() + r.h(), "2 px gap to the hotbar");
    }

    @ParameterizedTest
    @CsvSource({"320,240", "427,240", "640,360", "960,540"})
    void tightInSurvivalAvoidsTheExperienceBarAndSitsOverTheFoodRow(int w, int h) {
        Rect r = StaminaHudLayout.place(Position.ABOVE_HOTBAR_TIGHT, new Hud(w, h, true, 0), 1.0, 0, 0);
        int cx = w / 2;
        assertEquals(cx + 91, r.x() + r.w(), "right-aligned with the hotbar");
        assertEquals(h - 39 - 2, r.y() + r.h(), "2 px above the food row");
        assertFalse(overlaps(r, cx - 91, h - 29, cx + 91, h - 24), "experience bar");
        assertFalse(overlaps(r, cx - 91, h - 22, cx + 91, h), "hotbar");
        assertFalse(overlaps(r, cx + 10, h - 39, cx + 91, h - 30), "food row");
        assertFalse(overlaps(r, cx - 91, h - 49, cx - 10, h - 30), "hearts and armor");
    }

    @Test
    void airBubblesAndMountHeartsPushItUp() {
        Rect plain = StaminaHudLayout.place(Position.ABOVE_HOTBAR_TIGHT, new Hud(640, 360, true, 0), 1.0, 0, 0);
        Rect air = StaminaHudLayout.place(Position.ABOVE_HOTBAR_TIGHT, new Hud(640, 360, true, 1), 1.0, 0, 0);
        Rect mount = StaminaHudLayout.place(Position.ABOVE_HOTBAR_TIGHT, new Hud(640, 360, true, 2), 1.0, 0, 0);
        assertEquals(plain.y() - 10, air.y());
        assertEquals(plain.y() - 20, mount.y());
        assertFalse(overlaps(air, 320 + 10, 360 - 49, 320 + 91, 360 - 40), "air bubbles");
    }

    @ParameterizedTest
    @CsvSource({"320,240", "640,360", "960,540"})
    void leftOfHotbarIsUprightPastTheOffhandSlotAndBottomAligned(int w, int h) {
        for (boolean status : new boolean[]{true, false}) {
            Rect r = StaminaHudLayout.place(Position.LEFT_OF_HOTBAR, new Hud(w, h, status, 0), 1.0, 0, 0);
            assertTrue(r.vertical());
            assertEquals(7, r.w());
            assertEquals(22, r.h());
            assertEquals(h, r.y() + r.h(), "aligned to the hotbar's bottom");
            assertEquals(w / 2 - 91 - 29 - 3, r.x() + r.w());
            assertFalse(overlaps(r, w / 2 - 91 - 29, h - 23, w / 2 + 91, h), "hotbar and offhand slot");
            assertTrue(r.x() >= 0, "on screen at the smallest GUI width");
        }
    }

    @Test
    void offsetsShiftAndScaleGrowsFromTheAnchor() {
        Rect base = StaminaHudLayout.place(Position.ABOVE_HOTBAR_TIGHT, new Hud(640, 360, true, 0), 1.0, 0, 0);
        Rect moved = StaminaHudLayout.place(Position.ABOVE_HOTBAR_TIGHT, new Hud(640, 360, true, 0), 1.0, 5, -3);
        assertEquals(base.x() + 5, moved.x());
        assertEquals(base.y() - 3, moved.y());
        Rect big = StaminaHudLayout.place(Position.ABOVE_HOTBAR_TIGHT, new Hud(640, 360, true, 0), 2.0, 0, 0);
        assertEquals(164, big.w());
        assertEquals(14, big.h());
        assertEquals(base.x() + base.w(), big.x() + big.w(), "keeps the right edge");
        assertEquals(base.y() + base.h(), big.y() + big.h(), "keeps the bottom edge");
        Rect left = StaminaHudLayout.place(Position.LEFT_OF_HOTBAR, new Hud(640, 360, true, 0), 2.0, 0, 0);
        assertEquals(360, left.y() + left.h());
        assertEquals(320 - 91 - 29 - 3, left.x() + left.w());
    }
}
