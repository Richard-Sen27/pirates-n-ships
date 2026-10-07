package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.law.flag.FlagKind;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The colour of a custom (banner) flag's cloth (pure, vanilla classes only). The cloth model of
 * {@link FlagKind#CUSTOM} has {@link #TINT_INDEX} on its faces, and the client's block colour handler multiplies the
 * cloth texture by {@link #clothTint(FlagpoleState)}: the banner's base dye colour, as vanilla draws the banner's
 * base layer ({@link DyeColor#getTextureDiffuseColor()}). Banner patterns are not shown (only the base colour).
 */
public final class FlagTint {

    /** The tint index on the custom cloth's faces. */
    public static final int TINT_INDEX = 0;
    /** No tint (white multiplies to the texture's own colours). */
    public static final int NONE = 0xFFFFFF;

    private FlagTint() {
    }

    /** The base dye colour of a banner item, or null if the stack is no banner. */
    public static @Nullable DyeColor bannerBase(ItemStack stack) {
        return stack.getItem() instanceof BannerItem banner ? banner.getColor() : null;
    }

    /** RGB (no alpha) of a dye as the banner's base layer. */
    public static int rgb(DyeColor color) {
        return color.getTextureDiffuseColor() & 0xFFFFFF;
    }

    /** The cloth tint of a flagpole: the banner's base colour for a custom flag, {@link #NONE} otherwise. */
    public static int clothTint(FlagpoleState state) {
        if (state.kind() != FlagKind.CUSTOM) return NONE;
        DyeColor base = bannerBase(state.flagItem());
        return base == null ? NONE : rgb(base);
    }
}
