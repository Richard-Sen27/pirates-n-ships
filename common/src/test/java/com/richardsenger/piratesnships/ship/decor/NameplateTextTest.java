package com.richardsenger.piratesnships.ship.decor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fitting the ship's name to the nameplate's board, and what a plate shows (pure). */
class NameplateTextTest {

    @Test
    void shortNamesUseTheLargestScale() {
        assertEquals(NameplateText.MAX_SCALE, NameplateText.fitScale(0));
        assertEquals(NameplateText.MAX_SCALE, NameplateText.fitScale(12));
        // exactly the board width at the largest scale
        assertEquals(NameplateText.MAX_SCALE, NameplateText.fitScale((int) (NameplateText.AREA_WIDTH / NameplateText.MAX_SCALE)));
    }

    @Test
    void longerNamesShrinkToTheBoardWidth() {
        for (int width : new int[]{21, 30, 45, 60, 80}) {
            float scale = NameplateText.fitScale(width);
            assertTrue(scale < NameplateText.MAX_SCALE, width + " units should shrink");
            assertEquals(NameplateText.AREA_WIDTH, width * scale, 1e-4, width + " units should fill the board exactly");
        }
    }

    @Test
    void veryLongNamesStopAtTheSmallestScaleAndAreCut() {
        int width = 300;
        float scale = NameplateText.fitScale(width);
        assertEquals(NameplateText.MIN_SCALE, scale);
        int max = NameplateText.maxWidthAt(scale);
        assertTrue(max < width);
        assertTrue(max * scale <= NameplateText.AREA_WIDTH + 1e-4, "the cut text fits the board");
        assertEquals(80, max);
    }

    @Test
    void theBoardAreaStaysOnThePanelBetweenTheRivets() {
        float left = NameplateText.CENTER_X - NameplateText.AREA_WIDTH / 2;
        float right = NameplateText.CENTER_X + NameplateText.AREA_WIDTH / 2;
        assertTrue(left >= 2.75f && right <= 13.25f, "text runs into the rivets: " + left + ".." + right);
        float half = NameplateText.GLYPH_HEIGHT * NameplateText.MAX_SCALE / 2;
        assertTrue(NameplateText.CENTER_Y - half >= 5f && NameplateText.CENTER_Y + half <= 11f, "text taller than the panel");
        assertTrue(NameplateText.TEXT_Z < NameplateText.FRONT_Z, "text must sit in front of the panel (toward -z)");
    }

    @Test
    void plateShowsTheCleanedNameOnlyOnAShipWithTheToggleOn() {
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
