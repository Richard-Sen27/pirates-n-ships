package com.richardsenger.piratesnships.ship.decor.flag;

import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * The flag cloth on the flagpole (design.md §4.7, "one block high and 1.5 to 2 blocks long"), as block model JSON
 * built in code (pure). One model per flag kind; the block state rotates it to the facing (downwind).
 * <p>
 * <b>Geometry</b> (model pixels, pointing north, i.e. toward {@code -Z}): a 1 px thick cloth from
 * {@code (7.5, 0, -16)} to {@code (8.5, 16, 8)}: the full block height, starting at the pole's center (so there is no
 * gap at the pole; the first 2 px are hidden inside the 4 px pole) and reaching 24 px (1.5 blocks) from the pole's
 * center, 22 px past its surface. 24 px is the most a vanilla model can reach from the block center: element
 * coordinates must stay in {@code -16..32} ({@code BlockElement}), so a 2-block cloth from the pole does not fit in
 * one model.
 * <p>
 * <b>Texture</b> {@code block/flag_<kind>}, 32 × 16 pixels (see {@code tools/gen_flag_textures.py}): columns 0..23 are
 * the cloth at 1 texel per model pixel (column 0 at the pole's center, 23 at the tip), columns 24..31 a swatch of
 * the base color for the thin top and bottom edges. In vanilla's UV space (16 units = the whole texture) a texel
 * column is 0.5 units wide, so the cloth spans {@code u = 0..12}.
 * <p>
 * <b>Faces</b>: east (front) shows the texture with the hoist at the pole; west (back) the same UVs reversed, i.e.
 * mirrored like a real flag seen from behind, hoist still at the pole; north (the tip) the last cloth column; up and
 * down the swatch. No south face (inside the pole), no cullface, no ambient occlusion (the cloth reaches past the
 * block, where the block's own AO samples would be wrong). Rendered cutout. No collision: the block's shape stays the
 * pole's, the cloth is a visual overlap into the space downwind.
 * <p>
 * <b>Rotation</b>: the block state rotates the whole model with {@code y = 0/90/180/270} for north/east/south/west
 * (vanilla rotates vertex positions around the block center, so coordinates outside the block are fine, as for the
 * extended piston's arm). No {@code uvlock}: uvlock re-derives UVs from world axes, which would break the cloth's
 * texture mapping on the rotated sides.
 */
public final class FlagClothModel {

    public static final float X0 = 7.5f;
    public static final float X1 = 8.5f;
    public static final float BOTTOM = 0f;
    public static final float TOP = 16f;
    /** The tip, at the vanilla model limit. */
    public static final float TIP_Z = ElementModel.MIN_EXTENT;
    /** The hoist end, at the pole's center. */
    public static final float HOIST_Z = 8f;
    /** Cloth length in model pixels (= texture columns). */
    public static final int LENGTH = (int) (HOIST_Z - TIP_Z);
    /** Texture size in pixels. */
    public static final int TEXTURE_WIDTH = 32;
    public static final int TEXTURE_HEIGHT = 16;
    /** UV units per texture column (vanilla UVs span 16 units over the whole texture). */
    public static final float U_PER_COLUMN = 16f / TEXTURE_WIDTH;
    /** u at the tip end of the cloth. */
    public static final float CLOTH_U1 = LENGTH * U_PER_COLUMN;
    public static final String TEXTURE_KEY = "cloth";
    public static final String RENDER_TYPE = "minecraft:cutout";

    private FlagClothModel() {
    }

    /** The model id of a kind's cloth: {@code pirates_n_ships:block/flagpole_flag_<kind>}. */
    public static ResourceLocation modelId(FlagKind kind) {
        if (kind == FlagKind.NONE) throw new IllegalArgumentException("no cloth for FlagKind.NONE");
        return Constants.id("block/flagpole_flag_" + kind.getSerializedName());
    }

    /** The texture of a kind's cloth: {@code pirates_n_ships:block/flag_<kind>}. */
    public static ResourceLocation texture(FlagKind kind) {
        return Constants.id("block/flag_" + kind.getSerializedName());
    }

    /** The block state's y rotation (degrees) that turns the north-pointing cloth to {@code facing}. */
    public static int yRotation(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            case NORTH -> 0;
            default -> throw new IllegalArgumentException("not horizontal: " + facing);
        };
    }

    /** The cloth model JSON for a texture location such as {@code pirates_n_ships:block/flag_navy}. */
    public static JsonObject json(String texture) {
        String ref = "#" + TEXTURE_KEY;
        float swatchU = LENGTH * U_PER_COLUMN;
        return new ElementModel()
                .renderType(RENDER_TYPE)
                .ambientOcclusion(false)
                .texture("particle", texture)
                .texture(TEXTURE_KEY, texture)
                .element(X0, BOTTOM, TIP_Z, X1, TOP, HOIST_Z)
                .face(ElementModel.Face.EAST, 0f, 0f, CLOTH_U1, 16f, ref)
                .face(ElementModel.Face.WEST, CLOTH_U1, 0f, 0f, 16f, ref)
                .face(ElementModel.Face.NORTH, CLOTH_U1 - U_PER_COLUMN, 0f, CLOTH_U1, 16f, ref)
                .face(ElementModel.Face.UP, swatchU, 0f, swatchU + U_PER_COLUMN, 1f, ref)
                .face(ElementModel.Face.DOWN, swatchU, 15f, swatchU + U_PER_COLUMN, 16f, ref)
                .end()
                .build();
    }

    public static JsonObject json(FlagKind kind) {
        return json(texture(kind).toString());
    }
}
