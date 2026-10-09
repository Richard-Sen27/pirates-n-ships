package com.richardsenger.piratesnships.ship.decor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fitting the ship's name to a row of name boards (NAME2), and what a board shows (pure). */
class NameplateTextTest {

    /** "Black Gull" in the vanilla font, font units. */
    private static final int BLACK_GULL = 50;

    @Test
    void theTextAreaIsTheRowLessFrameAndMargin() {
        assertEquals(12f, NameplateText.areaWidth(1));
        assertEquals(44f, NameplateText.areaWidth(3));
        assertEquals(92f, NameplateText.areaWidth(6));
        assertEquals(12f, NameplateText.areaWidth(0), "a row has at least one board");
        assertEquals(10f, NameplateText.AREA_HEIGHT, "14 px board, 1 px frame and 1 px margin at top and bottom");
    }

    @Test
    void theNameFillsTheRowsLength() {
        for (int boards = 1; boards <= 4; boards++) {
            for (int width : new int[]{40, 60, 80}) {
                float scale = NameplateText.fitScale(boards, width);
                if (scale < NameplateText.MAX_SCALE) {
                    assertEquals(NameplateText.areaWidth(boards), width * scale, 1e-3, boards + " boards, " + width + " units");
                }
            }
        }
        // a longer row draws the same name larger
        float one = NameplateText.fitScale(1, BLACK_GULL);
        float three = NameplateText.fitScale(3, BLACK_GULL);
        assertTrue(three > 3 * one, "3 boards: " + three + " against " + one);
    }

    @Test
    void theHeightCapsTheScale() {
        // a short name on a long row stops at the board's height
        assertEquals(NameplateText.MAX_SCALE, NameplateText.fitScale(6, BLACK_GULL));
        assertEquals(NameplateText.MAX_SCALE, NameplateText.fitScale(1, 0));
        assertEquals(1.25f, NameplateText.MAX_SCALE);
        float glyph = NameplateText.GLYPH_HEIGHT * NameplateText.fitScale(20, 5);
        assertTrue(glyph <= NameplateText.AREA_HEIGHT + 1e-4, "a glyph cell of " + glyph + " px is taller than the text area");
        assertTrue(NameplateText.CENTER_Y - glyph / 2 >= NameplateText.BOARD_BOTTOM + NameplateText.FRAME
                && NameplateText.CENTER_Y + glyph / 2 <= NameplateText.BOARD_TOP - NameplateText.FRAME, "the text runs into the frame");
    }

    @Test
    void aOneBoardRowShowsAUsualNameSmall() {
        float scale = NameplateText.fitScale(1, BLACK_GULL);
        assertEquals(0.24f, scale, 1e-4);
        // about as large as the G8 nameplate's 0.2 for the same name, and still a sign's size at 16 blocks
        assertTrue(NameplateText.readableDistance(scale) >= 16f, "readable from " + NameplateText.readableDistance(scale));
    }

    @Test
    void veryLongNamesStopAtTheSmallestScaleAndAreCut() {
        int width = 300;
        float scale = NameplateText.fitScale(1, width);
        assertEquals(NameplateText.MIN_SCALE, scale);
        int max = NameplateText.maxWidthAt(1, scale);
        assertTrue(max < width);
        assertTrue(max * scale <= NameplateText.areaWidth(1) + 1e-4, "the cut text fits the board");
        assertEquals(96, max);
        // on three boards the same name fits without a cut
        assertTrue(NameplateText.maxWidthAt(3, NameplateText.fitScale(3, width)) >= width);
    }

    @Test
    void rowsAreReadableFromFarther() {
        // the G8 plate drew "Black Gull" at 0.2 px per unit: a sign's size at about 19 blocks
        assertEquals(19.2f, NameplateText.readableDistance(0.2f), 1e-3);
        // three boards: past the default name_board_distance (64)
        assertTrue(NameplateText.readableDistance(NameplateText.fitScale(3, BLACK_GULL)) > 64f);
        assertTrue(NameplateText.readableDistance(NameplateText.fitScale(6, BLACK_GULL)) > 100f);
    }

    @Test
    void theTextSitsOnThePanelBehindTheFrame() {
        assertTrue(NameplateText.TEXT_Z < NameplateText.PANEL_FRONT, "text must sit in front of the panel (toward -z)");
        assertTrue(NameplateText.TEXT_Z > NameplateText.FRAME_FRONT, "text must stay behind the frame's front");
        assertEquals(14f, NameplateText.BOARD_TOP - NameplateText.BOARD_BOTTOM, "the board is 14 px tall");
        assertEquals(2f, 16f - NameplateText.FRAME_FRONT, "the board is 2 px thick");
    }

    @Test
    void boardShowsTheCleanedNameOnlyOnAShipWithTheToggleOn() {
        assertEquals("Black Gull", NameplateText.shown(true, true, "  Black Gull "));
        assertEquals("", NameplateText.shown(true, false, "Black Gull"));
        assertEquals("", NameplateText.shown(false, true, "Black Gull"));
        assertEquals("", NameplateText.shown(true, true, ""));
        assertEquals("", NameplateText.clean(null));
        assertEquals(NameplateText.MAX_LENGTH, NameplateText.clean("x".repeat(80)).length());
    }

    @Test
    void unnamedWreckPieceShowsWreckOfTheLinesName() {
        // a wreck piece (RS1): no name of its own, the name its ship had
        assertEquals(new NameplateText.Shown("Black Gull", true), NameplateText.shown(true, true, "", true, " Black Gull "));
        // a named ship shows its own name, wreck or not
        assertEquals(new NameplateText.Shown("Sea Dog", false), NameplateText.shown(true, true, "Sea Dog", true, "Black Gull"));
        assertEquals(new NameplateText.Shown("Sea Dog", false), NameplateText.shown(true, true, "Sea Dog", false, ""));
        // a wreck of an unnamed ship, not a wreck, off a ship or with the toggle off: blank
        assertEquals(NameplateText.Shown.NONE, NameplateText.shown(true, true, "", true, ""));
        assertEquals(NameplateText.Shown.NONE, NameplateText.shown(true, true, "", false, "Black Gull"));
        assertEquals(NameplateText.Shown.NONE, NameplateText.shown(true, false, "", true, "Black Gull"));
        assertEquals(NameplateText.Shown.NONE, NameplateText.shown(false, true, "", true, "Black Gull"));
        assertEquals("nameplate.pirates_n_ships.wreck_of", NameplateText.KEY_WRECK_OF);
    }
}
