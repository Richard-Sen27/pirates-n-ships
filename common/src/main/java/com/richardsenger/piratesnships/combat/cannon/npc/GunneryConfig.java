package com.richardsenger.piratesnships.combat.cannon.npc;

import com.richardsenger.piratesnships.combat.cannon.CannonConfig;
import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;

/**
 * Server config section {@code cannons.npc} (WS4a, docs/design.md §8.2, §17 "Combat"): gun crews that aim and fire by
 * themselves ("Fire at will", NPC ships). Muzzle speed, gravity and the ball's lifetime are the cannon's
 * ({@code cannons.*}).
 */
public final class GunneryConfig {

    private static final ConfigSection S = CannonConfig.section("npc",
            "Gun crews that aim and fire by themselves at hostile ships (the \"Fire at will\" order, NPC ships)");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Gun crews ordered to fire at will (and the crews of NPC ships) aim and fire at hostile ships by themselves. "
                    + "Off = crews fire only on a captain's \"Fire!\"");
    public static final ConfigValue<Double> ARC_DEGREES = S.doubleRange("arc_degrees", 15.0, 0.0, 90.0,
            "A crew fires only at a target within this many degrees left or right of its cannon's barrel");
    public static final ConfigValue<Integer> ENGAGE_RANGE = S.intRange("engage_range", 64, 4, 512,
            "Ships within this many blocks (horizontally) are targets for crews firing at will");
    public static final ConfigValue<Integer> AIM_INTERVAL_TICKS = S.intRange("aim_interval_ticks", 20, 1, 1200,
            "Ticks between two aiming moves of a crew: each move turns the barrel one elevation step or fires");
    public static final ConfigValue<Integer> FIRE_INTERVAL_TICKS = S.intRange("fire_interval_ticks", 40, 0, 12000,
            "Least ticks between two shots a crew fires by itself from the same gun (on top of loading)");
    public static final ConfigValue<Double> NPC_BLOCK_DAMAGE_MULTIPLIER = S.doubleRange("npc_block_damage_multiplier", 0.5, 0.0, 4.0,
            "Blocks a ball fired by a crew on its own breaks, times this (0.5 = half of the hits break a block). "
                    + "Shots on a captain's \"Fire!\" break the full amount");
    public static final ConfigValue<Double> AIM_HEIGHT = S.doubleRange("aim_height", 0.5, 0.0, 1.0,
            "Where crews aim on a target ship's height: 0 = the bottom of its bounds, 0.5 = the middle, 1 = the top");
    public static final ConfigValue<Double> AIM_TOLERANCE = S.doubleRange("aim_tolerance", 0.5, 0.0, 8.0,
            "A crew fires only when its best elevation sends the ball through the target's bounds grown by this many blocks");

    private GunneryConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code CannonModule#registerConfig}. */
    public static void init() {
    }
}
