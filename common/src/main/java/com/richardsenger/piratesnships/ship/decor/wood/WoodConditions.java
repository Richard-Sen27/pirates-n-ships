package com.richardsenger.piratesnships.ship.decor.wood;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;

/**
 * The load conditions of a mod wood's data files (WOOD1, docs/woods.md), for both loaders in the same JSON: NeoForge
 * reads {@code neoforge:conditions} ({@code neoforge:mod_loaded}, {@code neoforge:item_exists}) and ignores the
 * Fabric key, Fabric API reads {@code fabric:load_conditions} ({@code fabric:all_mods_loaded},
 * {@code fabric:registry_contains}) and ignores the NeoForge key. Both loaders honour them on recipes, advancements
 * and loot tables (NeoForge: {@code ConditionalOps} in the recipe, advancement and loot data loaders; Fabric API:
 * {@code fabric-resource-conditions-api-v1}'s mixins into the same loaders). Pure: plain JSON.
 */
public final class WoodConditions {

    public static final String NEOFORGE = "neoforge:conditions";
    public static final String FABRIC = "fabric:load_conditions";

    private WoodConditions() {
    }

    /**
     * Adds both loaders' conditions to {@code json}: {@code modId} loaded and, when {@code items} is not empty, every
     * one of them registered (a mod can switch its woods off by config; then the recipe skips instead of failing).
     */
    public static void add(JsonObject json, String modId, Collection<ResourceLocation> items) {
        JsonArray neo = new JsonArray();
        JsonObject loaded = new JsonObject();
        loaded.addProperty("type", "neoforge:mod_loaded");
        loaded.addProperty("modid", modId);
        neo.add(loaded);
        for (ResourceLocation item : items) {
            JsonObject exists = new JsonObject();
            exists.addProperty("type", "neoforge:item_exists");
            exists.addProperty("item", item.toString());
            neo.add(exists);
        }
        JsonArray fabric = new JsonArray();
        JsonObject all = new JsonObject();
        all.addProperty("condition", "fabric:all_mods_loaded");
        JsonArray mods = new JsonArray();
        mods.add(modId);
        all.add("values", mods);
        fabric.add(all);
        if (!items.isEmpty()) {
            JsonObject contains = new JsonObject();
            contains.addProperty("condition", "fabric:registry_contains");
            contains.addProperty("registry", "minecraft:item");
            JsonArray values = new JsonArray();
            for (ResourceLocation item : items) values.add(item.toString());
            contains.add("values", values);
            fabric.add(contains);
        }
        json.add(NEOFORGE, neo);
        json.add(FABRIC, fabric);
    }
}
