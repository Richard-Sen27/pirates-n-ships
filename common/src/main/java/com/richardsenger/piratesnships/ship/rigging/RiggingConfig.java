package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/** Server config section {@code rigging} (docs/design.md §4.8, §17): the ratlines (RL1). */
public final class RiggingConfig {

    private static final ConfigSection S = ModConfigs.server("rigging", "Rigging blocks on masts: ratlines");

    public static final ConfigValue<Boolean> RATLINES_ENABLED = S.bool("ratlines_enabled", true,
            "Ratlines can be placed. Off = the item places nothing; ratlines already placed stay and can be climbed");

    private RiggingConfig() {
    }

    public static void init() {
    }
}
