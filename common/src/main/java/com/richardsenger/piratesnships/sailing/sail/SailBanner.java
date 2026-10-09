package com.richardsenger.piratesnships.sailing.sail;

import com.richardsenger.piratesnships.ship.decor.flag.FlagBanner;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jetbrains.annotations.Nullable;

/**
 * A banner on a square sail (SAIL2, docs/design.md §4.8 "large sails can carry banner patterns"; pure, vanilla classes
 * only, no world access): which sails may carry one, and how the banner's design lies on the cloth.
 * <p>
 * <b>Eligibility.</b> A square sail carries a banner when both its yards are at least {@code sails.banner_min_width}
 * blocks long and they are at least {@code sails.banner_min_drop} blocks apart ({@link #eligible}). Stay sails take dye
 * only. A hung banner is shown while the sail still qualifies and {@code sails.banners} is on ({@link #shown}); a
 * sail that shrinks keeps it (sneak-use takes it back).
 * <p>
 * <b>The mapping (after FLG2's {@link FlagBanner}).</b> Vanilla's 64 × 64 pattern textures hold the banner's front at
 * x 1..21, y 1..41 ({@link FlagBanner#REGION_X0} etc.). The region is stood upright on the cloth and scaled to it:
 * its top edge along the upper yard, its bottom at the lower yard, its sides on the cloth's slanted edges. Across, the
 * place is the cloth grid's fraction {@code t} (0 at the yard's negative end, 1 at its positive end), so a trapezoid
 * cloth carries the design trapezoidal too. Down, it is the depth below the upper yard over the full drop
 * ({@link #designDepth}), so a reefed sail (half trim) shows the banner's upper half, as real cloth would. Where the
 * cloth shows its frayed foot (ART5, {@code SailFoot}), the bottom {@link #FRAY} block is left bare so the fray's holes
 * stay holes ({@link #clipDepth}).
 * <p>
 * <b>Front and back.</b> The mapping depends only on the place on the cloth, never on the face: the back shows the same
 * texel at the same place, so from behind the design reads mirrored, like a real sail. The face it reads right from is
 * the one toward the ship's bow (a ship coming at you shows its sails' fronts); on land, the face toward +z for a yard
 * along x and toward +x for a yard along z ({@link #flipAcross}).
 */
public final class SailBanner {

    /** Vanilla draws at most this many pattern layers; so do we. */
    public static final int MAX_LAYERS = FlagBanner.MAX_LAYERS;
    /** The frayed rows of the foot tile: the bottom 9 of its 32 pixel rows (ART5, {@code SailFoot}) [blocks]. */
    public static final float FRAY = 9f / 32f;

    private SailBanner() {
    }

    /**
     * Whether a square sail of this cloth may carry a banner: both yards at least {@code minWidth} blocks long (the
     * shorter one counts) and at least {@code minDrop} blocks apart.
     */
    public static boolean eligible(@Nullable ClothGeometry g, int minWidth, int minDrop) {
        if (g == null) return false;
        float width = Math.min(g.upperNeg() + g.upperPos(), g.lowerNeg() + g.lowerPos());
        return width >= minWidth - 1.0e-3f && g.drop() >= minDrop;
    }

    /** Whether a hung {@code banner} is shown on a sail of this cloth: a banner, banners on, and the sail qualifies. */
    public static boolean shown(ItemStack banner, @Nullable ClothGeometry g, boolean banners, int minWidth, int minDrop) {
        return banners && isBanner(banner) && eligible(g, minWidth, minDrop);
    }

    public static boolean isBanner(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BannerItem;
    }

    /** The banner's pattern layers, bottom to top, at most {@link #MAX_LAYERS}; empty for anything but a banner. */
    public static List<BannerPatternLayers.Layer> layers(ItemStack banner) {
        if (!isBanner(banner)) return List.of();
        List<BannerPatternLayers.Layer> all = banner.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers();
        return all.size() > MAX_LAYERS ? all.subList(0, MAX_LAYERS) : all;
    }

    /** The depth [blocks below the upper yard] the design's bottom edge maps to on a sail of this {@code drop}. */
    public static float designDepth(float drop, boolean footShown) {
        return footShown ? drop - FRAY : drop;
    }

    /** How far down the design is drawn on a cloth that reaches down to {@code bottom}. */
    public static float clipDepth(float bottom, boolean footShown) {
        return footShown ? bottom - FRAY : bottom;
    }

    /**
     * Whether the design's left edge lies at the yard's positive end ({@code t = 1}) instead of its negative end, so
     * it reads right from the front face (see the class comment).
     *
     * @param alongX the yards run along x
     * @param bowDx  plot x of the ship's bow (0 with {@code bowDz} 0 on land, or when the bow is not known)
     * @param bowDz  plot z of the ship's bow
     */
    public static boolean flipAcross(boolean alongX, int bowDx, int bowDz) {
        int toward = alongX ? bowDz : bowDx; // the bow along the cloth's out axis (+z for a yard along x, +x along z)
        int front = toward < 0 ? -1 : 1;
        return alongX ? front < 0 : front > 0;
    }

    /** The pattern texture's u (0..1 over the whole 64-pixel texture) at fraction {@code t} across the cloth. */
    public static float patternU(float t, boolean flip) {
        float across = flip ? 1f - t : t;
        return (FlagBanner.REGION_X0 + (FlagBanner.REGION_X1 - FlagBanner.REGION_X0) * clamp(across)) / FlagBanner.PATTERN_TEXTURE_SIZE;
    }

    /** The pattern texture's v (0..1, top 0) at {@code depth} blocks below the upper yard. */
    public static float patternV(float depth, float designDepth) {
        float down = designDepth <= 0f ? 0f : depth / designDepth;
        return (FlagBanner.REGION_Y0 + (FlagBanner.REGION_Y1 - FlagBanner.REGION_Y0) * clamp(down)) / FlagBanner.PATTERN_TEXTURE_SIZE;
    }

    private static float clamp(float x) {
        return Math.max(0f, Math.min(1f, x));
    }
}
