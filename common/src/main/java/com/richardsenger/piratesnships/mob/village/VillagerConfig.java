package com.richardsenger.piratesnships.mob.village;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.MobKind;

/**
 * Server config section {@code mobs.villagers} (LIFE1, docs/design.md §10.1, §17): the people of the seafarer
 * villages. {@code enabled} is the per-type toggle every mob has ({@link MobConfig#enabled}); this class adds the rest.
 */
public final class VillagerConfig {

    private static final ConfigSection S = MobConfig.VILLAGERS;

    public static final ConfigValue<Boolean> ROLES = S.bool("roles", true,
            "Each villager has the role of the building it lives in (fishwife, net mender, dockhand, shipwright, innkeeper, "
                    + "lamplighter, vendor) with its outfit, workstations and stall; off = every new villager is a plain villager");
    public static final ConfigValue<Boolean> ROUTINES = S.bool("routines", true,
            "Villagers keep a daily routine: work at their stations by day, sit in the tavern or on a bench at dusk, sleep "
                    + "in a bed at night; off = they stroll about like sailors");
    public static final ConfigValue<Double> VENDOR_PRICE_SCALE = S.doubleRange("vendor_price_scale", 1.0, 0.1, 10.0,
            "Multiplier on the fixed prices of the village stalls (wares that are no trade good of the port)");
    public static final ConfigValue<Double> VENDOR_BUYBACK = S.doubleRange("vendor_buyback", 0.5, 0.0, 1.0,
            "Share of a stall's fixed price a vendor pays when he buys a ware back");
    public static final ConfigValue<Integer> RESPAWN_DAYS = S.intRange("respawn_days", 3, 0, 365,
            "Days after a villager died or was hired away until a new one moves into the building (once its chunk is loaded)");
    public static final ConfigValue<Integer> MAX_LAMPS = S.intRange("max_lamps", 8, 1, 32,
            "Lamp posts on the lamplighter's round at dusk and at dawn");
    public static final ConfigValue<Integer> STUCK_TICKS = S.intRange("stuck_ticks", 200, 0, 2400,
            "Ticks a villager tries to walk to a station, seat or bed it cannot reach (a ladder, a crowd) before it steps "
                    + "there directly; 0 = never");
    public static final ConfigValue<Integer> HIRE_CONFIRM_TICKS = S.intRange("hire_confirm_ticks", 200, 20, 1200,
            "Ticks within which a second sneak-use on a villager hires them after the first named the fee");

    private VillagerConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code VillageLifeModule.registerConfig}. */
    public static void init() {
    }

    /** {@code mobs.villagers.enabled}: off = none is placed or respawns, existing ones disappear. */
    public static boolean enabled() {
        return MobConfig.enabled(MobKind.VILLAGER).get();
    }
}
