package com.richardsenger.piratesnships.almanac.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The chapter bookmarks on the book's right edge (ALM3), pure. One tab per chapter in book order, top to bottom;
 * tabs shrink from {@link #MAX_H} to {@link #MIN_H} pixels to fit the edge, and when even the smallest do not fit,
 * further columns stick out beyond the first. The open chapter's tab sticks out {@link #SELECTED_OUT} pixels further.
 * Coordinates are in book pixels.
 */
public final class Bookmarks {

    /** Tab width; the left {@link #UNDER_COVER} pixels lie under the cover. */
    public static final int TAB_W = 24;
    public static final int UNDER_COVER = 4;
    public static final int MAX_H = 16;
    public static final int MIN_H = 10;
    public static final int GAP = 2;
    public static final int SELECTED_OUT = 3;

    private Bookmarks() {
    }

    /** One tab: the chapter index and its rectangle. */
    public record Tab(int chapter, int x, int y, int w, int h) {

        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    /**
     * The tabs of {@code count} chapters along the edge at {@code edgeX}, between {@code top} and
     * {@code top + height}; {@code selected} is the open chapter (-1 for none).
     */
    public static List<Tab> layout(int count, int selected, int edgeX, int top, int height) {
        List<Tab> tabs = new ArrayList<>(Math.max(0, count));
        if (count <= 0) return tabs;
        int h = Math.max(MIN_H, Math.min(MAX_H, (height + GAP) / count - GAP));
        int perColumn = Math.max(1, (height + GAP) / (h + GAP));
        for (int i = 0; i < count; i++) {
            int col = i / perColumn;
            int row = i % perColumn;
            int x = edgeX - UNDER_COVER + col * TAB_W;
            int y = top + row * (h + GAP);
            tabs.add(new Tab(i, x, y, TAB_W + (i == selected ? SELECTED_OUT : 0), h));
        }
        return tabs;
    }

    /** The chapter whose tab is at the point, or -1. */
    public static int hit(List<Tab> tabs, double px, double py) {
        for (Tab t : tabs) if (t.contains(px, py)) return t.chapter();
        return -1;
    }
}
