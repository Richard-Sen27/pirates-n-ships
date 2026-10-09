package com.richardsenger.piratesnships.almanac.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The chapter bookmarks on the open book's edges (ALM3, ALM7), pure. One tab per chapter in book order: down the left
 * edge first, top to bottom, and when the left edge is full ({@link #perSide} tabs at {@link #TAB_H} pixels plus
 * {@link #GAP}) on down the right edge from the top. When more chapters exist than both edges hold, the chapters split
 * evenly (the left edge takes the odd one) and each edge spreads its tabs over its full height: the gap shrinks first,
 * then the tabs overlap, and no tab ever leaves its edge. A left tab is the mirror of a right one: {@link #UNDER_COVER}
 * pixels of its inner end lie under the cover, and the open chapter's tab sticks out {@link #SELECTED_OUT} pixels further
 * on its own side. Coordinates are in book pixels.
 */
public final class Bookmarks {

    /** Tab width; the inner {@link #UNDER_COVER} pixels lie under the cover. */
    public static final int TAB_W = 24;
    public static final int UNDER_COVER = 4;
    /** Tab height, the bookmark texture's height. */
    public static final int MAX_H = 16;
    public static final int TAB_H = MAX_H;
    /** The gap between two tabs while an edge has room for it. */
    public static final int GAP = 2;
    public static final int SELECTED_OUT = 3;

    private Bookmarks() {
    }

    /** Which edge of the open book a tab sits on. */
    public enum Side {
        LEFT, RIGHT;

        /** +1 for the right edge (the tab points to +x), -1 for the left. */
        public int outward() {
            return this == RIGHT ? 1 : -1;
        }
    }

    /** One tab: the chapter index, its edge and its rectangle (the open chapter's already sticks out further). */
    public record Tab(int chapter, Side side, int x, int y, int w, int h) {

        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }

        /** The x of the tab's outer end (its swallowtail), past which nothing of it is drawn. */
        public int tipX() {
            return side == Side.RIGHT ? x + w : x;
        }
    }

    /** How many tabs one edge of {@code height} pixels holds at the normal gap. */
    public static int perSide(int height) {
        return Math.max(1, (height + GAP) / (TAB_H + GAP));
    }

    /**
     * The tabs of {@code count} chapters along the book's left edge at {@code leftEdgeX} and right edge at
     * {@code rightEdgeX}, between {@code top} and {@code top + height}; {@code selected} is the open chapter (-1 for
     * none).
     */
    public static List<Tab> layout(int count, int selected, int leftEdgeX, int rightEdgeX, int top, int height) {
        List<Tab> tabs = new ArrayList<>(Math.max(0, count));
        if (count <= 0) return tabs;
        int fit = perSide(height);
        int onLeft = count <= 2 * fit ? Math.min(count, fit) : (count + 1) / 2;
        int onRight = count - onLeft;
        for (int i = 0; i < count; i++) {
            boolean left = i < onLeft;
            int row = left ? i : i - onLeft;
            int y = top + rowOffset(row, left ? onLeft : onRight, fit, height);
            int out = i == selected ? SELECTED_OUT : 0;
            int w = TAB_W + out;
            int x = left ? leftEdgeX + UNDER_COVER - w : rightEdgeX - UNDER_COVER;
            tabs.add(new Tab(i, left ? Side.LEFT : Side.RIGHT, x, y, w, TAB_H));
        }
        return tabs;
    }

    /** The y offset of tab {@code row} of {@code n} on one edge: the normal step while they fit, else spread evenly. */
    private static int rowOffset(int row, int n, int fit, int height) {
        if (n <= fit || n <= 1) return row * (TAB_H + GAP);
        return row * Math.max(0, height - TAB_H) / (n - 1);
    }

    /** The chapter whose tab is at the point, or -1; where tabs overlap the later (drawn on top) wins. */
    public static int hit(List<Tab> tabs, double px, double py) {
        for (int i = tabs.size() - 1; i >= 0; i--) {
            Tab t = tabs.get(i);
            if (t.contains(px, py)) return t.chapter();
        }
        return -1;
    }
}
