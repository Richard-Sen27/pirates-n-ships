package com.richardsenger.piratesnships.rpg.reputation;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;
import com.richardsenger.piratesnships.rpg.deeds.Deed;
import com.richardsenger.piratesnships.rpg.deeds.DeedTable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Server config section {@code reputation} (docs/design.md §15, §17). The pure rules never read these handles;
 * {@link #deedTable()} copies the deed deltas into a {@link DeedTable}.
 */
public final class ReputationConfig {

    private static final ConfigSection S = ModConfigs.server("reputation",
            "Reputation with the navy, the pirates and the villagers: deeds shift it, it changes prices and hostility");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Track reputation. Off = deeds change nothing, prices don't swing, villagers always trade, pirates and navy "
                    + "ignore reputation, and the false-flag rule and port fees use a navy standing of 0");
    public static final ConfigValue<Double> DECAY_PER_DAY = S.doubleRange("decay_per_day", 2.0, 0.0, 200.0,
            "Points every score moves toward 0 per in-game day (24000 ticks), spread evenly over the day");
    public static final ConfigValue<Double> PRICE_SWING = S.doubleRange("price_swing", 0.10, 0.0, 0.9,
            "Largest price change from reputation, as a share of the price: at +100 buying costs this much less and selling "
                    + "pays this much more, at -100 the reverse (villagers at village markets, pirates at fences)");
    public static final ConfigValue<Integer> PIRATE_FRIENDLY_THRESHOLD = S.intRange("pirate_friendly_threshold", 40, -100, 101,
            "Pirates leave a player whose pirate reputation is above this alone until the player attacks them (101 = never)");
    public static final ConfigValue<Integer> NAVY_HOSTILE_THRESHOLD = S.intRange("navy_hostile_threshold", -60, -101, 100,
            "The navy attacks a player whose navy reputation is below this on sight, even without a bounty (-101 = never)");
    public static final ConfigValue<Integer> VILLAGER_TRADE_THRESHOLD = S.intRange("villager_trade_threshold", -60, -101, 100,
            "Village markets refuse to trade with a player whose villager reputation is below this (-101 = never)");
    public static final ConfigValue<Integer> VILLAGE_TRADE_DAILY_CAP = S.intRange("village_trade_daily_cap", 5, 0, 1000,
            "Village trades per in-game day that count as the trade_village deed");
    public static final ConfigValue<Integer> ATTACK_REPEAT_SECONDS = S.intRange("attack_repeat_seconds", 30, 0, 3600,
            "Hitting the same navy, pirate or villager again within this many seconds is not another attack deed");
    public static final ConfigValue<Integer> SYNC_INTERVAL_TICKS = S.intRange("sync_interval_ticks", 40, 1, 1200,
            "How often (ticks) a player's shown reputation is checked and sent to their client if it changed");

    private static final ConfigSection DEEDS = S.section("deeds", "Reputation change per deed and faction (-100 to 100)");

    /** {@code reputation.deeds.<deed>.<faction>}. */
    public static final Map<Deed, Map<Faction, ConfigValue<Integer>>> DEED_DELTAS;

    static {
        Map<Deed, Map<Faction, ConfigValue<Integer>>> deeds = new EnumMap<>(Deed.class);
        for (Deed d : Deed.values()) {
            ConfigSection section = DEEDS.section(d.id(), "Reputation change for: " + d.id().replace('_', ' '));
            Map<Faction, ConfigValue<Integer>> row = new EnumMap<>(Faction.class);
            for (Faction f : Faction.values()) {
                row.put(f, section.intRange(f.id(), d.defaultDelta(f), -DeedTable.LIMIT, DeedTable.LIMIT,
                        "Change of the " + f.id() + " reputation"));
            }
            deeds.put(d, Collections.unmodifiableMap(row));
        }
        DEED_DELTAS = Collections.unmodifiableMap(deeds);
    }

    private ReputationConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code RpgModule.registerConfig()}. */
    public static void init() {
    }

    /** The deed deltas from the current config. */
    public static DeedTable deedTable() {
        return DeedTable.read((d, f) -> DEED_DELTAS.get(d).get(f).get());
    }
}
