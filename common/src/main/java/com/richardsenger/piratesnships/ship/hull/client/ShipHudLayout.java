package com.richardsenger.piratesnships.ship.hull.client;

/**
 * Where the ship HUD goes and how its parts are laid out (pure, tested with JUnit; HUD1). The panel is
 * {@link #W} × {@link #H} GUI pixels at scale 1, drawn in its own coordinates and scaled from the corner it sits in.
 * Inside the panel, top to bottom: the compass rose with a ring of room for the wind arrow, the speed and rudder line,
 * the ship's name and the hull strip (bow on the left, one cell per compartment, widths by volume).
 */
public final class ShipHudLayout {

    /** The screen corner of the HUD (client config {@code ship_hud.corner}). */
    public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    /** Unscaled panel size. */
    public static final int W = 112;
    public static final int H = 104;
    /** Distance of the panel from the screen edges. */
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

    /** Text rows (top y, font line height 9). */
    public static final int SPEED_Y = 66;
    public static final int NAME_Y = 78;

    /** Hull strip: left of it the bow cap, then the cells. */
    public static final int STRIP_X = 4 + ShipHudSheet.BOW.w();
    public static final int STRIP_Y = 89;
    public static final int STRIP_W = W - 4 - STRIP_X;
    public static final int STRIP_H = ShipHudSheet.BOW.h();
    public static final int CELL_GAP = 1;
    public static final int CELL_MIN_W = 4;

    /** A screen rectangle (scaled size). */
    public record Rect(int x, int y, int w, int h) {
    }

    /** One cell of the strip, relative to {@link #STRIP_X}. */
    public record Span(int x, int w) {
    }

    private ShipHudLayout() {
    }

    /**
     * The panel's screen rectangle.
     *
     * @param topRightInset GUI pixels to keep free at the top right (vanilla's status effect icons, 0 when none)
     */
    public static Rect place(Corner corner, int guiWidth, int guiHeight, double scale, int topRightInset) {
        int w = scaled(W, scale), h = scaled(H, scale);
        boolean right = corner == Corner.TOP_RIGHT || corner == Corner.BOTTOM_RIGHT;
        boolean top = corner == Corner.TOP_LEFT || corner == Corner.TOP_RIGHT;
        int x = right ? guiWidth - MARGIN - w : MARGIN;
        int y = top ? MARGIN + (corner == Corner.TOP_RIGHT ? Math.max(0, topRightInset) : 0) : guiHeight - MARGIN - h;
        // a panel larger than the screen keeps its top left corner visible
        x = Math.max(0, Math.min(x, guiWidth - w));
        y = Math.max(0, Math.min(y, guiHeight - h));
        return new Rect(x, y, w, h);
    }

    /** The factor the renderer scales the unscaled panel by, so it fills {@code r} exactly. */
    public static float factor(Rect r) {
        return r.w() / (float) W;
    }

    static int scaled(int size, double scale) {
        return Math.max(1, (int) Math.round(size * scale));
    }

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
