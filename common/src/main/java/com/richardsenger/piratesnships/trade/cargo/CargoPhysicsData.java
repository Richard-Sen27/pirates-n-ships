package com.richardsenger.piratesnships.trade.cargo;

import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sable mass per {@code load} state of our containers (CW1, design.md §4.9). Writes one
 * {@code data/pirates_n_ships/physics_block_properties/<block>.json} per container family, selecting the family's
 * block tag (WOOD2, {@link #familyTag}) in Sable's format (refs/sable wiki
 * "Block Physics Properties"; codec {@code physics/config/block_properties/PhysicsBlockPropertiesDefinition.java}: a
 * {@code selector}, a {@code priority}, {@code properties} and block-state {@code overrides} such as {@code "load=2"},
 * parsed by {@code BlockStateConditionSet.parse}). Priority 1001 puts the file after Sable's own tag files (default
 * 1000), whose {@code #sable:light} / {@code #sable:heavy} masses these blocks also carry; the loader applies the
 * definitions in ascending priority and each one overwrites what it sets ({@code mixin/block_properties/BlockStateMixin.java}
 * {@code sable$loadProperties}, l.28-42), so ours wins.
 */
public final class CargoPhysicsData {

    private CargoPhysicsData() {
    }

    /** The four container families with their mass profiles (WOOD2: every wood's member weighs like the legacy one). */
    public static Map<WoodFamily<?>, CargoMass.Profile> families() {
        Map<WoodFamily<?>, CargoMass.Profile> m = new LinkedHashMap<>();
        m.put(ShipDecor.CARGO_CRATES, CargoMass.CRATE);
        m.put(ShipDecor.CARGO_BARRELS, CargoMass.BARREL);
        m.put(CrewContent.PANTRIES, CargoMass.PANTRY);
        m.put(CrewContent.WATER_BARRELS, CargoMass.WATER_BARREL);
        return m;
    }

    /**
     * The block tag of a family: every member, a mod's wood's as an optional entry ({@code cargo_crates},
     * {@code cargo_barrels}, {@code pantries}, {@code water_barrels}). It is the selector of the family's physics file:
     * Sable's loader applies no load conditions and logs an error for every unknown block id
     * ({@code PhysicsBlockPropertiesDefinitionLoader} l.31-45, l.71-77), so the mods' woods' members are never named by
     * id there; an absent mod only leaves its optional entries out of the tag.
     */
    public static TagKey<Block> familyTag(WoodFamily<?> family) {
        String suffix = family.suffix();
        String plural = suffix.equals("pantry") ? "pantries" : suffix + "s";
        return TagKey.create(Registries.BLOCK, Constants.id(plural));
    }

    /**
     * One file per family under its legacy member's id (the file names of CW1: {@code cargo_crate.json}, ...) whose
     * selector is {@link #familyTag}, and the tags.
     */
    public static void gather(DataContributions data) {
        families().forEach((family, profile) -> data.json(PackOutput.Target.DATA_PACK, "physics_block_properties",
                family.lead().id(), () -> json("#" + familyTag(family).location(), profile)));
        data.blockTags(tags -> families().keySet().forEach(family -> family.tag(tags, familyTag(family))));
    }

    /** The definition for one block. */
    public static JsonObject json(ResourceLocation block, CargoMass.Profile profile) {
        return json(block.toString(), profile);
    }

    /** The definition for {@code selector} (a block id or {@code #tag}): base mass, then one override per {@code load} value above 0. */
    public static JsonObject json(String selector, CargoMass.Profile profile) {
        JsonObject properties = new JsonObject();
        properties.addProperty("sable:mass", round(profile.mass(0)));
        JsonObject overrides = new JsonObject();
        for (int load = 1; load <= CargoMass.MAX_LOAD; load++) {
            JsonObject o = new JsonObject();
            o.addProperty("sable:mass", round(profile.mass(load)));
            overrides.add(CargoLoad.LOAD.getName() + "=" + load, o);
        }
        JsonObject json = new JsonObject();
        json.addProperty("selector", selector);
        json.addProperty("priority", 1001);
        json.add("properties", properties);
        json.add("overrides", overrides);
        return json;
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
