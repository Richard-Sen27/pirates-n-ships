package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The flag cloth on the flagpole (design.md §4.7, "one block high and 1.5 to 2 blocks long") as plain geometry
 * (pure): the faces the client's {@code FlagClothRenderer} draws, turned about the pole to the flag's continuous
 * downwind yaw (FL1; before FL1 this was a block model per facing, snapped to four directions).
 * <p>
 * <b>Geometry</b> (blocks, relative to the pole's axis at the bottom of the block, pointing north, i.e. toward
 * {@code -Z}, which is yaw 0): a 1 px thick cloth from {@code x = -0.5 px} to {@code +0.5 px}, the full block height,
 * from the pole's axis ({@code z = 0}, so there is no gap at the pole; the first 1.5 px are hidden inside the 3 px
 * pole) to {@code z = -1.5} blocks: 24 px, as before, so the 24 cloth columns of the texture stay square.
 * <p>
 * <b>Texture</b> {@code textures/block/flag_<kind>.png}, 32 × 16 pixels (see {@code tools/gen_flag_textures.py}):
 * columns 0..23 are the cloth at 1 texel per model pixel (column 0 at the pole, 23 at the tip), columns 24..31 a
 * swatch of the base color for the thin top and bottom edges.
 * <p>
 * <b>Faces</b>: east (front) shows the texture with the hoist at the pole; west (back) the same texture seen from
 * behind, i.e. mirrored like a real flag, hoist still at the pole; north (the tip) the last cloth column; up and down
 * the swatch. No face at the hoist end (inside the pole). The custom (banner) flag is drawn with the banner's base
 * colour as vertex colour ({@link FlagTint}).
 */
public final class FlagClothModel {

    /** Half the cloth's thickness: 0.5 px. */
    public static final float HALF_THICKNESS = 0.5f / 16f;
    public static final float BOTTOM = 0f;
    public static final float TOP = 1f;
    /** The hoist end, on the pole's axis. */
    public static final float HOIST_Z = 0f;
    /** Cloth length in model pixels (= texture columns). */
    public static final int LENGTH = 24;
    /** The tip, 1.5 blocks from the pole's axis. */
    public static final float TIP_Z = -LENGTH / 16f;
    /** Texture size in pixels. */
    public static final int TEXTURE_WIDTH = 32;
    public static final int TEXTURE_HEIGHT = 16;
    /** u (0..1 over the whole texture) at the tip end of the cloth. */
    public static final float CLOTH_U1 = (float) LENGTH / TEXTURE_WIDTH;
    /** u width of one texture column. */
    public static final float COLUMN_U = 1f / TEXTURE_WIDTH;
    /** v height of one texture row. */
    public static final float ROW_V = 1f / TEXTURE_HEIGHT;

    /**
     * One quad: four corners {@code {x, y, z}} in counter-clockwise order seen from outside, their texture
     * coordinates (0..1 over the whole texture) and the outward normal.
     */
    public record Face(String name, float[][] corners, float[] u, float[] v, float nx, float ny, float nz) {
    }

    private static final List<Face> FACES = build();

    private FlagClothModel() {
    }

    /** The texture of a kind's cloth: {@code pirates_n_ships:block/flag_<kind>} (as a sprite name). */
    public static ResourceLocation texture(FlagKind kind) {
        if (kind == FlagKind.NONE) throw new IllegalArgumentException("no cloth for FlagKind.NONE");
        return Constants.id("block/flag_" + kind.getSerializedName());
    }

    /** The texture file the renderer binds: {@code pirates_n_ships:textures/block/flag_<kind>.png}. */
    public static ResourceLocation textureFile(FlagKind kind) {
        ResourceLocation t = texture(kind);
        return ResourceLocation.fromNamespaceAndPath(t.getNamespace(), "textures/" + t.getPath() + ".png");
    }

    /** Only the custom (banner) flag's cloth takes the banner's colour; the other flags keep their own textures. */
    public static boolean tinted(FlagKind kind) {
        return kind == FlagKind.CUSTOM;
    }

    /** The cloth's faces, pointing north (yaw 0). */
    public static List<Face> faces() {
        return FACES;
    }

    private static List<Face> build() {
        float x0 = -HALF_THICKNESS, x1 = HALF_THICKNESS;
        float h = HOIST_Z, t = TIP_Z, b = BOTTOM, top = TOP;
        float swatchU0 = CLOTH_U1, swatchU1 = CLOTH_U1 + COLUMN_U;
        return List.of(
                // Front: seen from +X the tip is on the right; hoist column 0 at the pole.
                new Face("east", new float[][]{{x1, b, h}, {x1, b, t}, {x1, top, t}, {x1, top, h}},
                        new float[]{0f, CLOTH_U1, CLOTH_U1, 0f}, new float[]{1f, 1f, 0f, 0f}, 1f, 0f, 0f),
                // Back: seen from -X the tip is on the left; same columns, so the design reads mirrored.
                new Face("west", new float[][]{{x0, b, t}, {x0, b, h}, {x0, top, h}, {x0, top, t}},
                        new float[]{CLOTH_U1, 0f, 0f, CLOTH_U1}, new float[]{1f, 1f, 0f, 0f}, -1f, 0f, 0f),
                // Tip: the last cloth column.
                new Face("north", new float[][]{{x1, b, t}, {x0, b, t}, {x0, top, t}, {x1, top, t}},
                        new float[]{CLOTH_U1 - COLUMN_U, CLOTH_U1, CLOTH_U1, CLOTH_U1 - COLUMN_U}, new float[]{1f, 1f, 0f, 0f}, 0f, 0f, -1f),
                // Top and bottom edges: the base-colour swatch.
                new Face("up", new float[][]{{x0, top, h}, {x1, top, h}, {x1, top, t}, {x0, top, t}},
                        new float[]{swatchU0, swatchU1, swatchU1, swatchU0}, new float[]{0f, 0f, ROW_V, ROW_V}, 0f, 1f, 0f),
                new Face("down", new float[][]{{x0, b, t}, {x1, b, t}, {x1, b, h}, {x0, b, h}},
                        new float[]{swatchU0, swatchU1, swatchU1, swatchU0}, new float[]{1f - ROW_V, 1f - ROW_V, 1f, 1f}, 0f, -1f, 0f));
    }
}
