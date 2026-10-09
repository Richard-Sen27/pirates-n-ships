package com.richardsenger.piratesnships.core.perf;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config of the performance test (PERF1). Read only by {@link PerformanceGameTests}, which runs only with the
 * system property {@code pirates_n_ships.perf=true}; nothing in a game reads it.
 */
public final class PerfConfig {

    private static final ConfigSection S = ModConfigs.server("perf", "The manual performance test (developers only)");

    /** The p95 server tick time the perf test allows with four NPC ships around the player [ms]. */
    public static final ConfigValue<Double> BUDGET_MS = S.doubleRange("budget_ms", 50.0, 1.0, 1000.0,
            "Highest 95th percentile tick time in milliseconds the performance test allows with four NPC ships near the player");

    private PerfConfig() {
    }

    /** Loads the class so the value is declared in time. Called from {@code CoreModule.registerConfig()}. */
    public static void init() {
    }
}
