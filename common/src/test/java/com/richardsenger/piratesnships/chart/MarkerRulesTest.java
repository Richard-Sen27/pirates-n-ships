package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.chart.data.ChartData;
import com.richardsenger.piratesnships.chart.data.MarkerIcon;
import com.richardsenger.piratesnships.chart.data.MarkerRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Marker validation (MAP1): the cap, names, positions, edits and removal of unknown markers. */
class MarkerRulesTest {

    @Test
    void addGivesFreshIdsAndTrimsTheName() {
        MarkerRules.Outcome a = MarkerRules.add(ChartData.EMPTY, 1, 2, MarkerIcon.X, "  Gold  ", 3);
        assertTrue(a.ok());
        assertEquals("Gold", a.data().markers().get(0).name());
        MarkerRules.Outcome b = MarkerRules.add(a.data(), 3, 4, MarkerIcon.ANCHOR, "", 3);
        assertTrue(b.ok(), "an empty name is allowed");
        assertFalse(a.markerId() == b.markerId());
        // ids are never reused, even after a removal
        MarkerRules.Outcome removed = MarkerRules.remove(b.data(), b.markerId());
        MarkerRules.Outcome c = MarkerRules.add(removed.data(), 0, 0, MarkerIcon.X, "c", 3);
        assertTrue(c.markerId() > b.markerId());
    }

    @Test
    void theCapRefusesAndKeepsTheData() {
        ChartData d = ChartData.EMPTY;
        for (int i = 0; i < 2; i++) d = MarkerRules.add(d, i, i, MarkerIcon.X, "m", 2).data();
        MarkerRules.Outcome over = MarkerRules.add(d, 9, 9, MarkerIcon.X, "m", 2);
        assertEquals(MarkerRules.Refusal.TOO_MANY, over.refusal());
        assertSame(d, over.data());
        assertEquals(MarkerRules.Refusal.TOO_MANY, MarkerRules.add(ChartData.EMPTY, 0, 0, MarkerIcon.X, "", 0).refusal(), "0 markers allowed");
    }

    @Test
    void namesAreChecked() {
        String max = "n".repeat(MarkerRules.MAX_NAME_LENGTH);
        assertTrue(MarkerRules.add(ChartData.EMPTY, 0, 0, MarkerIcon.X, max, 9).ok());
        assertEquals(MarkerRules.Refusal.NAME_TOO_LONG, MarkerRules.add(ChartData.EMPTY, 0, 0, MarkerIcon.X, max + "n", 9).refusal());
        assertEquals(MarkerRules.Refusal.BAD_NAME, MarkerRules.add(ChartData.EMPTY, 0, 0, MarkerIcon.X, "a\nb", 9).refusal());
        assertEquals(MarkerRules.Refusal.BAD_NAME, MarkerRules.add(ChartData.EMPTY, 0, 0, MarkerIcon.X, "§cred", 9).refusal());
    }

    @Test
    void positionsStayInsideTheWorld() {
        assertTrue(MarkerRules.add(ChartData.EMPTY, MarkerRules.MAX_COORDINATE, -MarkerRules.MAX_COORDINATE, MarkerIcon.X, "", 9).ok());
        assertEquals(MarkerRules.Refusal.OUT_OF_WORLD, MarkerRules.add(ChartData.EMPTY, MarkerRules.MAX_COORDINATE + 1, 0, MarkerIcon.X, "", 9).refusal());
        assertEquals(MarkerRules.Refusal.OUT_OF_WORLD, MarkerRules.add(ChartData.EMPTY, 0, Integer.MIN_VALUE, MarkerIcon.X, "", 9).refusal());
    }

    @Test
    void editAndRemove() {
        MarkerRules.Outcome a = MarkerRules.add(ChartData.EMPTY, 1, 2, MarkerIcon.X, "Old", 9);
        MarkerRules.Outcome e = MarkerRules.edit(a.data(), a.markerId(), 5, 6, MarkerIcon.DANGER, "New");
        assertTrue(e.ok());
        var m = e.data().marker(a.markerId()).orElseThrow();
        assertEquals("New", m.name());
        assertEquals(MarkerIcon.DANGER, m.icon());
        assertEquals(5, m.x());
        assertEquals(MarkerRules.Refusal.UNKNOWN_MARKER, MarkerRules.edit(a.data(), 999, 0, 0, MarkerIcon.X, "").refusal());
        assertEquals(MarkerRules.Refusal.NAME_TOO_LONG, MarkerRules.edit(a.data(), a.markerId(), 0, 0, MarkerIcon.X, "n".repeat(99)).refusal());
        MarkerRules.Outcome r = MarkerRules.remove(e.data(), a.markerId());
        assertTrue(r.ok());
        assertTrue(r.data().markers().isEmpty());
        assertEquals(MarkerRules.Refusal.UNKNOWN_MARKER, MarkerRules.remove(r.data(), a.markerId()).refusal());
    }
}
