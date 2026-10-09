package com.richardsenger.piratesnships.almanac.client;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Client config of the Almanac's screen (ALM3), section {@code almanac_screen} (the server owns {@code almanac}, and
 * a top-level name can be used only once). Touches no client classes, so it loads on a dedicated server too.
 */
public final class AlmanacScreenConfig {

    private static final ConfigSection S = ModConfigs.client("almanac_screen", "How the Captain's Almanac's book screen looks and sounds");

    public static final ConfigValue<Integer> PAGE_TURN_TICKS = S.intRange("page_turn_ticks", 8, 0, 40,
            "Length of the page-turn animation in ticks (0: pages change at once)");

    public static final ConfigValue<Boolean> PAGE_TURN_SOUND = S.bool("page_turn_sound", true,
            "Play the book's page-turn sound when a page turns");

    public static final ConfigValue<Double> SCALE = S.doubleRange("scale", 1.0, 0.5, 2.0,
            "Size of the book on screen (1.0: 292x180 GUI pixels); always shrunk to fit small windows");

    private AlmanacScreenConfig() {
    }

    /** Loads the class so the values above are declared in time. */
    public static void init() {
    }
}
