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

    /** FLG2: how a banner's design lies on the flag cloth ({@link FlagBanner}). */
    public static final ConfigValue<Boolean> BANNER_UPRIGHT = S.bool("banner_upright", false,
            "Draw a banner flag's design upright (its top at the top of the cloth, stretched along the fly); off: the banner hangs sideways from the pole, its top at the pole");

    private FlagVisualsConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
