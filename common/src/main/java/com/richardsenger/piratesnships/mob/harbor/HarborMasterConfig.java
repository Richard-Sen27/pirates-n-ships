package com.richardsenger.piratesnships.mob.harbor;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.MobKind;

/**
 * Server config section {@code mobs.harbor_master} (PRT1a, docs/design.md §10.3, §17): the harbor master behind each
 * port's desk. {@code enabled} is the per-type toggle every mob has ({@link MobConfig#enabled}); this class adds the
 * rest to the same section. Whether the desk block itself still opens the market is the trade module's
 * {@code cargo_trade.harbor_desks.direct_use}.
 */
public final class HarborMasterConfig {

    private static final ConfigSection S = MobConfig.HARBOR_MASTER;

    public static final ConfigValue<Boolean> AT_PIRATE_ISLANDS = S.bool("at_pirate_islands", true,
            "A harbor master (the fence) also stands behind the counter of each new pirate island camp");
    public static final ConfigValue<Integer> RESPAWN_DAYS = S.intRange("respawn_days", 3, 0, 365,
            "Days after a harbor master's death until a new one takes his post (once its chunk is loaded)");
    public static final ConfigValue<Double> RETURN_DISTANCE = S.doubleRange("return_distance", 2.0, 0.5, 32.0,
            "Blocks from his post beyond which an idle harbor master walks back to it; read when he is loaded");

    private HarborMasterConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code MobConfig.init()}. */
    public static void init() {
    }

    /** {@code mobs.harbor_master.enabled}: off = none is placed or respawns, existing ones disappear. */
    public static boolean enabled() {
        return MobConfig.enabled(MobKind.HARBOR_MASTER).get();
    }
}
