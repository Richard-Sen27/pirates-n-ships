package com.richardsenger.piratesnships.combat.melee.client;

/**
 * Where the stamina bar goes (pure, tested with JUnit; client config {@code melee_hud.position}). Positions follow
 * vanilla's HUD in GUI pixels: the hotbar is 182 x 22 at the bottom centre; in survival the experience bar sits right
 * on top of it (y {@code h-29..h-24}), the hearts (left half) and the food row (right half, x {@code cx+10..cx+91})
 * above that at {@code h-39}, air bubbles or extra mount-heart rows stack 10 px higher on the right; the offhand
 * slot or the attack indicator take up to 29 px beside the hotbar.
 *
 * <ul>
 *   <li>{@link Position#ABOVE_HOTBAR_TIGHT}: without status bars (creative) 2 px above the hotbar, centred. With
 *   them, the space above the hotbar is the experience bar, so the bar goes over the right half instead: right-aligned
 *   with the hotbar, 2 px above the food row (and above the air bubbles or mount hearts while those show).</li>
 *   <li>{@link Position#LEFT_OF_HOTBAR}: a vertical bar left of the hotbar, past the offhand slot, aligned to the
 *   hotbar's bottom.</li>
 * </ul>
 * The offsets shift the result; the scale grows the bar away from its anchor (the bottom, and the centre, right
 * edge or left side respectively).
 */
public final class StaminaHudLayout {

    public enum Position { ABOVE_HOTBAR_TIGHT, LEFT_OF_HOTBAR }

    /** Unscaled size of the horizontal bar (trough with frame). */
    public static final int HORIZONTAL_W = 82;
    public static final int HORIZONTAL_H = 7;
    /** Unscaled size of the vertical bar: as tall as the hotbar. */
    public static final int VERTICAL_W = 7;
    public static final int VERTICAL_H = 22;
    /** Inner (fill) size of the horizontal and vertical bar. */
    public static final int FILL_LENGTH_H = 80;
    public static final int FILL_LENGTH_V = 20;

    static final int HOTBAR_HALF = 91;
    static final int HOTBAR_H = 22;
    static final int GAP = 2;
    /** Top of the hearts and food row, measured up from the bottom. */
    static final int STATUS_ROW = 39;
    static final int ROW = 10;
    /** The offhand slot (or the attack indicator) beside the hotbar. */
    static final int SIDE_SLOT = 29;
    static final int SIDE_GAP = 3;

    /**
     * What the vanilla HUD shows right now.
     *
     * @param statusBars     health, food and experience are drawn (survival, adventure)
     * @param rightRowsAbove rows stacked above the food row on the right (air bubbles, extra mount-heart rows)
     */
    public record Hud(int guiWidth, int guiHeight, boolean statusBars, int rightRowsAbove) {
    }

    /** The bar's screen rectangle (scaled size); {@code vertical} = fills from the bottom up. */
    public record Rect(int x, int y, int w, int h, boolean vertical) {
    }

    private StaminaHudLayout() {
    }

    public static Rect place(Position position, Hud hud, double scale, int offsetX, int offsetY) {
        int cx = hud.guiWidth() / 2;
        int bottom = hud.guiHeight();
        if (position == Position.LEFT_OF_HOTBAR) {
            int w = scaled(VERTICAL_W, scale);
            int h = scaled(VERTICAL_H, scale);
            int x = cx - HOTBAR_HALF - SIDE_SLOT - SIDE_GAP - w;
            return new Rect(x + offsetX, bottom - h + offsetY, w, h, true);
        }
        int w = scaled(HORIZONTAL_W, scale);
        int h = scaled(HORIZONTAL_H, scale);
        if (!hud.statusBars()) {
            return new Rect(cx - w / 2 + offsetX, bottom - HOTBAR_H - GAP - h + offsetY, w, h, false);
        }
        int rowTop = bottom - STATUS_ROW - ROW * Math.max(0, hud.rightRowsAbove());
        return new Rect(cx + HOTBAR_HALF - w + offsetX, rowTop - GAP - h + offsetY, w, h, false);
    }

    static int scaled(int size, double scale) {
        return Math.max(1, (int) Math.round(size * scale));
    }
}
