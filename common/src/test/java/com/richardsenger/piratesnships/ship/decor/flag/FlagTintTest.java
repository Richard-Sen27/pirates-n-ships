package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.law.flag.FlagKind;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The banner colour of custom flags and which cloth takes it. */
class FlagTintTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static FlagpoleState flying(FlagKind kind, ItemStack item) {
        return new FlagpoleState(kind, item, false, Optional.empty(), Optional.empty());
    }

    @Test
    void bannerBaseColourComesFromTheBannerItem() {
        assertEquals(DyeColor.RED, FlagTint.bannerBase(new ItemStack(Items.RED_BANNER)));
        assertEquals(DyeColor.WHITE, FlagTint.bannerBase(new ItemStack(Items.WHITE_BANNER)));
        assertEquals(DyeColor.BLACK, FlagTint.bannerBase(new ItemStack(Items.BLACK_BANNER)));
        assertNull(FlagTint.bannerBase(new ItemStack(Items.WHITE_WOOL)));
        assertNull(FlagTint.bannerBase(ItemStack.EMPTY));
    }

    @Test
    void rgbIsTheBannerLayerColourWithoutAlpha() {
        for (DyeColor c : DyeColor.values()) {
            int rgb = FlagTint.rgb(c);
            assertEquals(0, rgb & 0xFF000000, c + " has alpha bits");
            assertEquals(c.getTextureDiffuseColor() & 0xFFFFFF, rgb);
        }
        assertEquals(0xF9FFFE, FlagTint.rgb(DyeColor.WHITE));
        assertEquals(0xB02E26, FlagTint.rgb(DyeColor.RED));
    }

    @Test
    void onlyACustomBannerFlagIsTinted() {
        assertEquals(FlagTint.rgb(DyeColor.BLUE), FlagTint.clothTint(flying(FlagKind.CUSTOM, new ItemStack(Items.BLUE_BANNER))));
        // struck: the pole keeps the banner, its colour stays ready for when the colours go up again
        assertEquals(FlagTint.rgb(DyeColor.BLUE), FlagTint.clothTint(
                new FlagpoleState(FlagKind.CUSTOM, new ItemStack(Items.BLUE_BANNER), true, Optional.empty(), Optional.empty())));
        assertEquals(FlagTint.NONE, FlagTint.clothTint(FlagpoleState.EMPTY));
        assertEquals(FlagTint.NONE, FlagTint.clothTint(flying(FlagKind.MERCHANT, new ItemStack(Items.PAPER))));
        // a custom flag that somehow holds no banner falls back to no tint
        assertEquals(FlagTint.NONE, FlagTint.clothTint(flying(FlagKind.CUSTOM, new ItemStack(Items.PAPER))));
    }

    @Test
    void onlyTheCustomClothIsTinted() {
        for (FlagKind kind : FlagKind.values()) {
            if (kind == FlagKind.NONE) continue;
            if (kind == FlagKind.CUSTOM) {
                assertTrue(FlagClothModel.tinted(kind), kind + " must take the banner's colour");
            } else {
                assertFalse(FlagClothModel.tinted(kind), kind + " must keep its own colours");
            }
        }
    }
}
