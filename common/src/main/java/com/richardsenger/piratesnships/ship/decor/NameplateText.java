package com.richardsenger.piratesnships.ship.decor;

/**
 * What a name board shows and how big the name is drawn across its row (pure; NAME2, docs/design.md §4.8 "Ship name
 * boards"). The class keeps its G8 name: the name board is the nameplate's successor under the same registry id.
 * Units: model pixels (16 per block) for the board, font units for text widths ({@code Font#width}; a glyph cell is 8
 * units high, capitals 7).
 * <p>
 * <b>The board</b> is the Blockbench model {@code art/models/name_board.bbmodel} in its north orientation: a plank panel
 * flat against the south side of its block, x 0..16, y {@link #BOARD_BOTTOM}..{@link #BOARD_TOP} (14 px tall), its
 * front at z {@link #PANEL_FRONT}, inside a {@link #FRAME} px frame of stripped log (front at z 14, 2 px thick in all).
 * Boards side by side in a row ({@link NameBoardRow}) draw their frame around the whole row, so the text area of a
 * row of {@code n} boards is {@code 16 n} px less the frame and a {@link #MARGIN} px margin at both ends
 * ({@link #areaWidth}), and {@link #AREA_HEIGHT} px high.
 * <p>
 * <b>The scale rule</b> ({@link #fitScale}): model pixels per font unit, as large as fills the row's text area,
 * never taller than {@link #AREA_HEIGHT} (so at most {@link #MAX_SCALE}), never below {@link #MIN_SCALE} (the renderer
 * then cuts the name to {@link #maxWidthAt} with "..."). A one-block board therefore shows a usual name small, a row
 * shows it as large as the board's height allows. {@link #readableDistance} compares the result with a vanilla sign.
 */
public final class NameplateText {

    /** The board's bottom and top edge, model pixels: 14 px tall, centred in the block. */
    public static final float BOARD_BOTTOM = 1f;
    public static final float BOARD_TOP = 15f;
    /** Width of the carved frame around the row, model pixels. */
    public static final float FRAME = 1f;
    /** Free space between the frame and the text, model pixels. */
    public static final float MARGIN = 1f;
    /** The text area's height, model pixels: the board inside its frame and margin. */
    public static final float AREA_HEIGHT = BOARD_TOP - BOARD_BOTTOM - 2 * FRAME - 2 * MARGIN;
    /** The board's vertical centre, model pixels. */
    public static final float CENTER_Y = (BOARD_BOTTOM + BOARD_TOP) / 2f;
    /** The plank panel's front face in the north-facing model (the board lies against z 16). */
    public static final float PANEL_FRONT = 14.5f;
    /** The frame's front face in the north-facing model. */
    public static final float FRAME_FRONT = 14f;
    /** The text is drawn just in front of the panel (toward -z), behind the frame's front. */
    public static final float TEXT_Z = PANEL_FRONT - 0.05f;
    /** Height of a glyph cell in font units (capitals are 7, descenders reach 8). */
    public static final float GLYPH_HEIGHT = 8f;
    /** Largest scale (model pixels per font unit): a glyph cell as high as the text area (capitals 8.75 px). */
    public static final float MAX_SCALE = AREA_HEIGHT / GLYPH_HEIGHT;
    /** Below this scale a name gets too small to read and is cut short instead. */
    public static final float MIN_SCALE = 0.125f;
    /** Most characters kept on the board (an anvil-named tag holds at most 50). */
    public static final int MAX_LENGTH = 50;
    /**
     * A vanilla sign's text scale, blocks per font unit ({@code SignRenderer}: 0.015625 times the text render scale
     * 2/3).
     */
    public static final float SIGN_BLOCKS_PER_UNIT = 0.015625f * 2f / 3f;

    private NameplateText() {
    }

    /** The width of the text area of a row of {@code boards} boards, model pixels (at least one board). */
    public static float areaWidth(int boards) {
        return 16f * Math.max(1, boards) - 2 * FRAME - 2 * MARGIN;
    }

    /**
     * Model pixels per font unit for a text {@code textWidth} font units wide on a row of {@code boards} boards: fills
     * {@link #areaWidth}, capped at {@link #MAX_SCALE} (the board's height), never below {@link #MIN_SCALE}.
     */
    public static float fitScale(int boards, int textWidth) {
        if (textWidth <= 0) return MAX_SCALE;
        float fit = areaWidth(boards) / textWidth;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, fit));
    }

    /** The widest text (font units) that fits a row of {@code boards} boards at {@code scale}. */
    public static int maxWidthAt(int boards, float scale) {
        return (int) Math.floor(areaWidth(boards) / scale + 1e-4f);
    }

    /**
     * The distance in blocks from which text drawn at {@code scale} (model pixels per font unit) looks as large as a
     * vanilla sign's text from 16 blocks: the glyphs are {@code scale / 16} blocks per unit against the sign's
     * {@link #SIGN_BLOCKS_PER_UNIT}.
     */
    public static float readableDistance(float scale) {
        return 16f * (scale / 16f) / SIGN_BLOCKS_PER_UNIT;
    }

    /** The text a board stores: trimmed, cut to {@link #MAX_LENGTH}, empty for none. */
    public static String clean(String name) {
        if (name == null) return "";
        String s = name.strip();
        return s.length() > MAX_LENGTH ? s.substring(0, MAX_LENGTH) : s;
    }

    /** What a board shows: the ship's name while the feature is on and the board is on a ship, otherwise nothing. */
    public static String shown(boolean enabled, boolean onShip, String shipName) {
        return enabled && onShip ? clean(shipName) : "";
    }

    /** Lang key of the line on a wreck piece's board (RS1): "Wreck of %s" with the name the ship had. */
    public static final String KEY_WRECK_OF = "nameplate." + com.richardsenger.piratesnships.Constants.MOD_ID + ".wreck_of";

    /**
     * What a board stores: {@code name} and whether it is drawn as {@link #KEY_WRECK_OF} with that name.
     * {@link #NONE} for a blank board.
     */
    public record Shown(String name, boolean wreckOf) {
        public static final Shown NONE = new Shown("", false);
    }

    /**
     * What a board shows on a ship that may be a wreck piece of a split ship (RS1): a named ship shows its name; an
     * unnamed wreck piece shows "Wreck of" the name its line had (if that had one); off a ship, with the feature off,
     * or with no name at all, nothing.
     */
    public static Shown shown(boolean enabled, boolean onShip, String shipName, boolean wreck, String wreckOf) {
        String name = shown(enabled, onShip, shipName);
        if (!name.isEmpty()) return new Shown(name, false);
        String lineName = enabled && onShip && wreck ? clean(wreckOf) : "";
        return lineName.isEmpty() ? Shown.NONE : new Shown(lineName, true);
    }
}
