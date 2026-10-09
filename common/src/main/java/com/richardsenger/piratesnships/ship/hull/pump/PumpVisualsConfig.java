package com.richardsenger.piratesnships.ship.hull.pump;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Client config section {@code pump_visuals} (PMP1, docs/design.md §4.8 "Visual backlog 2", item 3): the rocking handle
 * of the bilge pump. Declared on both sides from {@code HullModule.registerConfig()} (no client classes here); only the
 * client reads it. The server side of the pump is the {@code flooding} section.
 */
public final class PumpVisualsConfig {

    private static final ConfigSection S = ModConfigs.client("pump_visuals", "How the bilge pump looks on this client");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Rock the bilge pump's handle while a player or crew member pumps (off = the handle stays up)");
    public static final ConfigValue<Integer> STROKE_TICKS = S.intRange("stroke_ticks", 20, 4, 200,
            "Ticks of one pump stroke, handle down and up again");
    public static final ConfigValue<Double> STROKE_DEGREES = S.doubleRange("stroke_degrees", 28.0, 0.0, 30.0,
            "How far the handle goes down from its raised rest, in degrees (30 = just clear of the pump's cylinder)");

    private PumpVisualsConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
