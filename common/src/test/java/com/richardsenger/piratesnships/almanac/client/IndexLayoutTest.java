package com.richardsenger.piratesnships.almanac.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The index spread's chapter grid (ALM8) with a fixed-width font: 6 px per character. */
class IndexLayoutTest {

    private static final FontMetrics MONO = (text, bold, italic) -> text.length() * 6;
    private static final int W = 116;
    private static final int H = 140;
    /** The book's fourteen titles today. */
    private static final List<String> BOOK = List.of("Welcome aboard", "Your first ship", "Ships", "Sailing", "Crew", "Flags",
            "Provisions", "Cargo and trade", "Law", "Weapons and combat", "Charts and navigation", "The ship's inventory",
            "The Quartermaster's ledger", "Not yet charted");

    private static List<String> titles(int n) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(i < BOOK.size() ? BOOK.get(i) : "Chapter " + (i + 1));
        return out;
    }

    /** Every entry inside the box, in order, none overlapping, each title inside its cell. */
    private static void checkSound(List<IndexLayout.Entry> entries, int n) {
        assertEquals(n, entries.size());
        for (int i = 0; i < n; i++) {
            IndexLayout.Entry e = entries.get(i);
            assertEquals(i, e.chapter(), "book order");
            PageLayout.Area c = e.cell();
            assertTrue(c.x() >= 0 && c.y() >= 0 && c.right() <= W && c.bottom() <= H, "cell " + c + " inside the page");
            PageLayout.Area t = e.tile();
            assertTrue(t.x() >= c.x() && t.right() <= c.right() && t.y() >= c.y() && t.bottom() <= c.bottom(), "tile in its cell");
            assertTrue(e.iconSize() <= t.w() && e.iconSize() >= PageLayout.INLINE_ICON, "icon " + e.iconSize() + " readable and on its tile");
            assertFalse(e.lines().isEmpty());
            for (String line : e.lines()) {
                assertTrue(e.textX() + MONO.width(line, false, false) * e.scale() <= c.right() + 0.01, "'" + line + "' inside its cell");
            }
            assertTrue(e.textY() + e.lines().size() * e.lineHeight() <= c.bottom() + 1, "title lines inside the cell");
            assertTrue(e.scale() >= IndexLayout.MIN_SCALE);
            for (int j = 0; j < i; j++) {
                PageLayout.Area o = entries.get(j).cell();
                boolean apart = o.right() <= c.x() || c.right() <= o.x() || o.bottom() <= c.y() || c.bottom() <= o.y();
                assertTrue(apart, "entries " + j + " and " + i + " overlap");
            }
        }
    }

    @Test
    void fourteenChaptersFillOneColumnOfTheLeftPage() {
        IndexLayout.Grid g = IndexLayout.grid(14, W, H);
        assertEquals(new IndexLayout.Grid(1, 14, 10, W), g);
        List<IndexLayout.Entry> entries = IndexLayout.layout(titles(14), W, H, MONO);
        checkSound(entries, 14);
        // full size wherever the title fits a line; only the longest one is drawn smaller or cut
        for (IndexLayout.Entry e : entries) {
            if (MONO.width(BOOK.get(e.chapter()), false, false) <= W - 10 - IndexLayout.TITLE_GAP) {
                assertEquals(1.0f, e.scale(), BOOK.get(e.chapter()));
                assertEquals(List.of(BOOK.get(e.chapter())), e.lines());
            }
        }
        IndexLayout.Entry ledger = entries.get(12);
        assertTrue(ledger.scale() < 1f, "the ledger's long title is drawn smaller");
        assertEquals(10 * 13, entries.get(13).cell().y(), "rows of 10 px, top to bottom");
    }

    @Test
    void twentyChaptersTakeTwoColumnsInBookOrder() {
        IndexLayout.Grid g = IndexLayout.grid(20, W, H);
        assertEquals(2, g.columns());
        assertEquals(10, g.rows());
        assertEquals(14, g.rowHeight());
        List<IndexLayout.Entry> entries = IndexLayout.layout(titles(20), W, H, MONO);
        checkSound(entries, 20);
        assertEquals(0, entries.get(9).cell().x(), "the first ten down the left column");
        assertEquals(g.columnWidth() + IndexLayout.COLUMN_GAP, entries.get(10).cell().x(), "the rest down the right column");
        assertEquals(0, entries.get(10).cell().y());
        assertTrue(entries.getFirst().framed(), "rows of 14 px draw framed tiles");
        IndexLayout.Entry ledger = entries.get(12);
        assertTrue(ledger.shortened() && ledger.lines().getLast().endsWith(IndexLayout.ELLIPSIS), "a title that cannot fit is cut");
    }

    @Test
    void fewChaptersGetLargerRowsWithTwoLineTitles() {
        List<IndexLayout.Entry> entries = IndexLayout.layout(List.of("Welcome aboard", "Weapons and combat at sea and ashore"), W, H, MONO);
        checkSound(entries, 2);
        assertEquals(IndexLayout.MAX_ROW, entries.getFirst().cell().h());
        assertTrue(entries.getFirst().framed());
        assertEquals(16, entries.getFirst().iconSize());
        assertEquals(2, entries.get(1).lines().size(), "a long title wraps onto a second line");
    }

    @Test
    void manyChaptersStayInsideThePage() {
        for (int n : new int[]{1, 7, 13, 15, 27, 40}) checkSound(IndexLayout.layout(titles(n), W, H, MONO), n);
        assertTrue(IndexLayout.layout(List.of(), W, H, MONO).isEmpty());
    }
}
