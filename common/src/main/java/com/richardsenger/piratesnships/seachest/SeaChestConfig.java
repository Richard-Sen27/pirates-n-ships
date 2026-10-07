package com.richardsenger.piratesnships.seachest;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code sea_chest} (docs/design.md §11, §17) and the client section {@code sea_chest_visuals}
 * (cosmetics). Declared on both sides from {@code SeaChestModule.registerConfig()}.
 */
public final class SeaChestConfig {

    private static final ConfigSection S = ModConfigs.server("sea_chest", "The sea chest: worn on the back and floating in water");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Sea chests can be worn on the back and float in water (off = a plain block item: no wearing, no floating; a worn chest is inert)");
    public static final ConfigValue<Double> WORN_SPEED_MULTIPLIER = S.doubleRange("worn_speed_multiplier", 0.6, 0.05, 1.0,
            "Walking speed while wearing a sea chest, as a factor of the normal speed");
    public static final ConfigValue<Double> SINK_PULL = S.doubleRange("sink_pull", 0.02, 0.0, 0.2,
            "Blocks per tick the worn chest drags its wearer down while in water (swimming up adds 0.04, sinking without input is about 0.005)");
    public static final ConfigValue<Double> DRIFT_FACTOR = S.doubleRange("drift_factor", 0.3, 0.0, 2.0,
            "A floating sea chest drifts with the wind at this fraction of the wind speed");
    public static final ConfigValue<Double> MAX_DRIFT_SPEED = S.doubleRange("max_drift_speed", 3.0, 0.0, 20.0,
            "Cap on a floating chest's wind drift [blocks per second]");
    public static final ConfigValue<Double> DRAFT = S.doubleRange("draft", 0.45, 0.1, 1.0,
            "Fraction of a floating chest's height under the waterline at rest");

    private static final ConfigSection C = ModConfigs.client("sea_chest_visuals", "How the floating sea chest looks");

    public static final ConfigValue<Double> BOB_AMPLITUDE = C.doubleRange("bob_amplitude", 0.04, 0.0, 0.5,
            "Height of the floating chest's bobbing on the water [blocks] (0 = still)");

    private SeaChestConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    public static WornRules.Params worn() {
        return new WornRules.Params(ENABLED.get(), WORN_SPEED_MULTIPLIER.get(), SINK_PULL.get());
    }

    public static FloatRules.Params floating() {
        return FloatRules.Params.of(DRAFT.get());
    }
}
