package com.richardsenger.piratesnships.ship.decor.wood;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The wood list and the family's names (RAIL1): what RAIL2 widens and what every family's datagen relies on. */
class ShipWoodTest {

    @Test
    void elevenVanillaWoodsAndOakShips() {
        assertEquals(11, ShipWood.values().length);
        assertEquals(List.of(ShipWood.OAK), ShipWood.SHIPPED, "RAIL1 ships oak; RAIL2 switches to every wood");
        assertTrue(ShipWood.OAK.shipped());
        assertFalse(ShipWood.CHERRY.shipped());
    }

    @Test
    void namesAndTexturesFollowVanilla() {
        assertEquals("Dark Oak", ShipWood.DARK_OAK.displayName());
        assertEquals(ResourceLocation.parse("minecraft:block/dark_oak_planks"), ShipWood.DARK_OAK.planksTexture());
        assertEquals(ResourceLocation.parse("minecraft:block/stripped_oak_log"), ShipWood.OAK.logTexture());
        assertEquals(ResourceLocation.parse("minecraft:block/stripped_bamboo_block"), ShipWood.BAMBOO.logTexture());
        assertEquals(ResourceLocation.parse("minecraft:block/stripped_warped_stem"), ShipWood.WARPED.logTexture());
        assertEquals(ResourceLocation.parse("minecraft:block/bamboo_planks"), ShipWood.BAMBOO.planksTexture());
        assertFalse(ShipWood.CRIMSON.flammable());
        assertTrue(ShipWood.MANGROVE.flammable());
    }

    @Test
    void idsAreUniqueAndFamilyNamesFollowThem() {
        Set<String> ids = new HashSet<>();
        for (ShipWood w : ShipWood.values()) assertTrue(ids.add(w.id()), "twice: " + w.id());
        assertEquals("oak_railing", WoodFamily.name(ShipWood.OAK, "railing"));
        assertEquals("dark_oak_railing_port", WoodFamily.name(ShipWood.DARK_OAK, "railing_port"));
        assertEquals(ResourceLocation.parse("pirates_n_ships:block/spruce_railing_edge"),
                WoodFamily.child(ShipWood.SPRUCE, ResourceLocation.parse("pirates_n_ships:block/railing_edge")));
    }
}
