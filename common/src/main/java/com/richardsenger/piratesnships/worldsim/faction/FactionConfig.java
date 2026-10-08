package com.richardsenger.piratesnships.worldsim.faction;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.worldsim.WorldSimConfig;

/** Server config {@code world_simulation.factions} (design.md §10.4, §17; WS1). */
public final class FactionConfig {

    private static final ConfigSection S = WorldSimConfig.sub("factions",
            "Aggression, wealth and tension of the Navy, the Pirates and the Merchants");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Keep a faction state that deeds and world events shift and that decays every day. Off = the state stays as it is");
    public static final ConfigValue<Double> DECAY_PER_DAY = S.doubleRange("decay_per_day", 0.05, 0.0, 1.0,
            "How far aggression (toward 0.3) and tension (toward 0) move back each in-game day");
    public static final ConfigValue<Double> EVENT_SCALE = S.doubleRange("event_scale", 1.0, 0.0, 10.0,
            "Strength of world events (NPC kills, convoys, raids, lost patrols) on the faction state. 0 = ignored");
    public static final ConfigValue<Double> DEED_SCALE = S.doubleRange("deed_scale", 1.0, 0.0, 10.0,
            "Strength of the players' deeds (plunder, kills, turn-ins) on the faction state. 0 = ignored");
    public static final ConfigValue<Integer> MAX_WEALTH = S.intRange("max_wealth", 1_000_000, 0, Integer.MAX_VALUE,
            "Most wealth (abstract doubloons) a faction can hold");

    private FactionConfig() {
    }

    /** Loads the class so the values above are declared in time. */
    public static void init() {
    }

    /** Whether the faction state changes at all (this toggle and the world simulation's). */
    public static boolean active() {
        return WorldSimConfig.ENABLED.get() && ENABLED.get();
    }
}
