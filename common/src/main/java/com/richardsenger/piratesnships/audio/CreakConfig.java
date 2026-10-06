package com.richardsenger.piratesnships.audio;

import com.richardsenger.piratesnships.audio.creak.CreakTrigger;
import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code hull_creaking} (docs/design.md §16, §17): when a rolling ship's planks creak. Server
 * side because the server decides when a ship creaks and every nearby player hears the same creak. Defaults come from
 * {@link CreakTrigger.Params#DEFAULTS}; {@link #params()} is the only adapter to the pure trigger.
 */
public final class CreakConfig {

    private static final CreakTrigger.Params D = CreakTrigger.Params.DEFAULTS;

    private static final ConfigSection S = ModConfigs.server("hull_creaking", "Creaking planks of rolling ships");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", D.enabled(),
            "A ship that rolls or pitches creaks now and then. Off: ships are silent");
    public static final ConfigValue<Double> CREAKS_PER_SECOND = S.doubleRange("creaks_per_second", D.creaksPerSecond(), 0.0, 20.0,
            "Average creaks per second of a ship that rolls just fast enough to creak; a ship rolling twice as fast creaks twice as often");
    public static final ConfigValue<Integer> MIN_INTERVAL_TICKS = S.intRange("min_interval_ticks", D.minIntervalTicks(), 1, 1200,
            "Shortest time between two creaks of one ship, in ticks");
    public static final ConfigValue<Double> MIN_VOLUME = S.doubleRange("min_volume", D.minVolume(), 0.0, 4.0,
            "Volume of the quietest creak");
    public static final ConfigValue<Double> MAX_VOLUME = S.doubleRange("max_volume", D.maxVolume(), 0.0, 4.0,
            "Volume of the loudest creak (raised to min_volume if lower)");
    public static final ConfigValue<Double> MIN_PITCH = S.doubleRange("min_pitch", D.minPitch(), 0.5, 2.0,
            "Lowest pitch of a creak (1 = the sound as recorded)");
    public static final ConfigValue<Double> MAX_PITCH = S.doubleRange("max_pitch", D.maxPitch(), 0.5, 2.0,
            "Highest pitch of a creak (raised to min_pitch if lower)");
    public static final ConfigValue<Double> ROLL_RATE_THRESHOLD = S.doubleRange("roll_rate_threshold", D.rollRateThreshold(), 0.001, 5.0,
            "How fast a ship must roll or pitch to creak, in radians per second (0.05 is about 3 degrees per second)");

    private CreakConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    /** Current creak tuning from the server config. */
    public static CreakTrigger.Params params() {
        return new CreakTrigger.Params(ENABLED.get(), CREAKS_PER_SECOND.get(), MIN_INTERVAL_TICKS.get(), MIN_VOLUME.get(),
                MAX_VOLUME.get(), MIN_PITCH.get(), MAX_PITCH.get(), ROLL_RATE_THRESHOLD.get());
    }
}
