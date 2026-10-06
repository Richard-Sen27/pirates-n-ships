package com.richardsenger.piratesnships.sailing.anchor;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config of the visible anchor ({@code anchor_chain}). The sounds are played by the server to every nearby
 * player, so their switch and volumes are server values too.
 */
public final class AnchorConfig {

    private static final ConfigSection S = ModConfigs.server("anchor_chain", "The visible anchor: chain travel, sounds and particles");

    public static final ConfigValue<Boolean> DEPTH_TRAVEL = S.bool("depth_travel_time", true,
            "Dropping and raising take as long as the chain needs for the depth. Off: the fixed sailing.anchor_drop_ticks and anchor_raise_ticks");
    public static final ConfigValue<Double> DROP_SPEED = S.doubleRange("drop_speed", 6.0, 0.1, 64.0,
            "Speed of the running chain while dropping, in blocks per second");
    public static final ConfigValue<Double> RAISE_SPEED = S.doubleRange("raise_speed", 2.5, 0.1, 64.0,
            "Speed of the chain while heaving the anchor in, in blocks per second");
    public static final ConfigValue<Integer> MIN_TRAVEL_TICKS = S.intRange("min_travel_ticks", 20, 1, 1200,
            "Shortest drop or raise, in ticks");
    public static final ConfigValue<Integer> MAX_TRAVEL_TICKS = S.intRange("max_travel_ticks", 400, 1, 6000,
            "Longest drop or raise, in ticks");
    public static final ConfigValue<Boolean> SOUNDS = S.bool("sounds", true,
            "Chain, splash and landing sounds and the splash particles of the anchor");
    public static final ConfigValue<Double> CHAIN_VOLUME = S.doubleRange("chain_volume", 0.8, 0.0, 4.0,
            "Volume of the running chain");
    public static final ConfigValue<Double> SPLASH_VOLUME = S.doubleRange("splash_volume", 1.0, 0.0, 4.0,
            "Volume of the splash when the anchor enters the water");
    public static final ConfigValue<Double> THUD_VOLUME = S.doubleRange("thud_volume", 1.0, 0.0, 4.0,
            "Volume of the anchor landing on the ground");

    private AnchorConfig() {
    }

    public static void init() {
        // class load registers the values
    }
}
