package com.richardsenger.piratesnships.ship.decor.wood;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The wood list (RAIL1, WOOD1): vanilla and compat woods, what ships, ids and textures every family's datagen relies on. */
class ShipWoodTest {

    @Test
    void elevenVanillaWoodsShipWithoutMods() {
        assertEquals(11, ShipWood.VANILLA.size());
        assertEquals(ShipWood.VANILLA, ShipWood.shippedFor(mod -> false, false), "no mods: the vanilla woods only");
        assertSame(ShipWood.OAK, ShipWood.VANILLA.get(0), "oak leads every family");
        for (ShipWood w : ShipWood.VANILLA) assertTrue(w.isVanilla(), w + " is vanilla");
    }

    @Test
    void compatWoodsShipOnlyWithTheirModAfterTheVanillaOnes() {
        List<ShipWood> bop = ShipWood.shippedFor("biomesoplenty"::equals, false);
        assertEquals(ShipWood.VANILLA, bop.subList(0, 11));
        assertTrue(bop.size() > 11, "Biomes O' Plenty adds woods");
        for (ShipWood w : bop.subList(11, bop.size())) {
            assertEquals("biomesoplenty", w.modId());
            assertTrue(w.id().startsWith("biomesoplenty_"), w.id());
        }
        List<String> ids = new ArrayList<>();
        for (ShipWood w : bop.subList(11, bop.size())) ids.add(w.id());
        List<String> sorted = new ArrayList<>(ids);
        sorted.sort(null);
        assertEquals(sorted, ids, "a mod's woods by name");
    }

    @Test
    void everythingOrdersCompatWoodsByModThenName() {
        List<ShipWood> all = ShipWood.all();
        assertEquals(11 + WoodCompat.WOODS.size(), all.size());
        assertEquals(all, ShipWood.shippedFor(mod -> true, false), "every mod loaded ships every wood");
        for (int i = 12; i < all.size(); i++) {
            ShipWood a = all.get(i - 1), b = all.get(i);
            int byMod = a.modId().compareTo(b.modId());
            assertTrue(byMod < 0 || byMod == 0 && a.id().compareTo(b.id()) < 0, a + " before " + b);
        }
        assertTrue(WoodCompat.WOODS.size() >= 90, "the table lists " + WoodCompat.WOODS.size() + " woods");
        Set<String> mods = new HashSet<>();
        for (ShipWood w : WoodCompat.WOODS) mods.add(w.modId());
        for (String required : List.of("biomesoplenty", "ars_nouveau", "twilightforest")) assertTrue(mods.contains(required), required);
        assertTrue(mods.size() >= 10, mods.toString());
    }

    @Test
    void idsAreUniqueAndCompatIdsCarryTheMod() {
        Set<String> ids = new HashSet<>();
        for (ShipWood w : ShipWood.all()) {
            assertTrue(ids.add(w.id()), "twice: " + w.id());
            assertTrue(w.id().matches("[a-z0-9_]+"), w.id());
            if (!w.isVanilla()) {
                assertTrue(w.id().startsWith(w.modId() + "_"), w.id());
                assertEquals(w.modId(), w.planksId().getNamespace());
                assertEquals(w.modId(), w.planksTexture().getNamespace());
                assertEquals(w.modId(), w.logTexture().getNamespace());
                assertEquals(w.modId(), w.logTopTexture().getNamespace());
            }
            assertFalse(w.displayName().isBlank());
        }
        assertSame(ShipWood.SPRUCE, ShipWood.byId("spruce"));
        assertNotNull(ShipWood.byId("biomesoplenty_redwood"));
        assertNull(ShipWood.byId("nonsense"));
    }

    @Test
    void namesTexturesAndLogsFollowVanilla() {
        assertEquals("Dark Oak", ShipWood.DARK_OAK.displayName());
        assertEquals(ResourceLocation.parse("minecraft:block/dark_oak_planks"), ShipWood.DARK_OAK.planksTexture());
        assertEquals(ResourceLocation.parse("minecraft:block/stripped_oak_log"), ShipWood.OAK.logTexture());
        assertEquals(ResourceLocation.parse("minecraft:block/stripped_oak_log_top"), ShipWood.OAK.logTopTexture());
        assertEquals(ResourceLocation.parse("minecraft:block/stripped_bamboo_block"), ShipWood.BAMBOO.logTexture());
        assertEquals(ResourceLocation.parse("minecraft:block/stripped_bamboo_block_top"), ShipWood.BAMBOO.logTopTexture());
        assertEquals(ResourceLocation.parse("minecraft:block/stripped_warped_stem"), ShipWood.WARPED.logTexture());
        assertEquals(ResourceLocation.parse("minecraft:block/stripped_crimson_stem_top"), ShipWood.CRIMSON.logTopTexture());
        assertEquals(ResourceLocation.parse("minecraft:bamboo_block"), ShipWood.BAMBOO.logId());
        assertEquals(ResourceLocation.parse("minecraft:stripped_bamboo_block"), ShipWood.BAMBOO.strippedLogId());
        assertEquals(ResourceLocation.parse("minecraft:crimson_stem"), ShipWood.CRIMSON.logId());
        assertEquals(ResourceLocation.parse("minecraft:stripped_cherry_log"), ShipWood.CHERRY.strippedLogId());
        assertEquals(ResourceLocation.parse("minecraft:mangrove_planks"), ShipWood.MANGROVE.planksId());
        assertFalse(ShipWood.CRIMSON.flammable());
        assertFalse(ShipWood.WARPED.flammable());
        assertTrue(ShipWood.MANGROVE.flammable());
        assertEquals(ShipWood.Sound.NETHER, ShipWood.WARPED.soundSet());
    }

    /** Every vanilla texture and block a wood names exists in the game's jar (on the test classpath). */
    @Test
    void vanillaTexturesAndBlocksExistInTheGame() throws Exception {
        for (ShipWood w : ShipWood.VANILLA) {
            for (ResourceLocation tex : List.of(w.planksTexture(), w.logTexture(), w.logTopTexture())) {
                assertExists("/assets/minecraft/textures/" + tex.getPath() + ".png");
            }
            for (ResourceLocation block : List.of(w.planksId(), w.logId(), w.strippedLogId())) {
                assertExists("/assets/minecraft/blockstates/" + block.getPath() + ".json");
            }
        }
    }

    @Test
    void compatTitleCase() {
        assertEquals("Palo Verde", WoodCompat.title("palo_verde"));
        assertEquals("Redwood", ShipWood.byId("biomesoplenty_redwood").displayName());
        assertEquals("Darkwood", ShipWood.byId("twilightforest_dark").displayName());
    }

    private static void assertExists(String path) throws Exception {
        try (InputStream in = ShipWoodTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is not in the game's jar");
        }
    }
}
