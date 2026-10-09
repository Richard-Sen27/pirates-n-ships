package com.richardsenger.piratesnships.almanac.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The index spread's chapter grid (ALM8, docs/almanac.md "The index spread"), pure: one entry per chapter in book
 * order, each the chapter's icon on a tile and its title beside it, filling the left page's text box column by column.
 * One column while every row gets at least {@link #MIN_ROW} pixels, else more columns (at most {@link #MAX_COLUMNS});
 * rows grow up to {@link #MAX_ROW}. A title is drawn at full size when it fits its room, else a little smaller (down to
 * {@link #MIN_SCALE}), on two lines when the row is high enough; what still does not fit ends in an ellipsis, and the
 * screen's tooltip shows the whole title. Coordinates are relative to the page's text box.
 */
public final class IndexLayout {

    public static final int MIN_ROW = 10;
    public static final int MAX_ROW = 20;
    public static final int MAX_COLUMNS = 3;
    public static final int COLUMN_GAP = 4;
    /** Space between the tile and the title. */
    public static final int TITLE_GAP = 3;
    /** A tile at least this high gets the tile frame; smaller rows draw the bare icon. */
    public static final int FRAMED_TILE = 14;
    /** The font's line height (8 px glyphs and a pixel of leading). */
    public static final int FONT_LINE = 9;
    public static final float[] SCALES = {1.0f, 0.875f, 0.75f};
    public static final float MIN_SCALE = 0.75f;
    public static final String ELLIPSIS = "...";

    private IndexLayout() {
    }

    /**
     * One chapter's entry.
     *
     * @param chapter   the chapter's index in book order
     * @param cell      the whole entry, the click and hover area
     * @param tile      the tile behind the icon
     * @param framed    whether the tile frame is drawn
     * @param iconSize  the icon's size in pixels, centred on the tile
     * @param textX     the left of the title's lines
     * @param textY     the top of the first line
     * @param scale     the title's text scale
     * @param lines     the title's lines as drawn (the last may end in {@link #ELLIPSIS})
     * @param shortened whether the title was cut
     */
    public record Entry(int chapter, PageLayout.Area cell, PageLayout.Area tile, boolean framed, int iconSize,
                        int textX, int textY, float scale, List<String> lines, boolean shortened) {

        public Entry {
            lines = List.copyOf(lines);
        }

        /** The height of one of the title's lines. */
        public float lineHeight() {
            return FONT_LINE * scale;
        }
    }

    /** The grid's shape for {@code count} chapters in a box {@code width x height}. */
    public record Grid(int columns, int rows, int rowHeight, int columnWidth) {
    }

    public static Grid grid(int count, int width, int height) {
        int n = Math.max(1, count);
        int columns = 1;
        while (columns < MAX_COLUMNS && height / ceilDiv(n, columns) < MIN_ROW) columns++;
        int rows = ceilDiv(n, columns);
        int rowH = Math.max(1, Math.min(MAX_ROW, height / rows));
        int colW = (width - (columns - 1) * COLUMN_GAP) / columns;
        return new Grid(columns, rows, rowH, colW);
    }

    /** The entries of {@code titles} (book order) in a box {@code width x height}. */
    public static List<Entry> layout(List<String> titles, int width, int height, FontMetrics metrics) {
        List<Entry> out = new ArrayList<>(titles.size());
        if (titles.isEmpty()) return out;
        Grid g = grid(titles.size(), width, height);
        int tileSize = Math.min(g.rowHeight(), MAX_ROW);
        boolean framed = tileSize >= FRAMED_TILE;
        int icon = framed ? Math.min(16, tileSize - 4) : Math.min(PageLayout.INLINE_ICON, tileSize);
        int maxLines = g.rowHeight() >= 2 * FONT_LINE + 1 ? 2 : 1;
        int titleW = Math.max(1, g.columnWidth() - tileSize - TITLE_GAP);
        for (int i = 0; i < titles.size(); i++) {
            int col = i / g.rows();
            int row = i % g.rows();
            int x = col * (g.columnWidth() + COLUMN_GAP);
            int y = row * g.rowHeight();
            PageLayout.Area cell = new PageLayout.Area(x, y, g.columnWidth(), g.rowHeight());
            PageLayout.Area tile = new PageLayout.Area(x, y + (g.rowHeight() - tileSize) / 2, tileSize, tileSize);
            Fitted f = fit(titles.get(i), titleW, maxLines, metrics);
            float textH = f.lines().size() * FONT_LINE * f.scale() - f.scale();
            int textY = y + Math.max(0, Math.round((g.rowHeight() - textH) / 2f));
            out.add(new Entry(i, cell, tile, framed, icon, x + tileSize + TITLE_GAP, textY, f.scale(), f.lines(), f.shortened()));
        }
        return out;
    }

    /** A title fitted into its room. */
    record Fitted(List<String> lines, float scale, boolean shortened) {
    }

    /** {@code title} in at most {@code maxLines} lines of {@code width} pixels: the largest scale that fits, else cut. */
    static Fitted fit(String title, int width, int maxLines, FontMetrics m) {
        for (float s : SCALES) {
            List<String> lines = wrap(title, width / s, m);
            if (lines != null && lines.size() <= maxLines) return new Fitted(lines, s, false);
        }
        // nothing fits: the smallest scale, filled line by line, the last line cut with an ellipsis
        float s = MIN_SCALE;
        double room = width / s;
        List<String> lines = new ArrayList<>();
        String rest = title.trim();
        while (!rest.isEmpty() && lines.size() < maxLines) {
            boolean last = lines.size() == maxLines - 1;
            String line = last ? rest : longestPrefix(rest, room, m);
            if (last && m.width(line, false, false) > room) {
                line = cut(line, room - m.width(ELLIPSIS, false, false), m) + ELLIPSIS;
                rest = "";
            } else {
                rest = rest.substring(line.length()).trim();
            }
            lines.add(line);
        }
        return new Fitted(lines, s, true);
    }

    /** Word-wrapped lines of at most {@code room} pixels; null when a single word is wider than that. */
    private static List<String> wrap(String text, double room, FontMetrics m) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (m.width(word, false, false) > room) return null;
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (m.width(candidate, false, false) <= room) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    /** The longest run of whole words of {@code text} that fits {@code room}, else the characters that fit (at least one). */
    private static String longestPrefix(String text, double room, FontMetrics m) {
        String best = null;
        int i = text.indexOf(' ');
        while (i > 0) {
            String head = text.substring(0, i);
            if (m.width(head, false, false) > room) break;
            best = head;
            i = text.indexOf(' ', i + 1);
        }
        if (m.width(text, false, false) <= room) return text;
        return best != null ? best : cut(text, room, m);
    }

    /** The longest prefix of {@code text} no wider than {@code room} (at least one character). */
    private static String cut(String text, double room, FontMetrics m) {
        int k = 0;
        while (k < text.length() && m.width(text.substring(0, k + 1), false, false) <= room) k++;
        return text.substring(0, Math.max(1, k)).stripTrailing();
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }
}
