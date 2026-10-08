package com.richardsenger.piratesnships.worldsim.raid;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.worldsim.WorldSimConfig;
import net.minecraft.server.MinecraftServer;

/**
 * Server config {@code world_simulation.raids} (WS5, design.md §10.4, §17). The chance values
 * ({@code chance_growth_per_minute}, {@code chance_cap}, {@code cooldown_days}) and the switch
 * {@code world_simulation.retaliation_enabled} were declared ahead in {@link WorldSimConfig}; this class adds the rest
 * to the same section.
 */
public final class RaidConfig {

    private static final ConfigSection S = WorldSimConfig.RAIDS;

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Pirates raid navy settlements while players stay there. Off = no raids start (raids under way go on)");
    public static final ConfigValue<Integer> SHIPS = S.intRange("ships", 1, 1, 2,
            "Pirate ships that sail in one raid");
    public static final ConfigValue<Integer> APPROACH_DISTANCE = S.intRange("approach_distance", 320, 32, 2048,
            "Blocks out at sea from the settlement's berth at which the raiders appear");
    public static final ConfigValue<Integer> LANDING_DISTANCE = S.intRange("landing_distance", 24, 4, 128,
            "Within this many blocks of the berth the raiders anchor and their fighters go ashore");
    public static final ConfigValue<Integer> BELL_TICKS = S.intRange("bell_ticks", 600, 0, 12_000,
            "Ticks the settlement's bells ring (every 100 ticks) after a raid is sighted (0 = no bells)");
    public static final ConfigValue<Integer> RAID_DURATION_TICKS = S.intRange("raid_duration_ticks", 6000, 200, 72_000,
            "Ticks the raiders fight ashore before the survivors go back and the ships withdraw");
    public static final ConfigValue<Boolean> TARGET_VILLAGES = S.bool("target_villages", true,
            "Seafarer villages are raided too, not only navy outposts");
    public static final ConfigValue<Double> RETALIATION_FACTOR = S.doubleRange("retaliation_factor", 1.0, 0.0, 10.0,
            "With retaliation_enabled the raid chance growth is multiplied by 1 + this x the Navy-Pirates tension (0..1)");
    public static final ConfigValue<Boolean> ANNOUNCE = S.bool("announce", true,
            "Tell the players at the settlement when raiders are sighted and how the raid ended");

    private RaidConfig() {
    }

    /** Loads the class so the values above are declared in time. */
    public static void init() {
    }

    /** Whether new raids may start on their own (both toggles). */
    public static boolean active() {
        return WorldSimConfig.ENABLED.get() && ENABLED.get();
    }

    /** The chance parameters as configured now, with the given Navy-Pirates tension. */
    public static RaidRules.Params params(double tension) {
        return new RaidRules.Params(WorldSimConfig.RAID_CHANCE_GROWTH.get(), WorldSimConfig.RAID_CHANCE_CAP.get(),
                WorldSimConfig.RAID_COOLDOWN_DAYS.get(),
                RaidRules.multiplier(WorldSimConfig.RETALIATION_ENABLED.get(), RETALIATION_FACTOR.get(), tension));
    }

    /** {@link #params(double)} with the server's current tension. */
    public static RaidRules.Params params(MinecraftServer server) {
        return params(com.richardsenger.piratesnships.worldsim.faction.Factions.tension(server,
                com.richardsenger.piratesnships.law.flag.Faction.NAVY, com.richardsenger.piratesnships.law.flag.Faction.PIRATES));
    }
}
