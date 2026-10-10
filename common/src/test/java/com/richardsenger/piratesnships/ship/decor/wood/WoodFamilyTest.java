package com.richardsenger.piratesnships.ship.decor.wood;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.richardsenger.piratesnships.ship.decor.wood.client.WoodModels;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The family's pure rules (WOOD1): names, legacy ids, child models, renderer part ids, family ids, conditions. */
class WoodFamilyTest {

    private static final ResourceLocation EDGE = ResourceLocation.parse("pirates_n_ships:block/railing_edge");

    @Test
    void membersAreNamedWoodThenSuffix() {
        assertEquals("oak_railing", WoodFamily.name(ShipWood.OAK, "railing"));
        assertEquals("dark_oak_railing_port", WoodFamily.name(ShipWood.DARK_OAK, "railing_port"));
        assertEquals("biomesoplenty_redwood_railing", WoodFamily.name(ShipWood.byId("biomesoplenty_redwood"), "railing"));
    }

    @Test
    void aLegacyMemberKeepsItsOldIdAndOnlyThatOne() {
        assertEquals("helm", WoodFamily.memberName(ShipWood.DARK_OAK, "helm", ShipWood.DARK_OAK));
        assertEquals("oak_helm", WoodFamily.memberName(ShipWood.OAK, "helm", ShipWood.DARK_OAK));
        assertEquals("spruce_helm", WoodFamily.memberName(ShipWood.SPRUCE, "helm", ShipWood.DARK_OAK));
        assertEquals("dark_oak_railing", WoodFamily.memberName(ShipWood.DARK_OAK, "railing", null));
    }

    @Test
    void childModelsSetPlanksLogAndLogTopAndTheChosenParticle() {
        assertEquals(ResourceLocation.parse("pirates_n_ships:block/spruce_railing_edge"), WoodFamily.child(ShipWood.SPRUCE, EDGE));
        assertEquals(ResourceLocation.parse("pirates_n_ships:block/twilightforest_dark_railing_edge"),
                WoodFamily.child(ShipWood.byId("twilightforest_dark"), EDGE));
        JsonObject planks = WoodFamily.childJson(ShipWood.CHERRY, EDGE).getAsJsonObject("textures");
        assertEquals("minecraft:block/cherry_planks", planks.get(WoodFamily.PLANKS).getAsString());
        assertEquals("minecraft:block/stripped_cherry_log", planks.get(WoodFamily.LOG).getAsString());
        assertEquals("minecraft:block/stripped_cherry_log_top", planks.get(WoodFamily.LOG_TOP).getAsString());
        assertEquals("minecraft:block/cherry_planks", planks.get("particle").getAsString());
        JsonObject log = WoodFamily.childJson(ShipWood.CHERRY, EDGE, WoodFamily.Particle.LOG).getAsJsonObject("textures");
        assertEquals("minecraft:block/stripped_cherry_log", log.get("particle").getAsString());
        assertEquals(EDGE.toString(), WoodFamily.childJson(ShipWood.CHERRY, EDGE).get("parent").getAsString());
    }

    @Test
    void rendererPartsLoadOneChildPerWood() {
        ResourceLocation wheel = ResourceLocation.parse("pirates_n_ships:block/helm_wheel");
        List<ResourceLocation> ids = WoodModels.ids(ShipWood.VANILLA, wheel);
        assertEquals(11, ids.size());
        assertEquals(ResourceLocation.parse("pirates_n_ships:block/oak_helm_wheel"), ids.get(0));
        assertEquals(ResourceLocation.parse("pirates_n_ships:block/warped_helm_wheel"), ids.get(10));
        assertEquals(Set.copyOf(ids).size(), ids.size());
    }

    @Test
    void familyRuleMapsEveryWoodAndTheLegacyIdToOneMember() {
        assertEquals("railing", WoodIds.suffixOf("dark_oak_railing"));
        assertEquals("railing_port", WoodIds.suffixOf("oak_railing_port"));
        assertEquals("oar_bench", WoodIds.suffixOf("biomesoplenty_redwood_oar_bench"));
        assertNull(WoodIds.suffixOf("helm"));
        assertNull(WoodIds.suffixOf("oak"));
        Set<String> ids = Set.of("oak_railing", "spruce_railing", "helm", "oak_helm", "spruce_helm", "rope");
        assertEquals("oak_railing", WoodIds.representative("spruce_railing", ids::contains));
        assertEquals("oak_railing", WoodIds.representative("oak_railing", ids::contains));
        assertEquals("helm", WoodIds.representative("spruce_helm", ids::contains), "the legacy member stands for its family");
        assertEquals("helm", WoodIds.representative("oak_helm", ids::contains));
        assertEquals("helm", WoodIds.representative("helm", ids::contains));
        assertEquals("rope", WoodIds.representative("rope", ids::contains));
        assertEquals("cherry_thing", WoodIds.representative("cherry_thing", ids::contains), "no lead: itself");
    }

    @Test
    void conditionsCoverBothLoaders() {
        JsonObject json = new JsonObject();
        WoodConditions.add(json, "biomesoplenty", List.of(ResourceLocation.parse("biomesoplenty:fir_planks")));
        JsonArray neo = json.getAsJsonArray(WoodConditions.NEOFORGE);
        assertEquals("neoforge:mod_loaded", neo.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("biomesoplenty", neo.get(0).getAsJsonObject().get("modid").getAsString());
        assertEquals("neoforge:item_exists", neo.get(1).getAsJsonObject().get("type").getAsString());
        JsonArray fabric = json.getAsJsonArray(WoodConditions.FABRIC);
        assertEquals("fabric:all_mods_loaded", fabric.get(0).getAsJsonObject().get("condition").getAsString());
        assertEquals("fabric:registry_contains", fabric.get(1).getAsJsonObject().get("condition").getAsString());
        JsonObject loot = new JsonObject();
        WoodConditions.add(loot, "quark", List.of());
        assertEquals(1, loot.getAsJsonArray(WoodConditions.NEOFORGE).size());
        assertEquals(1, loot.getAsJsonArray(WoodConditions.FABRIC).size());
    }

    @Test
    void standInsAreReplacedEverywhereInAJsonTree() {
        var json = JsonParser.parseString("{\"key\":{\"P\":{\"item\":\"minecraft:barrier\"}},\"list\":[\"minecraft:barrier\",\"minecraft:stick\"]}");
        var out = WoodRecipe.replace(json, Map.of("minecraft:barrier", "biomesoplenty:fir_planks"));
        assertEquals("{\"key\":{\"P\":{\"item\":\"biomesoplenty:fir_planks\"}},\"list\":[\"biomesoplenty:fir_planks\",\"minecraft:stick\"]}", out.toString());
        assertTrue(WoodRecipe.STAND_INS.size() == 3);
    }
}
