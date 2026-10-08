package com.richardsenger.piratesnships.crew.hammock;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.crew.CrewConfig;

/** Server config section {@code crew.hammock} (docs/design.md §7.1, §17): players in hammocks (SLP1). */
public final class HammockConfig {

    private static final ConfigSection S = CrewConfig.sub("hammock", "Hammocks: players sleeping in them (SLP1)");

    public static final ConfigValue<Boolean> PLAYER_SLEEP = S.bool("player_sleep", true,
            "Players can sleep in a free hammock at night, on land and on an assembled ship; it counts for skipping the night and sets the respawn point. Off = hammocks are for the crew only");

    private HammockConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
