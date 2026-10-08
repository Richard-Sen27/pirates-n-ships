package com.richardsenger.piratesnships.ship.decor;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/** Server config section {@code ship_decor} (docs/design.md §4.8, §17): the little behaviour the decor blocks have. */
public final class DecorConfig {

    private static final ConfigSection SECTION = ModConfigs.server("ship_decor",
            "Ship decor: lanterns, ship's bell, rope coils, stern windows, chart table, sea cot");

    public static final ConfigValue<Boolean> SEA_COT_SLEEPING = SECTION.bool("sea_cot_sleeping", true,
            "Players can sleep in a sea cot and set their spawn there like in a bed (never while the cot is on an assembled ship)");
    public static final ConfigValue<Integer> BELL_RING_TICKS = SECTION.intRange("bell_ring_ticks", 20, 2, 200,
            "How long the ship's bell swings after it is rung, in ticks (20 ticks = 1 second)");

    private DecorConfig() {
    }

    public static void init() {
    }
}
