package com.richardsenger.piratesnships.almanac.client;

/**
 * The geometry of the Almanac's recipe panel (ALM8, docs/almanac.md "Recipe panels and spotlights"), pure: where each
 * slot, the arrow, the flame and the result sit inside a panel of {@link #W} x {@link #H} book pixels, and which grid
 * slot each ingredient of a recipe takes. The screen ({@link AlmanacScreen}) reads the recipe from the client's
 * recipe manager and draws the pieces from {@code textures/gui/almanac/panels.png} (tools/gen_almanac_textures.py
 * draws the same sizes).
 */
public final class RecipeGrid {

    /** The panel: as wide as a page's text box. */
    public static final int W = 116;
    public static final int H = 62;
    /** An input slot (the item sits one pixel in). */
    public static final int SLOT = 18;
    public static final int GRID_X = 6;
    public static final int GRID_Y = 4;
    /** The arrow between the grid and the result. */
    public static final int ARROW_X = 62;
    public static final int ARROW_Y = 23;
    public static final int ARROW_W = 22;
    public static final int ARROW_H = 15;
    /** The result's larger slot (the item sits five pixels in). */
    public static final int OUT = 26;
    public static final int OUT_X = 86;
    public static final int OUT_Y = 18;
    /** The mark of a shapeless recipe, above the result. */
    public static final int MARK = 9;
    public static final int MARK_X = OUT_X + (OUT - MARK) / 2;
    public static final int MARK_Y = 6;
    /** A furnace recipe: its one input slot (the grid's top middle) and the flame under it. */
    public static final int FLAME = 14;
    public static final int COOK_IN_X = GRID_X + SLOT;
    public static final int COOK_IN_Y = GRID_Y + 4;
    public static final int FLAME_X = COOK_IN_X + (SLOT - FLAME) / 2;
    public static final int FLAME_Y = COOK_IN_Y + SLOT + 4;
    /** A single-input recipe without heat (the stonecutter): the grid's middle slot. */
    public static final int SINGLE_X = GRID_X + SLOT;
    public static final int SINGLE_Y = GRID_Y + SLOT;
    /** Ticks one alternative of an ingredient (a tag, several items) shows before the next. */
    public static final int CYCLE_TICKS = 20;

    /** How the panel shows a recipe. */
    public enum Kind {
        /** A shaped crafting recipe: its pattern in the 3x3 grid. */
        SHAPED,
        /** A shapeless crafting recipe: the ingredients in reading order, with the shapeless mark. */
        SHAPELESS,
        /** Smelting, blasting, smoking, campfire cooking: one input over a flame. */
        COOKING,
        /** The stonecutter: one input. */
        SINGLE,
        /** A recipe type the panel does not know, or no recipe: the result only, if any. */
        OTHER
    }

    private RecipeGrid() {
    }

    /** The x of grid slot {@code slot} (0..8, row by row). */
    public static int slotX(int slot) {
        return GRID_X + (slot % 3) * SLOT;
    }

    /** The y of grid slot {@code slot} (0..8, row by row). */
    public static int slotY(int slot) {
        return GRID_Y + (slot / 3) * SLOT;
    }

    /**
     * The grid slot of ingredient {@code index} of a shaped recipe {@code width x height} (row by row); a pattern
     * narrower or lower than three is centred where it can be (a one-wide column in the middle column).
     *
     * @throws IllegalArgumentException for a pattern larger than 3x3 or an index outside it
     */
    public static int shapedSlot(int index, int width, int height) {
        if (width < 1 || height < 1 || width > 3 || height > 3) throw new IllegalArgumentException("pattern " + width + "x" + height);
        if (index < 0 || index >= width * height) throw new IllegalArgumentException("ingredient " + index + " of " + width + "x" + height);
        int col = index % width + (3 - width) / 2;
        int row = index / width + (3 - height) / 2;
        return row * 3 + col;
    }

    /**
     * The grid slot of ingredient {@code index} of a shapeless recipe with {@code count} ingredients: reading order
     * from the top left.
     *
     * @throws IllegalArgumentException for more than nine ingredients or an index outside them
     */
    public static int shapelessSlot(int index, int count) {
        if (count < 1 || count > 9) throw new IllegalArgumentException(count + " ingredients");
        if (index < 0 || index >= count) throw new IllegalArgumentException("ingredient " + index + " of " + count);
        return index;
    }

    /** Which of {@code options} alternatives of an ingredient shows at {@code gameTime} (ticks). */
    public static int cycle(int options, long gameTime) {
        if (options <= 1) return 0;
        return (int) Math.floorMod(gameTime / CYCLE_TICKS, (long) options);
    }
}
