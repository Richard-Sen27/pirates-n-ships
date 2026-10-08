package com.richardsenger.piratesnships.world.wreck;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.world.WorldConfig;
import net.minecraft.data.PackOutput;

import java.util.List;

/**
 * Datagen of the wrecks' worldgen files (vanilla formats, raw JSON; WK1): the structure, its structure set and the
 * biome tag. Step {@code surface_structures} like vanilla's shipwrecks and ocean ruins (vanilla has no separate
 * underwater step), so kelp and seagrass of the vegetation step grow around the wreck afterwards; terrain adaptation
 * {@code none}, since the piece brings its own seabed row.
 */
public final class WreckData {

    /** Fixed salt of the structure set's random spread (never change it: it moves every wreck of existing worlds). */
    public static final int SALT = 731_552_049;
    public static final int DEPTH_BELOW_FLOOR = 1;

    /**
     * The pieces with their weights and water cover (art/README.md "Wrecks (ST5)": each piece's height, so its top
     * row stays under the sea surface).
     */
    public static final List<WreckPieceEntry> PIECES = List.of(
            new WreckPieceEntry(WreckKeys.CARGO_FIELD, 3, 4),
            new WreckPieceEntry(WreckKeys.MAST_STUMP, 3, 12),
            new WreckPieceEntry(WreckKeys.STERN, 2, 8),
            new WreckPieceEntry(WreckKeys.SUNKEN_SLOOP, 1, 10));

    private WreckData() {
    }

    public static void gather(DataContributions data) {
        data.json(PackOutput.Target.DATA_PACK, "worldgen/structure", WreckKeys.WRECK.location(), WreckData::structure);
        data.json(PackOutput.Target.DATA_PACK, "worldgen/structure_set", WreckKeys.WRECKS.location(), WreckData::structureSet);
        data.json(PackOutput.Target.DATA_PACK, "tags/worldgen/biome", WreckKeys.HAS_WRECK.location(), () -> {
            JsonArray values = new JsonArray();
            values.add("#minecraft:is_ocean");
            values.add("#minecraft:is_deep_ocean");
            JsonObject json = new JsonObject();
            json.add("values", values);
            return json;
        });
    }

    static JsonObject structure() {
        JsonObject json = new JsonObject();
        json.addProperty("type", WreckKeys.WRECK_TYPE.toString());
        json.addProperty("biomes", "#" + WreckKeys.HAS_WRECK.location());
        json.addProperty("step", "surface_structures");
        json.add("spawn_overrides", new JsonObject());
        json.addProperty("terrain_adaptation", "none");
        JsonArray pieces = new JsonArray();
        for (WreckPieceEntry piece : PIECES) {
            JsonObject entry = new JsonObject();
            entry.addProperty("template", piece.template().toString());
            entry.addProperty("weight", piece.weight());
            entry.addProperty("water_above", piece.waterAbove());
            pieces.add(entry);
        }
        json.add("pieces", pieces);
        json.addProperty("depth_below_floor", DEPTH_BELOW_FLOOR);
        return json;
    }

    static JsonObject structureSet() {
        JsonObject entry = new JsonObject();
        entry.addProperty("structure", WreckKeys.WRECK.location().toString());
        entry.addProperty("weight", 1);
        JsonArray structures = new JsonArray();
        structures.add(entry);
        JsonObject placement = new JsonObject();
        placement.addProperty("type", "minecraft:random_spread");
        placement.addProperty("salt", SALT);
        placement.addProperty("spacing", WorldConfig.WRECK.spacing().defaultValue());
        placement.addProperty("separation", WorldConfig.WRECK_SEPARATION.defaultValue());
        JsonObject json = new JsonObject();
        json.add("structures", structures);
        json.add("placement", placement);
        return json;
    }
}
