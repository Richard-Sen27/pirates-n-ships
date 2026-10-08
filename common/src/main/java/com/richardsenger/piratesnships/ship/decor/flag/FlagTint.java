package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.law.flag.FlagKind;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The colour of a custom (banner) flag's cloth (pure, vanilla classes only). The client's cloth renderer draws the
 * cloth of {@link FlagKind#CUSTOM} ({@link FlagClothModel#tinted}) with {@link #clothTint(FlagpoleState)} as vertex
 * colour: the banner's base dye colour, as vanilla draws the banner's base layer
 * ({@link DyeColor#getTextureDiffuseColor()}). The banner's pattern layers are drawn over the tinted cloth
 * ({@link FlagBanner}, FLG2).
 */
public final class FlagTint {

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
        return clothTint(state.kind(), state.flagItem());
    }

    /** The cloth tint of a flag of {@code kind} made from {@code item} (VIS1a: the flag being hoisted). */
    public static int clothTint(FlagKind kind, ItemStack item) {
        if (kind != FlagKind.CUSTOM) return NONE;
        DyeColor base = bannerBase(item);
        return base == null ? NONE : rgb(base);
    }
}
