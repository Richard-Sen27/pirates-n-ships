package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Client config section {@code flag_visuals} (VIS1a, docs/design.md §4.7, §17): how flags are drawn on this client.
 * Named {@code flag_visuals} because client and server sections share translation keys and the server already has
 * {@code flags}. Declared on both sides from {@code ShipDecorModule.registerConfig()} so datagen and the config screen
 * see it (no client classes here); only the client reads it.
 */
public final class FlagVisualsConfig {

    private static final ConfigSection S = ModConfigs.client("flag_visuals", "How flags are drawn on this client");

    public static final ConfigValue<Boolean> HOIST_ANIMATION = S.bool("hoist_animation", true,
            "While a flag is hoisted, struck, raised or taken down, draw the cloth running along the pole; off: it appears and vanishes at once");

    private FlagVisualsConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
