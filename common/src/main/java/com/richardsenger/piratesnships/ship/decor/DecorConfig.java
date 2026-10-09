package com.richardsenger.piratesnships.ship.decor;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/** Server config section {@code ship_decor} (docs/design.md §4.8, §17): the little behaviour the decor blocks have. */
public final class DecorConfig {

    private static final ConfigSection SECTION = ModConfigs.server("ship_decor",
            "Ship decor: lanterns, ship's bell, rope coils, stern windows, chart table, sea cot");

    public static final ConfigValue<Boolean> SEA_COT_SLEEPING = SECTION.bool("sea_cot_sleeping", true,
            "Players can sleep in a sea cot and set their spawn there like in a bed (on an assembled ship only with sea_cot_sleeping_aboard as well)");
    public static final ConfigValue<Boolean> SEA_COT_SLEEPING_ABOARD = SECTION.bool("sea_cot_sleeping_aboard", true,
            "Players can sleep in a sea cot on an assembled ship: they lie in it as the ship sails, count for skipping the night and respawn on the ship. Needs sea_cot_sleeping");
    public static final ConfigValue<Integer> BELL_RING_TICKS = SECTION.intRange("bell_ring_ticks", 20, 2, 200,
            "How long the ship's bell swings after it is rung, in ticks (20 ticks = 1 second)");

    // BELL1: how the ship's bell is drawn on this client
    private static final ConfigSection BELL_VISUALS = ModConfigs.client("bell_visuals", "How the ship's bell looks on this client");

    public static final ConfigValue<Boolean> BELL_SWING_ENABLED = BELL_VISUALS.bool("enabled", true,
            "Swing the ship's bell about its yoke when it is rung (off = it hangs still)");
    public static final ConfigValue<Double> BELL_SWING_DEGREES = BELL_VISUALS.doubleRange("swing_degrees", 20.0, 0.0, 30.0,
            "How far the ship's bell swings out on its first swing after a strike from the front or back, in degrees");

    private DecorConfig() {
    }

    public static void init() {
    }
}
