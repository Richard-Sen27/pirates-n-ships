package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code rigging} (docs/design.md §4.8, §17): the ratlines (RL1), the ratline nets (RL2) and the
 * boarding nets along the rail (RAIL5).
 */
public final class RiggingConfig {

    private static final ConfigSection S = ModConfigs.server("rigging", "Rigging blocks on masts: ratlines and ratline nets");

    public static final ConfigValue<Boolean> RATLINES_ENABLED = S.bool("ratlines_enabled", true,
            "Ratlines can be placed. Off = the item places nothing; ratlines already placed stay and can be climbed");

    /** RL2: ratline nets strung between two fittings. */
    public static final ConfigValue<Boolean> NETS_ENABLED = S.bool("nets_enabled", true,
            "Ratline nets can be strung between a chainplate and a mast hoop. Off = the net item strings nothing; nets already strung stay and can be climbed");
    public static final ConfigValue<Double> NET_MAX_LENGTH = S.doubleRange("net_max_length", 32.0, 1.0, 64.0,
            "Longest ratline net, ring to ring [blocks]");
    public static final ConfigValue<Double> NET_MIN_PITCH = S.doubleRange("net_min_pitch", 30.0, 10.0, 90.0,
            "Flattest ratline net, its pitch above the horizontal [degrees]; flatter nets are refused (90 = vertical nets only)");
    public static final ConfigValue<Boolean> NET_ASSIST = S.bool("net_assist", true,
            "A player climbing a ratline net is carried along the net's line, so steep and diagonal nets climb like a ladder. Off = vanilla ladder climbing only (fine for vertical nets, jerky or stuck on diagonal ones)");

    /** RL4: several strands to one mast hoop form a triangular fan. */
    public static final ConfigValue<Integer> NET_MAX_STRANDS = S.intRange("net_max_strands", 5, 1, 8,
            "Most ratline nets one mast hoop holds, each from its own chainplate; together they form one fan (a chainplate always holds one)");
    public static final ConfigValue<Double> NET_MAX_SPREAD = S.doubleRange("net_max_spread", 6.0, 1.0, 16.0,
            "Widest gap between the chainplates of two neighbouring nets on one hoop that is still netted in between [blocks]; wider ones stay separate nets");

    /** RAIL5: a boarding net strung along the rail between two chainplates on railing blocks. */
    public static final ConfigValue<Boolean> BOARDING_NETS = S.bool("boarding_nets", true,
            "A ratline net strung between two chainplates on top of railings along one rail edge hangs as a boarding net a block above the rail; a boarder crossing it from outboard is held, then climbs over, while the ship's own crew pass freely. Off = such nets are refused as too flat, and boarding nets already strung hold nobody");
    public static final ConfigValue<Integer> BOARDING_NET_LENGTH = S.intRange("boarding_net_length", 8, 2, 32,
            "Longest boarding net, chainplate to chainplate along the rail [blocks]");
    public static final ConfigValue<Integer> BOARDING_NET_HOLD = S.intRange("boarding_net_hold", 20, 0, 200,
            "Ticks a boarding net holds a boarder who crosses the rail from outboard (slowed like a cobweb at half strength), before it climbs over (0 = nobody is held)");
    public static final ConfigValue<Integer> BOARDING_NET_CUT_HITS = S.intRange("boarding_net_cut_hits", 3, 1, 20,
            "Cutlass hits that cut a boarding net down (it drops as one net item at its first chainplate); other tools and bare hands cannot break it");

    private RiggingConfig() {
    }

    public static void init() {
    }
}
