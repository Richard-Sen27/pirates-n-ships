package com.richardsenger.piratesnships.ship.hull.client;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Where the ship HUD's two panels go and how their parts are laid out (pure, tested with JUnit; HUD1, HUD2).
 *
 * <p>The <b>compass panel</b> ({@link #W} × {@link #COMPASS_H} GUI pixels at scale 1): the compass rose with a ring of
 * room for the wind arrow, the speed and rudder line and the ship's name with its load. The <b>hull panel</b>
 * ({@link #W} × {@link #HULL_H}): the hull strip (bow on the left, one cell per compartment, widths by volume). Each
 * panel is drawn in its own coordinates and scaled from the corner it sits in.
 *
 * <p>Placement ({@link #place}): a panel starts {@link #MARGIN} from its corner and, while it overlaps something the
 * vanilla HUD draws there ({@link Screen#obstacles}: the chat, the hotbar with the status rows, the stamina bar, the
 * status effect icons), moves away from the screen edge to {@link #MARGIN} beyond that obstacle. Both panels in one
 * corner stack as one block, the compass above the hull strip (the HUD1 look); in different corners the hull panel
 * also keeps clear of the compass panel. A panel that cannot get clear on screen keeps its corner and reports
 * {@link Placed#clear()} false; the renderer hides such a panel while the chat is open.
 */
public final class ShipHudLayout {

    /** A screen corner (client config {@code ship_hud.compass_corner}, {@code ship_hud.hull_corner}). */
    public enum Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

        public boolean right() {
            return this == TOP_RIGHT || this == BOTTOM_RIGHT;
        }

        public boolean top() {
            return this == TOP_LEFT || this == TOP_RIGHT;
        }
    }

    /** Unscaled panel width (both panels). */
    public static final int W = 112;
    /** Unscaled height of the compass panel. */
    public static final int COMPASS_H = 88;
    /** Unscaled height of the hull panel. */
    public static final int HULL_H = 17;
    /** Distance of a panel from the screen edges and from whatever it keeps clear of. */
    public static final int MARGIN = 4;

    /** Compass: centre and the radius of the rose sprite, then the wind arrow's ring outside it. */
    public static final int ROSE_CX = W / 2;
    public static final int ROSE_CY = 32;
    public static final int ROSE_R = ShipHudSheet.ROSE.w() / 2;
    /** The wind arrow's tip rests this far from the centre; its tail reaches out up to {@link #ARROW_MAX} more. */
    public static final int ARROW_TIP_R = ROSE_R + 2;
    public static final int ARROW_MIN = 4;
    public static final int ARROW_MAX = 10;
    /** Wind speed [blocks/s] that draws the longest arrow (default storm winds reach about 26). */
    public static final double ARROW_FULL_WIND = 20.0;

    /** Text rows of the compass panel (top y, font line height 9). */
    public static final int SPEED_Y = 66;
    public static final int NAME_Y = 78;

    /** Hull strip in the hull panel: left of it the bow cap, then the cells; the breach tick reaches 2 px above it. */
    public static final int STRIP_X = 4 + ShipHudSheet.BOW.w();
    public static final int STRIP_Y = 2;
    public static final int STRIP_W = W - 4 - STRIP_X;
    public static final int STRIP_H = ShipHudSheet.BOW.h();
    public static final int CELL_GAP = 1;
    public static final int CELL_MIN_W = 4;

    /** Vanilla's HUD in GUI pixels (1.21.1 {@code Gui}, {@code ChatComponent}, {@code ChatScreen}). */
    static final int HOTBAR_HALF = 91;
    static final int HOTBAR_H = 23;
    /** The offhand slot beside the hotbar (either side, by the main hand). */
    static final int OFFHAND_W = 29;
    /** Top of the hearts and food row, measured up from the bottom; each further row is 10 higher. */
    static final int STATUS_ROW = 39;
    static final int STATUS_ROW_H = 10;
    /** The chat's lowest line ends this far above the bottom ({@code Gui#renderChat}, {@code ChatComponent#render}). */
    public static final int CHAT_BOTTOM = 40;
    /** The open chat's input box: full width, from 14 above the bottom ({@code ChatScreen#render}). */
    public static final int CHAT_INPUT_H = 14;
    /** A closed chat draws a line only while it is younger than this many GUI ticks ({@code ChatComponent#render}). */
    public static final int CHAT_FADE_TICKS = 200;

    /** A screen rectangle. */
    public record Rect(int x, int y, int w, int h) {

        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public boolean intersects(Rect o) {
            return x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();
        }
    }

    /** One cell of the strip, relative to {@link #STRIP_X}. */
    public record Span(int x, int w) {
    }

    /** A placed panel; {@code clear} = it overlaps none of the obstacles and fits on screen. */
    public record Placed(Rect rect, boolean clear) {
    }

    /** Both panels. */
    public record Placement(Placed compass, Placed hull) {
    }

    /** The GUI's size and what vanilla (and our other HUD parts) draw on it right now. */
    public record Screen(int guiWidth, int guiHeight, List<Rect> obstacles) {

        public Screen {
            obstacles = List.copyOf(obstacles);
        }
    }

    private ShipHudLayout() {
    }

    /**
     * Both panels' screen rectangles.
     *
     * @param scale the HUD's size ({@code ship_hud.scale})
     */
    public static Placement place(Corner compassCorner, Corner hullCorner, Screen screen, double scale) {
        int w = scaled(W, scale), ch = scaled(COMPASS_H, scale), hh = scaled(HULL_H, scale);
        if (compassCorner == hullCorner) {
            Placed block = placeBlock(compassCorner, w, ch + hh, screen.guiWidth(), screen.guiHeight(), screen.obstacles());
            Rect b = block.rect();
            return new Placement(new Placed(new Rect(b.x(), b.y(), w, ch), block.clear()),
                    new Placed(new Rect(b.x(), b.y() + ch, w, hh), block.clear()));
        }
        Placed compass = placeBlock(compassCorner, w, ch, screen.guiWidth(), screen.guiHeight(), screen.obstacles());
        List<Rect> withCompass = new ArrayList<>(screen.obstacles());
        withCompass.add(compass.rect());
        Placed hull = placeBlock(hullCorner, w, hh, screen.guiWidth(), screen.guiHeight(), withCompass);
        return new Placement(compass, hull);
    }

    /**
     * A {@code w} × {@code h} block at {@code corner}, moved away from the corner's top or bottom edge past every
     * obstacle it overlaps. When it runs off the screen it stays at the corner, not clear.
     */
    static Placed placeBlock(Corner corner, int w, int h, int guiWidth, int guiHeight, List<Rect> obstacles) {
        int x = corner.right() ? guiWidth - MARGIN - w : MARGIN;
        x = Math.max(0, Math.min(x, guiWidth - w));
        int y0 = corner.top() ? MARGIN : guiHeight - MARGIN - h;
        int y = y0;
        // every obstacle is passed at most once (the block only moves away from the edge), so this ends
        for (int step = 0; step <= obstacles.size(); step++) {
            Rect r = new Rect(x, y, w, h);
            Rect hit = firstHit(r, obstacles);
            if (hit == null) {
                if (y >= 0 && y + h <= guiHeight) {
                    return new Placed(r, true);
                }
                break;
            }
            y = corner.top() ? hit.bottom() + MARGIN : hit.y() - MARGIN - h;
            if (y < 0 || y + h > guiHeight) {
                break;
            }
        }
        // no clear spot: the corner, kept on screen (a block larger than the screen keeps its top left visible)
        int yc = Math.max(0, Math.min(y0, guiHeight - h));
        return new Placed(new Rect(x, yc, w, h), false);
    }

    private static @Nullable Rect firstHit(Rect r, List<Rect> obstacles) {
        for (Rect o : obstacles) {
            if (o.w() > 0 && o.h() > 0 && r.intersects(o)) {
                return o;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ vanilla's HUD as obstacles

    /**
     * The chat ({@code ChatComponent#render}): its lines run up from {@link #CHAT_BOTTOM} above the bottom over
     * {@code height} GUI pixels and {@code width} across from the left edge; while it is open ({@code focused}) the
     * input box takes the full width at the bottom as well. No lines when the chat is hidden ({@code height} 0).
     *
     * @param width  the chat's width with its backing, GUI pixels (chat width × chat scale + 8)
     * @param height the height of the lines vanilla draws right now, GUI pixels ({@link #chatHeight}; HUD3: not the
     *               configured chat height, which pushed the compass to the top of a short GUI)
     */
    public static List<Rect> chat(int guiWidth, int guiHeight, int width, int height, boolean focused) {
        List<Rect> out = new ArrayList<>(2);
        if (height > 0 && width > 0) {
            out.add(new Rect(0, guiHeight - CHAT_BOTTOM - height, width, height));
        }
        if (focused) {
            out.add(new Rect(0, guiHeight - CHAT_INPUT_H, guiWidth, CHAT_INPUT_H));
        }
        return out;
    }

    /**
     * How many line rows the chat draws ({@code ChatComponent#render}, HUD3): of the first {@code linesPerPage} lines
     * (newest first), the open chat ({@code focused}) draws every one; the closed chat only those younger than
     * {@link #CHAT_FADE_TICKS}. The rows run up from the chat's bottom by index, so the count is the highest drawn
     * index plus one.
     *
     * @param ages each line's age in GUI ticks ({@code Gui#getGuiTicks} minus {@code GuiMessage.Line#addedTime}),
     *             newest first
     */
    public static int chatRows(int[] ages, int linesPerPage, boolean focused) {
        int n = Math.min(ages.length, Math.max(0, linesPerPage));
        int rows = 0;
        for (int i = 0; i < n; i++) {
            if (focused || ages[i] < CHAT_FADE_TICKS) {
                rows = i + 1;
            }
        }
        return rows;
    }

    /**
     * The chat lines' height in GUI pixels: {@code rows} lines of {@code lineHeight} chat pixels
     * ({@code ChatComponent#getLineHeight}, 9 × (line spacing + 1)) at the chat's {@code scale}.
     */
    public static int chatHeight(int rows, int lineHeight, double scale) {
        if (rows <= 0 || lineHeight <= 0 || !(scale > 0)) {
            return 0;
        }
        return (int) Math.ceil(rows * lineHeight * scale);
    }

    /**
     * The hotbar with both offhand slots, and above it (with {@code statusBars}, survival and adventure) the
     * experience bar and the status rows: hearts and armour on the left, food, air and mount hearts on the right.
     *
     * @param rowsAbove status rows above the hearts and food row (armour, extra heart rows, air, mount hearts; the
     *                  larger of the two sides)
     */
    public static List<Rect> hotbar(int guiWidth, int guiHeight, boolean statusBars, int rowsAbove) {
        int cx = guiWidth / 2;
        List<Rect> out = new ArrayList<>(2);
        out.add(new Rect(cx - HOTBAR_HALF - OFFHAND_W, guiHeight - HOTBAR_H, 2 * (HOTBAR_HALF + OFFHAND_W), HOTBAR_H));
        if (statusBars) {
            int top = STATUS_ROW + STATUS_ROW_H * Math.max(0, rowsAbove);
            out.add(new Rect(cx - HOTBAR_HALF, guiHeight - top, 2 * HOTBAR_HALF, top - HOTBAR_H));
        }
        return out;
    }

    /** Vanilla's status effect icons at the top right: {@code inset} GUI pixels high (0 when none show). */
    public static List<Rect> effects(int guiWidth, int inset) {
        return inset <= 0 ? List.of() : List.of(new Rect(guiWidth / 2, 0, guiWidth - guiWidth / 2, inset));
    }

    /** The factor the renderer scales an unscaled panel by, so it fills {@code r} exactly. */
    public static float factor(Rect r) {
        return r.w() / (float) W;
    }

    static int scaled(int size, double scale) {
        return Math.max(1, (int) Math.round(size * scale));
    }

    // ------------------------------------------------------------------ panel contents

    /**
     * Cells of the strip for compartments of the given volumes (bow first), side by side over {@code width} pixels
     * with {@link #CELL_GAP} between them: each at least {@link #CELL_MIN_W} wide, the rest shared by volume (largest
     * remainder, so the widths add up exactly). When even the minimum does not fit, all cells get the same width.
     */
    public static Span[] cells(int[] volumes, int width) {
        int n = volumes.length;
        Span[] out = new Span[n];
        if (n == 0) {
            return out;
        }
        int avail = Math.max(n, width - CELL_GAP * (n - 1));
        int[] w = new int[n];
        if (n * CELL_MIN_W > avail) {
            int each = Math.max(1, avail / n);
            java.util.Arrays.fill(w, each);
        } else {
            long total = 0;
            for (int v : volumes) {
                total += Math.max(0, v);
            }
            int extra = avail - n * CELL_MIN_W;
            double[] rest = new double[n];
            int given = 0;
            for (int i = 0; i < n; i++) {
                double share = total == 0 ? extra / (double) n : extra * Math.max(0, volumes[i]) / (double) total;
                int whole = (int) Math.floor(share);
                w[i] = CELL_MIN_W + whole;
                rest[i] = share - whole;
                given += whole;
            }
            for (int left = extra - given; left > 0; left--) {
                int best = 0;
                for (int i = 1; i < n; i++) {
                    if (rest[i] > rest[best]) {
                        best = i;
                    }
                }
                w[best]++;
                rest[best] = -1;
            }
        }
        int x = 0;
        for (int i = 0; i < n; i++) {
            out[i] = new Span(x, w[i]);
            x += w[i] + CELL_GAP;
        }
        return out;
    }

    /** Pixels of water in a cell of inner height {@code inner}: at least one while there is any water. */
    public static int waterPixels(float fraction, int inner) {
        if (!(fraction > 0f)) {
            return 0;
        }
        return Math.max(1, Math.min(inner, Math.round(fraction * inner)));
    }

    /** Length of the wind arrow for a wind of {@code strength} blocks/s (0 when calm). */
    public static int arrowLength(double strength) {
        if (!(strength > 0.05)) {
            return 0;
        }
        double f = Math.min(1.0, strength / ARROW_FULL_WIND);
        return (int) Math.round(ARROW_MIN + (ARROW_MAX - ARROW_MIN) * f);
    }
}
