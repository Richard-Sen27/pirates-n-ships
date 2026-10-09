package com.richardsenger.piratesnships.sailing.sail;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Client config section {@code sail_visuals} (VIS1b, docs/design.md §4.8 visual backlog (3), §17): how sail cloth moves
 * in the wind on this client. Render only, so a client section. Declared on both sides from
 * {@code SailingModule.registerConfig()} so datagen and the config screen see it (no client classes here); only the
 * sail renderers read it.
 */
public final class SailVisualsConfig {

    private static final ConfigSection S = ModConfigs.client("sail_visuals", "How sail cloth moves in the wind on this client");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Sails belly out while they draw, shake while they luff and hang slack in a calm; off: the fixed belly of before");
    public static final ConfigValue<Double> MAX_BELLY = S.doubleRange("max_belly", 0.6, 0.0, 1.5,
            "Deepest belly of a drawing sail with a 4-block drop, in blocks (scaled with the sail's drop, at most twice this)");
    public static final ConfigValue<Double> FLUTTER_AMPLITUDE = S.doubleRange("flutter_amplitude", 0.15, 0.0, 0.5,
            "Swing of a luffing sail's cloth in a full wind, in blocks");
    public static final ConfigValue<Integer> SEGMENTS = S.intRange("segments", 8, 2, 32,
            "Cloth grid: at least this many columns across a sail and half as many rows per block down");
    public static final ConfigValue<Boolean> BANNER_LAYERS = S.bool("banner_layers", true,
            "Draw the pattern layers of a banner hung on a square sail over its cloth; off: only the banner's base colour");

    private SailVisualsConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
