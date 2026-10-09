package com.richardsenger.piratesnships.sailing.sail;

import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The colour of a sail's cloth (SAIL2, docs/design.md §4.8 "Sails: dyeable"; pure, vanilla classes only). The sail
 * renderers draw the cloth, the hanging part and the furled bundle alike, with {@link #clothTint} as vertex colour over
 * ART3's canvas weave, the way vanilla tints a banner's base layer ({@link DyeColor#getTextureDiffuseColor()}).
 * White is the natural canvas: a white dye or a white banner leaves the weave as it is.
 */
public final class SailTint {

    /** No tint: the canvas as drawn (white multiplies to the texture's own colours). */
    public static final int NONE = 0xFFFFFF;

    private SailTint() {
    }

    /** RGB (no alpha) of {@code color} on the cloth; white and null are {@link #NONE}. */
    public static int rgb(@Nullable DyeColor color) {
        return color == null || color == DyeColor.WHITE ? NONE : color.getTextureDiffuseColor() & 0xFFFFFF;
    }

    /** The base colour of a banner item, or null if the stack is no banner. */
    public static @Nullable DyeColor bannerBase(ItemStack banner) {
        return banner.getItem() instanceof BannerItem b ? b.getColor() : null;
    }

    /**
     * The cloth's tint: a shown banner's base colour wins (its patterns are drawn over it), else the dye while
     * {@code sails.dyeing} is on, else the natural canvas.
     *
     * @param dye         the sail's dye, or null (never dyed)
     * @param banner      the banner hung on the sail, or empty
     * @param dyeing      server config {@code sailing.sails.dyeing}
     * @param bannerShown whether the banner is shown ({@link SailBanner#shown})
     */
    public static int clothTint(@Nullable DyeColor dye, ItemStack banner, boolean dyeing, boolean bannerShown) {
        DyeColor base = bannerShown ? bannerBase(banner) : null;
        if (base != null) {
            return rgb(base);
        }
        return dyeing ? rgb(dye) : NONE;
    }
}
