package com.richardsenger.piratesnships.mob.village;

import com.richardsenger.piratesnships.trade.stall.StallKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** LIFE1: the outfit follows the role, the role follows the building; every outfit has its painted sheet. */
class VillagerOutfitTest {

    private static final Path TEXTURES = Path.of("src/main/resources/assets/pirates_n_ships/textures/entity");

    @Test
    void theBuildingPicksTheRoleAndSoTheOutfit() {
        assertEquals(List.of(VillageRole.FISHWIFE), ResidentPosts.rolesOf("house_fisher"));
        assertEquals(List.of(VillageRole.VILLAGER), ResidentPosts.rolesOf("house_small"));
        assertEquals(List.of(VillageRole.NET_MENDER), ResidentPosts.rolesOf("net_loft"));
        assertEquals(List.of(VillageRole.DOCKHAND), ResidentPosts.rolesOf("warehouse"));
        assertEquals(List.of(VillageRole.SHIPWRIGHT), ResidentPosts.rolesOf("slipway"));
        assertEquals(List.of(VillageRole.INNKEEPER), ResidentPosts.rolesOf("tavern"));
        assertEquals(List.of(VillageRole.LAMPLIGHTER), ResidentPosts.rolesOf("lighthouse"));
        assertEquals(List.of(VillageRole.VENDOR), ResidentPosts.rolesOf("fish_market"));
        assertEquals(List.of(VillageRole.VENDOR, VillageRole.VENDOR), ResidentPosts.rolesOf("street_square_market"));
        assertTrue(ResidentPosts.rolesOf("dock_head").isEmpty(), "the dock head keeps its harbor master only");
        assertTrue(ResidentPosts.rolesOf("chapel").isEmpty());
        assertEquals("villager_fishwife", VillagerOutfit.of(ResidentPosts.rolesOf("house_fisher").get(0)).texture());
    }

    @Test
    void everyRoleLivesSomewhere() {
        Set<VillageRole> placed = new HashSet<>();
        ResidentPosts.RESIDENTS.values().forEach(slots -> slots.forEach(s -> placed.add(s.role())));
        assertEquals(Set.of(VillageRole.values()), placed);
    }

    @Test
    void stallsSitWithTheirVendors() {
        assertEquals(StallKind.FISH, ResidentPosts.residentsOf("fish_market").get(0).stall());
        assertEquals(StallKind.PROVISIONS, ResidentPosts.residentsOf("street_square_market").get(0).stall());
        assertEquals(StallKind.CHANDLERY, ResidentPosts.residentsOf("street_square_market").get(1).stall());
        assertEquals(StallKind.SHIPWRIGHT, ResidentPosts.residentsOf("shipwright").get(0).stall());
        assertEquals(StallKind.SHIPWRIGHT, VillageRole.SHIPWRIGHT.defaultStall());
    }

    @ParameterizedTest
    @EnumSource(VillageRole.class)
    void everyOutfitHasItsOwnPaintedSheet(VillageRole role) throws IOException {
        VillagerOutfit outfit = VillagerOutfit.of(role);
        assertEquals(role, outfit.role());
        Path png = TEXTURES.resolve(outfit.texture() + ".png");
        assertTrue(Files.exists(png), "texture " + png);
        BufferedImage img = ImageIO.read(png.toFile());
        assertEquals(64, img.getWidth());
        assertEquals(64, img.getHeight());
        assertEquals("sailor", VillagerOutfit.geometry());
    }

    @Test
    void outfitsDifferFromRoleToRole() {
        Set<String> textures = new HashSet<>();
        Set<List<String>> layers = new HashSet<>();
        for (VillageRole role : VillageRole.values()) {
            VillagerOutfit o = VillagerOutfit.of(role);
            assertTrue(textures.add(o.texture()), "texture of " + role);
            assertTrue(layers.add(List.of(o.hat(), o.coat(), o.clothing())), "layers of " + role);
            assertFalse(o.clothing().isBlank());
        }
        assertNotEquals(VillageRole.VILLAGER.textureName(), VillageRole.VENDOR.textureName());
        assertEquals("villager", VillageRole.VILLAGER.textureName());
    }

    @Test
    void rolesRoundTripTheirIds() {
        for (VillageRole r : VillageRole.values()) {
            assertEquals(r, VillageRole.byId(r.id()).orElseThrow());
            assertEquals(r, VillageRole.byOrdinal(r.ordinal()));
        }
        assertEquals(VillageRole.VILLAGER, VillageRole.byOrdinal(99));
    }

    @Test
    void namesAreStableAndFishwivesAreWomen() {
        assertEquals(VillageNames.name(VillageRole.DOCKHAND, 42), VillageNames.name(VillageRole.DOCKHAND, 42));
        for (long seed = 0; seed < 50; seed++) {
            String first = VillageNames.name(VillageRole.FISHWIFE, seed).split(" ")[0];
            assertTrue(VillageNames.WOMEN.contains(first), first);
        }
    }
}
