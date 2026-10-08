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

    private static final ConfigSection JOB_BOARD = S.section("job_board",
            "Orders instead of assignments (CR1): unmanned stations that take a ship-wide order become open jobs, and free crew on board claim them");
    public static final ConfigValue<Boolean> JOB_BOARD_ENABLED = JOB_BOARD.bool("enabled", true,
            "Ship-wide orders post open jobs for unmanned stations that free crew take by themselves. Off = only manned stations carry orders out");
    public static final ConfigValue<Integer> CLAIM_INTERVAL_TICKS = JOB_BOARD.intRange("claim_interval_ticks", 20, 1, 200,
            "Ticks between two passes of the job board, in which free crew claim the open jobs");
    public static final ConfigValue<Double> MAX_CLAIM_DISTANCE = JOB_BOARD.doubleRange("max_claim_distance", 0.0, 0.0, 128.0,
            "Crew members farther than this many blocks from an open job never claim it (0 = the whole ship)");

    private StationConfig() {
    }

    /** A subsection {@code crew_stations.<name>} for a station kind's own config class (e.g. {@code station.helm.CourseConfig}). */
    public static ConfigSection section(String name, String comment) {
        return S.section(name, comment);
    }

    public static void init() {
    }
}
