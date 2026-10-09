package com.richardsenger.piratesnships.almanac.client;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The recipe panel's geometry and slot mapping (ALM8). */
class RecipeGridTest {

    @Test
    void aFullPatternFillsTheGridRowByRow() {
        for (int i = 0; i < 9; i++) assertEquals(i, RecipeGrid.shapedSlot(i, 3, 3));
    }

    @Test
    void smallPatternsAreCentredWhereTheyCanBe() {
        // a stick-like column: the middle column
        assertEquals(List.of(1, 4), List.of(RecipeGrid.shapedSlot(0, 1, 2), RecipeGrid.shapedSlot(1, 1, 2)));
        // a 1x1 pattern: the middle slot
        assertEquals(4, RecipeGrid.shapedSlot(0, 1, 1));
        // a 2x2 pattern stays top left (it cannot be centred on whole slots)
        assertEquals(List.of(0, 1, 3, 4), List.of(RecipeGrid.shapedSlot(0, 2, 2), RecipeGrid.shapedSlot(1, 2, 2),
                RecipeGrid.shapedSlot(2, 2, 2), RecipeGrid.shapedSlot(3, 2, 2)));
        // a 3x1 row: the middle row
        assertEquals(List.of(3, 4, 5), List.of(RecipeGrid.shapedSlot(0, 3, 1), RecipeGrid.shapedSlot(1, 3, 1), RecipeGrid.shapedSlot(2, 3, 1)));
    }

    @Test
    void shapelessIngredientsGoInReadingOrder() {
        assertEquals(0, RecipeGrid.shapelessSlot(0, 3));
        assertEquals(2, RecipeGrid.shapelessSlot(2, 3));
        assertThrows(IllegalArgumentException.class, () -> RecipeGrid.shapelessSlot(0, 10));
        assertThrows(IllegalArgumentException.class, () -> RecipeGrid.shapelessSlot(3, 3));
        assertThrows(IllegalArgumentException.class, () -> RecipeGrid.shapedSlot(0, 4, 1));
        assertThrows(IllegalArgumentException.class, () -> RecipeGrid.shapedSlot(4, 2, 2));
    }

    @Test
    void alternativesCycleOncePerSecond() {
        assertEquals(0, RecipeGrid.cycle(1, 12345));
        assertEquals(0, RecipeGrid.cycle(3, 0));
        assertEquals(0, RecipeGrid.cycle(3, RecipeGrid.CYCLE_TICKS - 1));
        assertEquals(1, RecipeGrid.cycle(3, RecipeGrid.CYCLE_TICKS));
        assertEquals(0, RecipeGrid.cycle(3, 3L * RecipeGrid.CYCLE_TICKS));
    }

    @Test
    void everyPieceSitsInsideThePanelWithoutOverlap() {
        Set<Integer> ys = new HashSet<>();
        for (int slot = 0; slot < 9; slot++) {
            assertTrue(RecipeGrid.slotX(slot) >= 0 && RecipeGrid.slotX(slot) + RecipeGrid.SLOT <= RecipeGrid.ARROW_X);
            assertTrue(RecipeGrid.slotY(slot) >= 0 && RecipeGrid.slotY(slot) + RecipeGrid.SLOT <= RecipeGrid.H);
            ys.add(RecipeGrid.slotY(slot));
        }
        assertEquals(3, ys.size());
        assertTrue(RecipeGrid.ARROW_X + RecipeGrid.ARROW_W <= RecipeGrid.OUT_X, "arrow before the result");
        assertTrue(RecipeGrid.OUT_X + RecipeGrid.OUT <= RecipeGrid.W && RecipeGrid.OUT_Y + RecipeGrid.OUT <= RecipeGrid.H);
        assertTrue(RecipeGrid.MARK_Y + RecipeGrid.MARK <= RecipeGrid.OUT_Y, "the shapeless mark above the result");
        assertTrue(RecipeGrid.FLAME_Y + RecipeGrid.FLAME <= RecipeGrid.H);
        assertTrue(RecipeGrid.W <= 116, "the panel fits a page's text box");
        assertTrue(AlmanacPanels.tileU(AlmanacPanels.TILE_MAX) + AlmanacPanels.TILE_MAX <= AlmanacPanels.TEX_W, "every tile in the atlas");
        assertEquals(AlmanacPanels.TILE_U + 10, AlmanacPanels.tileU(11));
    }
}
