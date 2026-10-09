package com.richardsenger.piratesnships.sailing.sail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** SAIL2: which square sails carry a banner, how its region lies on the cloth, and the cloth's tint. */
class SailBannerTest {

    private static final float EPS = 1.0e-6f;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** A sail with yards of {@code upper} and {@code lower} blocks (odd: centred on the mast), {@code drop} apart. */
    private static ClothGeometry sail(int upper, int lower, int drop) {
        return new ClothGeometry(true, upper / 2f, upper / 2f, lower / 2f, lower / 2f, drop);
    }

    @Test
    void eligibilityTakesTheShorterYardAndTheDrop() {
        assertTrue(SailBanner.eligible(sail(3, 3, 2), 3, 2), "3 wide, 2 deep is the smallest");
        assertTrue(SailBanner.eligible(sail(5, 7, 4), 3, 2));
        assertFalse(SailBanner.eligible(sail(5, 1, 4), 3, 2), "the shorter yard counts");
        assertFalse(SailBanner.eligible(sail(1, 5, 4), 3, 2));
        assertFalse(SailBanner.eligible(sail(3, 3, 1), 3, 2), "too shallow");
        assertFalse(SailBanner.eligible(null, 3, 2), "no sail");
        // an even yard: 4 blocks, its middle one of the two central blocks
        assertTrue(SailBanner.eligible(new ClothGeometry(false, 1.5f, 2.5f, 1.5f, 2.5f, 3), 4, 2));
        assertFalse(SailBanner.eligible(new ClothGeometry(false, 1.5f, 2.5f, 1.5f, 2.5f, 3), 5, 2));
    }

    @Test
    void shownNeedsABannerTheToggleAndAQualifyingSail() {
        ItemStack banner = new ItemStack(Items.RED_BANNER);
        assertTrue(SailBanner.shown(banner, sail(5, 5, 4), true, 3, 2));
        assertFalse(SailBanner.shown(banner, sail(5, 5, 4), false, 3, 2), "banners off");
        assertFalse(SailBanner.shown(banner, sail(1, 1, 4), true, 3, 2), "a shrunk sail keeps but hides it");
        assertFalse(SailBanner.shown(ItemStack.EMPTY, sail(5, 5, 4), true, 3, 2));
        assertFalse(SailBanner.shown(new ItemStack(Items.RED_WOOL), sail(5, 5, 4), true, 3, 2));
        assertTrue(SailBanner.layers(new ItemStack(Items.RED_WOOL)).isEmpty());
        assertTrue(SailBanner.layers(banner).isEmpty(), "a plain banner has no layers");
    }

    @Test
    void theRegionStandsUprightAndFillsTheCloth() {
        // top-left of the region at the upper yard's left end, bottom-right at the lower yard's right end
        assertEquals(1f / 64f, SailBanner.patternU(0f, false), EPS);
        assertEquals(21f / 64f, SailBanner.patternU(1f, false), EPS);
        assertEquals(11f / 64f, SailBanner.patternU(0.5f, false), EPS);
        assertEquals(21f / 64f, SailBanner.patternU(0f, true), EPS);
        assertEquals(1f / 64f, SailBanner.patternV(0f, 4f), EPS);
        assertEquals(41f / 64f, SailBanner.patternV(4f, 4f), EPS);
        assertEquals(21f / 64f, SailBanner.patternV(2f, 4f), EPS);
        assertEquals(41f / 64f, SailBanner.patternV(5f, 4f), EPS, "clamped below the design");
        // half trim: the cloth reaches half way down and shows the banner's upper half
        float design = SailBanner.designDepth(4f, true);
        assertEquals(4f - 9f / 32f, design, EPS);
        assertEquals(21f / 64f, SailBanner.patternV(design / 2f, design), EPS);
        // the frayed foot stays bare: the design stops above the fray of the cloth's bottom block
        assertEquals(2f - 9f / 32f, SailBanner.clipDepth(2f, true), EPS);
        assertEquals(1.5f, SailBanner.clipDepth(1.5f, false), EPS);
        assertEquals(4f, SailBanner.designDepth(4f, false), EPS);
    }

    /**
     * The design reads right (its left edge on the viewer's left) from the front face: the face toward the bow, or on
     * land +z for a yard along x and +x for one along z. Checked by composing the view: a viewer at {@code front}
     * looking back at the cloth has {@code right = forward x up}; the yard end where the region's left edge lies
     * ({@code t} = 0 unflipped, 1 flipped) must project to the left.
     */
    @Test
    void theDesignReadsRightFromTheFront() {
        int[][] bows = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (boolean alongX : new boolean[] {true, false}) {
            for (int[] bow : bows) {
                double[] out = alongX ? new double[] {0, 0, 1} : new double[] {1, 0, 0};
                int toward = alongX ? bow[1] : bow[0];
                double f = toward < 0 ? -1 : 1;
                double[] forward = {-f * out[0], 0, -f * out[2]};
                // forward x up(0,1,0) = (-forward.z, 0, forward.x)
                double[] right = {-forward[2], 0, forward[0]};
                boolean flip = SailBanner.flipAcross(alongX, bow[0], bow[1]);
                double leftEdgeT = flip ? 1 : 0;
                double along = -1 + 2 * leftEdgeT; // the negative end at t = 0
                double[] at = alongX ? new double[] {along, 0, 0} : new double[] {0, 0, along};
                double screenX = at[0] * right[0] + at[2] * right[2];
                assertTrue(screenX < 0, "alongX " + alongX + ", bow " + bow[0] + "," + bow[1] + ": left edge on the right");
            }
        }
    }

    @Test
    void tintIsTheBannerBaseElseTheDyeElseTheCanvas() {
        assertEquals(SailTint.NONE, SailTint.clothTint(null, ItemStack.EMPTY, true, false));
        assertEquals(0xB02E26, SailTint.clothTint(DyeColor.RED, ItemStack.EMPTY, true, false));
        assertEquals(SailTint.NONE, SailTint.clothTint(DyeColor.WHITE, ItemStack.EMPTY, true, false), "white is the canvas");
        assertEquals(SailTint.NONE, SailTint.clothTint(DyeColor.RED, ItemStack.EMPTY, false, false), "dyeing off");
        ItemStack blue = new ItemStack(Items.BLUE_BANNER);
        assertEquals(SailTint.rgb(DyeColor.BLUE), SailTint.clothTint(DyeColor.RED, blue, true, true), "the banner wins");
        assertEquals(SailTint.rgb(DyeColor.BLUE), SailTint.clothTint(DyeColor.RED, blue, false, true));
        assertEquals(0xB02E26, SailTint.clothTint(DyeColor.RED, blue, true, false), "a hidden banner leaves the dye");
        for (DyeColor c : DyeColor.values()) {
            assertEquals(0, SailTint.rgb(c) & 0xFF000000, c + " has alpha bits");
        }
    }
}
