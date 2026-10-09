package com.richardsenger.piratesnships.almanac.client;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.resources.ResourceLocation;

/**
 * Where the pieces of the recipe panels, spotlights and index tiles (ALM8) and the 3D spotlight card (ALM10) sit in {@code textures/gui/almanac/panels.png}
 * (drawn by tools/gen_almanac_textures.py; AlmanacTexturesTest checks the size). Plain constants, no client classes.
 */
public final class AlmanacPanels {

    public static final ResourceLocation TEXTURE = Constants.id("textures/gui/almanac/panels.png");
    public static final int TEX_W = 256;
    public static final int TEX_H = 256;

    public static final int PANEL_U = 0;
    public static final int PANEL_V = 0;
    public static final int SLOT_U = 0;
    public static final int SLOT_V = 64;
    public static final int OUT_U = RecipeGrid.SLOT;
    public static final int OUT_V = 64;
    public static final int ARROW_U = 44;
    public static final int ARROW_V = 64;
    public static final int FLAME_U = 66;
    public static final int FLAME_V = 64;
    public static final int MARK_U = 80;
    public static final int MARK_V = 64;
    public static final int SPOTLIGHT_U = 0;
    public static final int SPOTLIGHT_V = 90;
    /** The index tiles: one per size from {@link #TILE_MIN} to {@link #TILE_MAX}, side by side from {@link #TILE_U}. */
    public static final int TILE_MIN = 10;
    public static final int TILE_MAX = 20;
    public static final int TILE_U = 40;
    public static final int TILE_V = 90;

    /** The 3D spotlight's card (ALM10), {@link PageLayout#SPOTLIGHT_3D_W} x {@link PageLayout#SPOTLIGHT_3D_FRAME}. */
    public static final int SPOTLIGHT_3D_U = 0;
    public static final int SPOTLIGHT_3D_V = 128;
    /** The card's leather rim and the shade line inside it: the model is clipped inside this inset. */
    public static final int SPOTLIGHT_3D_INSET = 4;

    private AlmanacPanels() {
    }

    /** The u of the index tile of {@code size} pixels (clamped to the drawn sizes). */
    public static int tileU(int size) {
        int s = Math.max(TILE_MIN, Math.min(TILE_MAX, size));
        int u = TILE_U;
        for (int k = TILE_MIN; k < s; k++) u += k;
        return u;
    }
}
