package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.law.flag.FlagKind;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.List;

/**
 * A banner's design on the flag cloth (FLG2, docs/design.md §4.7 "Banner flags with patterns"; pure, vanilla classes
 * only, no world access). The client's {@code FlagClothRenderer} draws the cloth of a custom flag tinted with the
 * banner's base colour ({@link FlagTint}), then every pattern layer of the banner ({@link #layers}) once more over the
 * same rippled strips, with the pattern's sprite from vanilla's banner atlas tinted with the layer's dye.
 * <p>
 * <b>The banner region.</b> Vanilla's 64 × 64 pattern textures hold the banner's front face at x 1..21, y 1..41
 * ({@code BannerRenderer}'s flag part, 20 wide and 40 tall, the top at y 1). This class maps that region onto the part
 * of the cloth between {@link #DESIGN_START_COLUMN} and {@link #DESIGN_END_COLUMN}: the heading tape and its stitch
 * line at the hoist and the frayed last column at the fly keep the bare base cloth, so the fray's cutout holes stay
 * holes.
 * <p>
 * <b>Orientation.</b> By default the banner hangs <em>sideways</em> from the pole, as a real banner turned into a
 * flag: its top edge at the hoist, its length running along the fly, its right edge (as it faces you upright) at the
 * top of the cloth. Seen on the front face (the hoist on the left) the design reads as the banner turned a quarter
 * turn anticlockwise, not mirrored. The region's 40 × 20 pixels fall on 20 × 16 cloth pixels, so the design is
 * shortened along the fly by about a third relative to its height; that keeps every part of the design on the cloth.
 * The client option {@code flag_visuals.banner_upright} instead stands the banner upright (its top at the top of the
 * cloth, its left edge at the hoist), stretched along the fly and squashed in height to fill the same area.
 * <p>
 * <b>Front and back.</b> The mapping depends only on the place on the cloth (how far along from the hoist, how high),
 * never on the face: the back face shows the same texel at the same place, so from behind the design reads mirrored,
 * like a real flag (and like the base cloth, {@link FlagClothModel}).
 */
public final class FlagBanner {

    /** Vanilla draws at most this many pattern layers ({@code BannerRenderer.renderPatterns}); so do we. */
    public static final int MAX_LAYERS = 16;
    /** Pattern textures are 64 × 64 pixels; the banner's front face is the region below. */
    public static final float PATTERN_TEXTURE_SIZE = 64f;
    public static final float REGION_X0 = 1f;
    public static final float REGION_X1 = 21f;
    public static final float REGION_Y0 = 1f;
    public static final float REGION_Y1 = 41f;

    /** First cloth column of the design: columns 0..2 are the heading tape and its stitch line. */
    public static final int DESIGN_START_COLUMN = 3;
    /** Column the design ends at: the last column (23) is the frayed fly, left as bare cloth. */
    public static final int DESIGN_END_COLUMN = FlagClothModel.LENGTH - 1;
    /** Where the design starts and ends along the cloth (0 hoist, 1 tip; {@link FlagRipple#along} units). */
    public static final float DESIGN_START = (float) DESIGN_START_COLUMN / FlagClothModel.LENGTH;
    public static final float DESIGN_END = (float) DESIGN_END_COLUMN / FlagClothModel.LENGTH;

    private FlagBanner() {
    }

    /**
     * The pattern layers drawn on the cloth of a flag of {@code kind} made from {@code item}, bottom to top: the
     * banner's {@link DataComponents#BANNER_PATTERNS}, at most {@link #MAX_LAYERS}. Empty for anything but a custom
     * flag. The base colour is not a layer here: it is the cloth's tint ({@link FlagTint#clothTint}).
     */
    public static List<BannerPatternLayers.Layer> layers(FlagKind kind, ItemStack item) {
        if (kind != FlagKind.CUSTOM || item.isEmpty()) return List.of();
        List<BannerPatternLayers.Layer> all = item.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers();
        return all.size() > MAX_LAYERS ? all.subList(0, MAX_LAYERS) : all;
    }

    /** How far into the design a place {@code along} the cloth is (0 at {@link #DESIGN_START}, 1 at {@link #DESIGN_END}). */
    public static float design(float along) {
        return (along - DESIGN_START) / (DESIGN_END - DESIGN_START);
    }

    /**
     * The pattern texture's u (0..1 over the whole 64-pixel texture) at a place on the cloth.
     *
     * @param along   how far from the hoist (0) to the tip (1), within {@link #DESIGN_START}..{@link #DESIGN_END}
     * @param height  how high on the cloth (0 bottom, 1 top)
     * @param upright {@code flag_visuals.banner_upright}
     */
    public static float patternU(float along, float height, boolean upright) {
        float across = upright ? design(along) : height;
        return (REGION_X0 + (REGION_X1 - REGION_X0) * across) / PATTERN_TEXTURE_SIZE;
    }

    /** The pattern texture's v (0..1, top 0) at a place on the cloth; see {@link #patternU}. */
    public static float patternV(float along, float height, boolean upright) {
        float down = upright ? 1f - height : design(along);
        return (REGION_Y0 + (REGION_Y1 - REGION_Y0) * down) / PATTERN_TEXTURE_SIZE;
    }

    /** Where strip {@code k} of the ripple ({@link FlagRipple#STRIPS}) starts carrying the design (along the cloth). */
    public static float stripStart(int k) {
        return Math.max(FlagRipple.along(k), DESIGN_START);
    }

    /** Where strip {@code k} stops carrying the design; at or before {@link #stripStart} if it carries none. */
    public static float stripEnd(int k) {
        return Math.min(FlagRipple.along(k + 1), DESIGN_END);
    }

    /**
     * The weight of strip boundary {@code k + 1} at {@code along} inside strip {@code k} (0 at boundary {@code k}, 1 at
     * {@code k + 1}): the design's quads lie on the strip's own quad, so their corners are interpolated between the
     * strip's rippled boundaries.
     */
    public static float weight(int k, float along) {
        return (along - FlagRipple.along(k)) * FlagRipple.STRIPS;
    }
}
