package com.richardsenger.piratesnships.ship.assembly;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/** Server config section {@code assembly}: helm assembly and disassembly (docs/design.md §4.1). */
public final class AssemblyConfig {

    private static final ConfigSection S = ModConfigs.server("assembly", "Ship assembly and disassembly at the helm");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Using a helm in the world turns the connected blocks into a ship");
    public static final ConfigValue<Boolean> DISASSEMBLY_ENABLED = S.bool("disassembly_enabled", true,
            "Using the helm of a still, level ship turns it back into world blocks");
    public static final ConfigValue<Integer> MAX_BLOCKS = S.intRange("max_blocks", 2048, 1, 65536,
            "Maximum number of blocks one ship may have. Also stops runaway gathers into docks or buildings");
    public static final ConfigValue<Double> MAX_LINEAR_SPEED = S.doubleRange("max_linear_speed", 0.3, 0.0, 20.0,
            "Disassembly is refused while the ship moves faster than this (m/s)");
    public static final ConfigValue<Double> MAX_ANGULAR_SPEED = S.doubleRange("max_angular_speed", 0.15, 0.0, 10.0,
            "Disassembly is refused while the ship turns faster than this (rad/s)");
    public static final ConfigValue<Double> MAX_TILT_DEGREES = S.doubleRange("max_tilt_degrees", 6.0, 0.0, 45.0,
            "Disassembly is refused while the ship is tilted more than this from level (degrees)");
    public static final ConfigValue<Boolean> RESTORE_SEA = S.bool("restore_sea_on_assembly", true,
            "Refill the hole a hull leaves in the sea with water sources when it is assembled");
    public static final ConfigValue<Boolean> DRAIN_HULL = S.bool("drain_hull_on_disassembly", true,
            "Remove the sea water that would end up inside the hull when a ship is disassembled in water");
    public static final ConfigValue<Boolean> MOVE_ENTITIES = S.bool("move_entities_on_disassembly", true,
            "Move players and mobs standing on deck onto the placed deck blocks when a ship is disassembled");


    /** Sub-section {@code assembly.split}: a ship that Sable splits in two (RS1, {@link ShipSplits}). */
    private static final ConfigSection SPLIT = S.section("split",
            "A ship whose only connecting block is destroyed splits into pieces; the piece with the helm stays the ship");

    public static final ConfigValue<Boolean> SPLIT_ENABLED = SPLIT.bool("enabled", true,
            "Ships split when a part is cut off. Off = also switches Sable's sub_level_splitting off while this server runs, so ships never split");
    public static final ConfigValue<Integer> WRECK_MIN_BLOCKS = SPLIT.intRange("wreck_min_blocks", 4, 1, 4096,
            "A loose piece with fewer blocks than this breaks up into items instead of drifting off as a wreck");
    public static final ConfigValue<Integer> WRECK_PERSIST_TICKS = SPLIT.intRange("wreck_persist_ticks", 0, 0, 100000000,
            "Ticks a wreck piece stays while no player is near it before it is removed (0 = forever)");

    private AssemblyConfig() {
    }

    public static void init() {
    }
}
