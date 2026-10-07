package com.richardsenger.piratesnships.ship.decor;

/**
 * What a nameplate shows and how big the name is drawn on its board (pure). Units: model pixels (16 per block) for
 * the board, font units for text widths ({@code Font#width}; glyphs are 8 units high).
 * <p>
 * The board is the raised spruce panel of the Blockbench model {@code art/models/nameplate.bbmodel} in its north
 * orientation: x 2..14, y 5..11, front face at z 13.5 (the plate hangs against the south side of its block and faces
 * north). Rivets sit at x 2.25..2.75 and 13.25..13.75, so the text keeps to x 3..13.
 */
public final class NameplateText {

    /** Text area width on the board, model pixels (between the rivets). */
    public static final float AREA_WIDTH = 10f;
    /** Board centre, model pixels. */
    public static final float CENTER_X = 8f;
    public static final float CENTER_Y = 8f;
    /** Front face of the raised panel; the text is drawn just in front of it (toward -z). */
    public static final float FRONT_Z = 13.5f;
    public static final float TEXT_Z = FRONT_Z - 0.05f;
    /** Height of a glyph cell in font units (capitals are 7, descenders reach 8). */
    public static final float GLYPH_HEIGHT = 8f;
    /** Largest scale (model pixels per font unit): glyphs become 4 px, a quarter block, readable from a few blocks. */
    public static final float MAX_SCALE = 0.5f;
    /** Below this scale a name gets too small to read and is cut short instead. */
    public static final float MIN_SCALE = 0.125f;
    /** Most characters kept on the plate (an anvil-named tag holds at most 50). */
    public static final int MAX_LENGTH = 50;

    private NameplateText() {
    }

    /**
     * Model pixels per font unit for a text {@code textWidth} font units wide: as large as {@link #MAX_SCALE}, shrunk
     * to fit {@link #AREA_WIDTH}, never below {@link #MIN_SCALE} (the caller then cuts the text to
     * {@link #maxWidthAt(float)}).
     */
    public static float fitScale(int textWidth) {
        if (textWidth <= 0) return MAX_SCALE;
        float fit = AREA_WIDTH / textWidth;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, fit));
    }

    /** The widest text (font units) that fits the board at {@code scale}. */
    public static int maxWidthAt(float scale) {
        return (int) Math.floor(AREA_WIDTH / scale);
    }

    /** The text a nameplate stores: trimmed, cut to {@link #MAX_LENGTH}, empty for none. */
    public static String clean(String name) {
        if (name == null) return "";
        String s = name.strip();
        return s.length() > MAX_LENGTH ? s.substring(0, MAX_LENGTH) : s;
    }

    /** What a plate shows: the ship's name while the feature is on and the plate is on a ship, otherwise nothing. */
    public static String shown(boolean enabled, boolean onShip, String shipName) {
        return enabled && onShip ? clean(shipName) : "";
    }
}
