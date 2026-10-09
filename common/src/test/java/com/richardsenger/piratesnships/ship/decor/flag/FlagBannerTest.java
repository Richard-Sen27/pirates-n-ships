package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.law.flag.FlagKind;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FLG2: which pattern layers a banner flag draws and where the banner's flag region lies on the rippled cloth. */
class FlagBannerTest {

    private static final float EPS = 1e-5f;
    private static HolderLookup.RegistryLookup<BannerPattern> patterns;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        patterns = VanillaRegistries.createLookup().lookupOrThrow(Registries.BANNER_PATTERN);
    }

    private static Holder<BannerPattern> pattern(net.minecraft.resources.ResourceKey<BannerPattern> key) {
        return patterns.getOrThrow(key);
    }

    // --- the layer list ---

    @Test
    void aPatternedBannerGivesItsLayersBottomToTop() {
        ItemStack banner = new ItemStack(Items.WHITE_BANNER);
        banner.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder()
                .add(pattern(BannerPatterns.STRAIGHT_CROSS), DyeColor.RED)
                .add(pattern(BannerPatterns.BORDER), DyeColor.BLACK).build());
        List<BannerPatternLayers.Layer> layers = FlagBanner.layers(FlagKind.CUSTOM, banner);
        assertEquals(2, layers.size());
        assertEquals(pattern(BannerPatterns.STRAIGHT_CROSS), layers.get(0).pattern());
        assertEquals(DyeColor.RED, layers.get(0).color());
        assertEquals(pattern(BannerPatterns.BORDER), layers.get(1).pattern());
        assertEquals(DyeColor.BLACK, layers.get(1).color());
    }

    @Test
    void aPlainBannerOrAnotherFlagHasNoLayers() {
        assertTrue(FlagBanner.layers(FlagKind.CUSTOM, new ItemStack(Items.RED_BANNER)).isEmpty(), "a plain banner is only its base colour");
        assertTrue(FlagBanner.layers(FlagKind.CUSTOM, ItemStack.EMPTY).isEmpty());
        ItemStack banner = new ItemStack(Items.BLUE_BANNER);
        banner.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder().add(pattern(BannerPatterns.SKULL), DyeColor.WHITE).build());
        assertTrue(FlagBanner.layers(FlagKind.NAVY, banner).isEmpty(), "only a custom flag draws banner layers");
        assertTrue(FlagBanner.layers(FlagKind.NONE, banner).isEmpty());
    }

    @Test
    void atMostSixteenLayersAreDrawnAsVanillaDoes() {
        BannerPatternLayers.Builder b = new BannerPatternLayers.Builder();
        for (int i = 0; i < 20; i++) b.add(pattern(i % 2 == 0 ? BannerPatterns.STRIPE_TOP : BannerPatterns.STRIPE_BOTTOM), DyeColor.byId(i % 16));
        ItemStack banner = new ItemStack(Items.GREEN_BANNER);
        banner.set(DataComponents.BANNER_PATTERNS, b.build());
        List<BannerPatternLayers.Layer> layers = FlagBanner.layers(FlagKind.CUSTOM, banner);
        assertEquals(FlagBanner.MAX_LAYERS, layers.size());
        assertEquals(DyeColor.byId(0), layers.get(0).color());
        assertEquals(DyeColor.byId(15), layers.get(15).color());
    }

    // --- the mapping ---

    private static void assertTexel(float expectedXPx, float expectedYPx, float along, float height, boolean upright) {
        assertEquals(expectedXPx / 64f, FlagBanner.patternU(along, height, upright), EPS,
                "u at along " + along + ", height " + height + (upright ? " upright" : " sideways"));
        assertEquals(expectedYPx / 64f, FlagBanner.patternV(along, height, upright), EPS,
                "v at along " + along + ", height " + height + (upright ? " upright" : " sideways"));
    }

    @Test
    void sidewaysTheBannersTopIsAtTheHoistAndItsLengthAlongTheFly() {
        float s = FlagBanner.DESIGN_START, e = FlagBanner.DESIGN_END;
        // the banner's top edge (y 1) at the hoist; its bottom edge (y 41) at the fly
        assertTexel(1, 1, s, 0f, false);
        assertTexel(21, 1, s, 1f, false);
        assertTexel(1, 41, e, 0f, false);
        assertTexel(21, 41, e, 1f, false);
        // halfway along the fly is halfway down the banner, halfway up the cloth is the banner's middle
        assertTexel(11, 21, (s + e) / 2f, 0.5f, false);
    }

    @Test
    void uprightTheBannersTopIsAtTheTopAndItsLeftEdgeAtTheHoist() {
        float s = FlagBanner.DESIGN_START, e = FlagBanner.DESIGN_END;
        assertTexel(1, 1, s, 1f, true);
        assertTexel(21, 1, e, 1f, true);
        assertTexel(1, 41, s, 0f, true);
        assertTexel(21, 41, e, 0f, true);
        assertTexel(11, 21, (s + e) / 2f, 0.5f, true);
    }

    /**
     * The orientation of the design as seen on a face: the determinant of how the banner's own right and up (texture
     * x, minus texture y) change with the viewer's right and up. Positive: the banner reads as itself (perhaps turned);
     * negative: mirrored. On the front face (seen from +X) the viewer's right runs from the hoist to the fly; on the
     * back face (seen from -X) it runs from the fly to the hoist, and the faces share the texels of each place.
     */
    private static float handedness(boolean back, boolean upright) {
        float a = (FlagBanner.DESIGN_START + FlagBanner.DESIGN_END) / 2f, h = 0.5f, d = 0.01f;
        float right = back ? -d : d;
        float dxdr = (FlagBanner.patternU(a + right, h, upright) - FlagBanner.patternU(a, h, upright)) / d;
        float dydr = -(FlagBanner.patternV(a + right, h, upright) - FlagBanner.patternV(a, h, upright)) / d;
        float dxdu = (FlagBanner.patternU(a, h + d, upright) - FlagBanner.patternU(a, h, upright)) / d;
        float dydu = -(FlagBanner.patternV(a, h + d, upright) - FlagBanner.patternV(a, h, upright)) / d;
        return dxdr * dydu - dxdu * dydr;
    }

    @Test
    void theFrontReadsTrueAndTheBackMirroredInBothOrientations() {
        for (boolean upright : new boolean[]{false, true}) {
            assertTrue(handedness(false, upright) > 0, "the front must not be mirrored" + (upright ? " (upright)" : ""));
            assertTrue(handedness(true, upright) < 0, "the back must be mirrored like a real flag" + (upright ? " (upright)" : ""));
        }
    }

    @Test
    void sidewaysIsAQuarterTurnAnticlockwiseOnTheFront() {
        // turning the upright banner anticlockwise sends its top to the left (hoist) and its right edge to the top
        float a = (FlagBanner.DESIGN_START + FlagBanner.DESIGN_END) / 2f;
        float up = FlagBanner.patternU(a, 0.75f, false) - FlagBanner.patternU(a, 0.25f, false);
        float toFly = FlagBanner.patternV(a + 0.1f, 0.5f, false) - FlagBanner.patternV(a, 0.5f, false);
        assertTrue(up > 0, "up the cloth is toward the banner's right edge");
        assertTrue(toFly > 0, "toward the fly is down the banner");
        assertEquals(0f, FlagBanner.patternV(a, 0.75f, false) - FlagBanner.patternV(a, 0.25f, false), EPS);
    }

    // --- the strips ---

    @Test
    void theDesignSpansTheClothBetweenTheHeadingTapeAndTheFray() {
        assertEquals(3f / 24f, FlagBanner.DESIGN_START, EPS);
        assertEquals(23f / 24f, FlagBanner.DESIGN_END, EPS);
        float covered = FlagBanner.DESIGN_START;
        for (int k = 0; k < FlagRipple.STRIPS; k++) {
            float a0 = FlagBanner.stripStart(k), a1 = FlagBanner.stripEnd(k);
            if (a1 <= a0) {
                assertTrue(FlagRipple.along(k + 1) <= FlagBanner.DESIGN_START + EPS || FlagRipple.along(k) >= FlagBanner.DESIGN_END - EPS,
                        "strip " + k + " skipped inside the design");
                continue;
            }
            assertEquals(covered, a0, EPS, "strip " + k + " must start where the last one ended");
            float w0 = FlagBanner.weight(k, a0), w1 = FlagBanner.weight(k, a1);
            assertTrue(w0 >= -EPS && w1 <= 1 + EPS && w0 < w1, "strip " + k + " design must lie on the strip");
            covered = a1;
        }
        assertEquals(FlagBanner.DESIGN_END, covered, EPS, "the design must reach the fray");
    }

    @Test
    void stripWeightsMeetTheBoundaries() {
        for (int k = 0; k < FlagRipple.STRIPS; k++) {
            assertEquals(0f, FlagBanner.weight(k, FlagRipple.along(k)), EPS);
            assertEquals(1f, FlagBanner.weight(k, FlagRipple.along(k + 1)), EPS);
        }
    }
}
