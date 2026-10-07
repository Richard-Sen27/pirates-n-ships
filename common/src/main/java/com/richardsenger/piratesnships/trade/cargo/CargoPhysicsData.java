package com.richardsenger.piratesnships.trade.cargo;

import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sable mass per {@code load} state of our containers (CW1, design.md §4.9). Writes one
 * {@code data/pirates_n_ships/physics_block_properties/<block>.json} per container in Sable's format (refs/sable wiki
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

    /** The containers and their profiles, by block id. */
    public static Map<ResourceLocation, CargoMass.Profile> profiles() {
        Map<ResourceLocation, CargoMass.Profile> m = new LinkedHashMap<>();
        m.put(ShipDecor.CARGO_CRATE.id(), CargoMass.CRATE);
        m.put(ShipDecor.CARGO_BARREL.id(), CargoMass.BARREL);
        m.put(CrewContent.PANTRY.id(), CargoMass.PANTRY);
        m.put(CrewContent.WATER_BARREL.id(), CargoMass.WATER_BARREL);
        return m;
    }

    public static void gather(DataContributions data) {
        profiles().forEach((id, profile) -> data.json(PackOutput.Target.DATA_PACK, "physics_block_properties", id, () -> json(id, profile)));
    }

    /** The definition for one block: base mass, then one override per {@code load} value above 0. */
    public static JsonObject json(ResourceLocation block, CargoMass.Profile profile) {
        JsonObject properties = new JsonObject();
        properties.addProperty("sable:mass", round(profile.mass(0)));
        JsonObject overrides = new JsonObject();
        for (int load = 1; load <= CargoMass.MAX_LOAD; load++) {
            JsonObject o = new JsonObject();
            o.addProperty("sable:mass", round(profile.mass(load)));
            overrides.add(CargoLoad.LOAD.getName() + "=" + load, o);
        }
        JsonObject json = new JsonObject();
        json.addProperty("selector", block.toString());
        json.addProperty("priority", 1001);
        json.add("properties", properties);
        json.add("overrides", overrides);
        return json;
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
