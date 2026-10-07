package com.richardsenger.piratesnships.survival;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.survival.cold.ColdWater;
import com.richardsenger.piratesnships.survival.swim.SwimHunger;
import com.richardsenger.piratesnships.trade.content.TradeContent;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.List;

/**
 * The {@code survival} module (docs/design.md §14): cold water and swimming hunger. Owns the server section
 * {@code survival} ({@link SurvivalConfig}).
 *
 * <ul>
 *   <li><b>Cold water</b> ({@code survival.cold}): living entities in the water of a {@code #pirates_n_ships:cold_water}
 *       biome fill vanilla's freezing meter as in powder snow (overlay, slowdown, freezing damage). Exempt: riding
 *       (boats), standing on a ship, leather armour (vanilla's {@code canFreeze}), creative and spectator players,
 *       water creatures and mobs that breathe under water, and the {@code pirates_n_ships:warm} effect, which
 *       finishing a drink in {@code #pirates_n_ships:warming} (rum) grants.</li>
 *   <li><b>Swimming hunger</b> ({@code survival.swim}): moving in water costs {@code swim_exhaustion_multiplier}
 *       times vanilla's exhaustion.</li>
 * </ul>
 */
public final class SurvivalModule implements ModModule {

    /** Vanilla biomes in {@code #pirates_n_ships:cold_water}; the tag also includes {@code #c:is_cold} if present. */
    public static final List<ResourceKey<Biome>> COLD_BIOMES = List.of(
            Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN, Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN, Biomes.FROZEN_RIVER);
    /** The convention tag of cold biomes (NeoForge and Fabric 1.21.1), referenced optionally. */
    public static final String C_IS_COLD = "#c:is_cold";

    @Override
    public String id() {
        return "survival";
    }

    @Override
    public void registerConfig() {
        SurvivalConfig.init();
    }

    @Override
    public void registerContent() {
        SurvivalContent.init();
    }

    @Override
    public void registerEvents() {
        CommonEvents.LEVEL_TICK_END.register(ColdWater::onLevelTick);
        CommonEvents.ITEM_USE_FINISH.register(ColdWater::onItemUseFinish);
        CommonEvents.PLAYER_TICK_END.register(SwimHunger::onPlayerTick);
        CommonEvents.SERVER_STOPPED.register(server -> SwimHunger.clear());
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang.add("effect." + SurvivalContent.WARM.id().getNamespace() + "." + SurvivalContent.WARM.id().getPath(), "Warm"));
        data.itemTags(tags -> tags.tag(SurvivalContent.WARMING).add(TradeContent.RUM.get()));
        // biome tags are a datapack registry, which the tag providers don't cover (DataContributions.tags)
        data.json(PackOutput.Target.DATA_PACK, "tags/worldgen/biome", SurvivalContent.COLD_WATER.location(), () -> {
            JsonArray values = new JsonArray();
            for (ResourceKey<Biome> biome : COLD_BIOMES) values.add(biome.location().toString());
            JsonObject optional = new JsonObject();
            optional.addProperty("id", C_IS_COLD);
            optional.addProperty("required", false);
            values.add(optional);
            JsonObject json = new JsonObject();
            json.add("values", values);
            return json;
        });
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(SurvivalGameTests.class);
    }
}
