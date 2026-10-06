package com.richardsenger.piratesnships.station;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/** Server config of the crew stations (docs/design.md §6, §17), section {@code crew_stations}. */
public final class StationConfig {

    private static final ConfigSection S = ModConfigs.server("crew_stations", "Ship stations operated by crew members (spike 4)");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Crew members can be assigned to stations and carry out orders there. Off: assigned crew are released");
    public static final ConfigValue<Integer> TICKS_PER_TRIM_STEP = S.intRange("ticks_per_trim_step", 40, 0, 1200,
            "Ticks a crew member at the sail winch needs per trim step (furled to half, half to full)");
    public static final ConfigValue<Integer> ACK_RADIUS = S.intRange("ack_radius", 24, 0, 256,
            "Players within this many blocks of a crew member see its acknowledgement of an order (0 = nobody)");
    public static final ConfigValue<Integer> ORDER_RADIUS = S.intRange("order_radius", 64, 1, 512,
            "Range of /pirates crew order without targets: assigned crew members within this many blocks");
    public static final ConfigValue<Integer> SEAT_CHECK_INTERVAL = S.intRange("seat_check_interval", 10, 1, 200,
            "Ticks between checks of an assigned crew member that is not at its station (take the seat, or release it)");

    private StationConfig() {
    }

    public static void init() {
    }
}
