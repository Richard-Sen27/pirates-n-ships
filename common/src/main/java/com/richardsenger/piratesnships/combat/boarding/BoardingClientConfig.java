package com.richardsenger.piratesnships.combat.boarding;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/** Client config section {@code boarding_view} (BRD2): the boarding plank's placement preview. */
public final class BoardingClientConfig {

    private static final ConfigSection S = ModConfigs.client("boarding_view", "How laying a boarding plank looks on this client");

    public static final ConfigValue<Boolean> PREVIEW = S.bool("preview", true,
            "While holding a boarding plank, outline the run it would lay where you look (green), or the cell in the way (red)");

    private BoardingClientConfig() {
    }

    public static void init() {
    }
}
