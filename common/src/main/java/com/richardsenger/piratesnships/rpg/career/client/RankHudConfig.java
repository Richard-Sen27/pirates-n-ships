package com.richardsenger.piratesnships.rpg.career.client;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Client config section {@code career_hud} (HON1, docs/design.md §15): the small rank box. Declared on both sides from
 * {@code CareerModule.registerConfig()} so datagen and the config screen see it (no client classes here); only the
 * client reads it.
 */
public final class RankHudConfig {

    private static final ConfigSection S = ModConfigs.client("career_hud", "The rank box: your career title, reputation and letter of marque");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Show the rank box: your navy rank or infamy, navy and pirate reputation, and your letter of marque");
    public static final ConfigValue<Integer> X = S.intRange("x", 4, -4096, 4096,
            "Horizontal position of the rank box in GUI pixels: from the left edge, or from the right edge when negative");
    public static final ConfigValue<Integer> Y = S.intRange("y", 4, -4096, 4096,
            "Vertical position of the rank box in GUI pixels: from the top edge, or from the bottom edge when negative");

    private RankHudConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
