package com.richardsenger.piratesnships.station.capstan;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.station.StationConfig;

/** Server config of the capstan station (CRW3, docs/design.md §6, §17), section {@code crew_stations.capstan}. */
public final class CapstanConfig {

    private static final ConfigSection S = StationConfig.section("capstan",
            "A crew member at the capstan drops and raises the ship's anchor on order");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Crew members at a capstan carry out anchor orders. Off: the capstan takes no crew orders (players still use it)");
    public static final ConfigValue<Integer> DROP_TICKS = S.intRange("drop_ticks", 40, 1, 1200,
            "Ticks a crew member at the capstan needs to let the anchor go (knocking the pawls off) before it falls");
    public static final ConfigValue<Integer> MIN_RAISE_TICKS = S.intRange("min_raise_ticks", 20, 1, 1200,
            "Shortest work time of a raise order in ticks; otherwise it lasts as long as the capstan needs to wind the chain in "
                    + "(anchor_chain.raise_speed), and the crew member carries on while the anchor is still coming up");

    private CapstanConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
