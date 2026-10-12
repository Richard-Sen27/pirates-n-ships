package com.richardsenger.piratesnships.core.advancement;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/** Server config of the advancement tree (ADV1, {@code docs/advancements.md}). */
public final class AdvancementConfig {

    private static final ConfigSection SECTION = ModConfigs.server("advancements", "The mod's advancement tab");

    public static final ConfigValue<Boolean> ENABLED = SECTION.bool("enabled", true,
            "Fire the mod's own advancement criteria and pay their experience; off: no criterion of the mod fires and no experience is paid");

    public static final ConfigValue<Double> EXPERIENCE_SCALE = SECTION.doubleRange("experience_scale", 1.0, 0.0, 10.0,
            "Multiplier on the experience each advancement of the mod pays (0 pays none)");

    private AdvancementConfig() {
    }

    /** Called from {@code AdvancementModule.registerConfig()}. */
    public static void init() {
    }
}
