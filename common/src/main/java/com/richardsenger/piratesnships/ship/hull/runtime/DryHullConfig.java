package com.richardsenger.piratesnships.ship.hull.runtime;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/** Server config section {@code dry_hull} (docs/design.md §4.3-§4.5, §17). Read only through these values. */
public final class DryHullConfig {

    private static final ConfigSection SECTION = ModConfigs.server("dry_hull",
            "Dry hulls below the waterline: water occlusion, hull runtime and the buoyancy correction");

    public static final ConfigValue<Boolean> ENABLED = SECTION.bool("enabled", true,
            "Keep the inside of closed hulls free of water (no water rendered, no swimming below deck)");
    public static final ConfigValue<Boolean> PARTIAL_BLOCKS = SECTION.bool("partial_blocks", true,
            "Also keep the empty part of slabs, stairs, trapdoors, hatches and doors free of water when it faces the dry inside "
                    + "and no sea (a deck hatch counts as inside the ship)");
    public static final ConfigValue<Boolean> DRY_BUOYANCY = SECTION.bool("dry_buoyancy", true,
            "The dry air inside a hull below the waterline lifts the ship");
    public static final ConfigValue<Double> DRY_BUOYANCY_SCALE = SECTION.doubleRange("dry_buoyancy_scale", 1.0, 0.0, 10.0,
            "Multiplier on the lift of the submerged dry volume (1.0 = Sable's own float force per block)");
    public static final ConfigValue<Boolean> FLOOD_WEIGHT = SECTION.bool("flood_weight", true,
            "Water inside a hull weighs the ship down");
    public static final ConfigValue<Double> FLOOD_WEIGHT_SCALE = SECTION.doubleRange("flood_weight_scale", 1.0, 0.0, 10.0,
            "Multiplier on the weight of flood water (1.0 = Sable's float force per block)");
    public static final ConfigValue<Integer> REGION_REBUILD_TICKS = SECTION.intRange("region_rebuild_ticks", 10, 1, 200,
            "Least ticks between two rebuilds of a ship's dry regions while it floods");
    public static final ConfigValue<Integer> SYNC_CHECK_TICKS = SECTION.intRange("sync_check_ticks", 5, 1, 100,
            "How often (ticks) the server looks for players who started seeing a ship and sends them its dry regions");
    public static final ConfigValue<Integer> SAVE_INTERVAL_TICKS = SECTION.intRange("save_interval_ticks", 40, 1, 1200,
            "How often (ticks) a changing flood state is written into the ship's saved data");
    public static final ConfigValue<Integer> SEA_PROBE_HEIGHT = SECTION.intRange("sea_probe_height", 24, 1, 128,
            "How far up from the hull's bottom to follow the water that touches it when finding the sea surface");
    public static final ConfigValue<Boolean> ASYNC_ANALYSIS = SECTION.bool("async_analysis", true,
            "Run hull re-analysis on a background thread (the first analysis after assembly or loading is synchronous)");
    public static final ConfigValue<Boolean> FLOOD_BREATH = SECTION.bool("flood_breath", true,
            "Breath runs out below the flood water inside a ship's rooms, also where the room still counts as dry or the "
                    + "flood stands higher than the sea");


    /**
     * Client section {@code dry_hull_view} (HV1): how dry hulls look on this client. Declared on both sides so datagen and
     * the config screen see it; only the client reads it. A separate name because server and client sections share
     * their lang keys.
     */
    private static final ConfigSection VIEW = ModConfigs.client("dry_hull_view", "How the inside of dry hulls looks on this client");

    public static final ConfigValue<Boolean> HIDE_WATER_PLANTS = VIEW.bool("hide_water_plants", true,
            "Do not draw seagrass, kelp, sea pickles and bubble columns of the world where they stand inside a dry hull "
                    + "(block tag pirates_n_ships:hidden_in_dry_hull)");
    public static final ConfigValue<Integer> HIDDEN_PLANTS_REFRESH_TICKS = VIEW.intRange("hidden_plants_refresh_ticks", 5, 1, 100,
            "How often (ticks) the hidden water plants follow a moving ship; lower is quicker but redraws more chunk sections");
    public static final ConfigValue<Boolean> FLOOD_SURFACE = VIEW.bool("flood_surface", true,
            "Draw a water surface inside flooded rooms of a ship, and the underwater view below it");

    private DryHullConfig() {
    }

    public static void init() {
    }

    public static HullBuoyancy.Params buoyancy() {
        return new HullBuoyancy.Params(DRY_BUOYANCY.get(), DRY_BUOYANCY_SCALE.get(), FLOOD_WEIGHT.get(),
                FLOOD_WEIGHT_SCALE.get(), HullBuoyancy.SABLE_FLOAT_FORCE);
    }
}
