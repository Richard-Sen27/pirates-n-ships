package com.richardsenger.piratesnships.guide;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/** Server config section {@code guide} (docs/design.md §17): the in-game guide book (GuideME, optional). */
public final class GuideConfig {

    private static final ConfigSection S = ModConfigs.server("guide", "The in-game guide book (needs the GuideME mod)");

    public static final ConfigValue<Boolean> GIVE_ON_FIRST_JOIN = S.bool("give_on_first_join", true,
            "A player who joins for the first time gets the guide book once (only while GuideME is installed)");

    private GuideConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
