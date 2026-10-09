package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/** Server config section {@code rigging} (docs/design.md §4.8, §17): the ratlines (RL1) and the ratline nets (RL2). */
public final class RiggingConfig {

    private static final ConfigSection S = ModConfigs.server("rigging", "Rigging blocks on masts: ratlines and ratline nets");

    public static final ConfigValue<Boolean> RATLINES_ENABLED = S.bool("ratlines_enabled", true,
            "Ratlines can be placed. Off = the item places nothing; ratlines already placed stay and can be climbed");

    /** RL2: ratline nets strung between two fittings. */
    public static final ConfigValue<Boolean> NETS_ENABLED = S.bool("nets_enabled", true,
            "Ratline nets can be strung between a chainplate and a mast hoop. Off = the net item strings nothing; nets already strung stay and can be climbed");
    public static final ConfigValue<Double> NET_MAX_LENGTH = S.doubleRange("net_max_length", 12.0, 2.0, 32.0,
            "Longest ratline net, ring to ring [blocks]");
    public static final ConfigValue<Double> NET_MIN_PITCH = S.doubleRange("net_min_pitch", 30.0, 10.0, 90.0,
            "Flattest ratline net, its pitch above the horizontal [degrees]; flatter nets are refused (90 = vertical nets only)");
    public static final ConfigValue<Boolean> NET_ASSIST = S.bool("net_assist", true,
            "A player climbing a ratline net is carried along the net's line, so steep and diagonal nets climb like a ladder. Off = vanilla ladder climbing only (fine for vertical nets, jerky or stuck on diagonal ones)");

    private RiggingConfig() {
    }

    public static void init() {
    }
}
