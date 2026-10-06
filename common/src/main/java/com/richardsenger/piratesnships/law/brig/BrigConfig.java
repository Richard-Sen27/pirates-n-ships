package com.richardsenger.piratesnships.law.brig;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config of the brig (design.md §13.3, §17 "Flags &amp; brig"). The two toggles {@code player_capture} and
 * {@code prisoner_escapes} live in {@code LawConfig} ({@code flags_brig}); everything else is here.
 */
public final class BrigConfig {

    private static final ConfigSection S = ModConfigs.server("brig", "Shackles, prisoners and brig cells");

    public static final ConfigValue<Double> CAPTURE_HEALTH_FRACTION = S.doubleRange("capture_health_fraction", 0.25, 0.01, 1.0,
            "A target can be shackled at or below this fraction of its max health");
    public static final ConfigValue<Double> FOLLOW_DISTANCE = S.doubleRange("follow_distance", 3.0, 1.0, 16.0,
            "A led prisoner walks after its captor when farther away than this (blocks)");
    public static final ConfigValue<Double> PULL_DISTANCE = S.doubleRange("pull_distance", 6.0, 2.0, 32.0,
            "Beyond this distance the chain pulls a led prisoner towards its captor, like a lead");
    public static final ConfigValue<Double> BREAK_DISTANCE = S.doubleRange("break_distance", 12.0, 4.0, 64.0,
            "Beyond this distance the captor loses hold: the prisoner stays shackled and stands still");
    public static final ConfigValue<Double> FOLLOW_SPEED = S.doubleRange("follow_speed", 1.0, 0.1, 3.0,
            "Walking speed modifier of a led prisoner");
    public static final ConfigValue<Integer> MAX_CELL_SIZE = S.intRange("max_cell_size", 64, 2, 1024,
            "Most open blocks (air volume) a brig cell may have");
    public static final ConfigValue<Integer> CHECK_INTERVAL_TICKS = S.intRange("check_interval_ticks", 20, 1, 1200,
            "Ticks between cell and escape checks of a prisoner");
    public static final ConfigValue<Double> ESCAPE_CHANCE_PER_MINUTE = S.doubleRange("escape_chance_per_minute", 0.1, 0.0, 1.0,
            "Chance per minute that a prisoner outside a locked cell (and not being led) escapes");
    public static final ConfigValue<Double> LOW_MORALE_ESCAPE_CHANCE_PER_MINUTE = S.doubleRange("low_morale_escape_chance_per_minute", 0.02, 0.0, 1.0,
            "Chance per minute that a prisoner escapes even a locked cell while the crew's morale is low");
    public static final ConfigValue<Integer> PLAYER_CAPTURE_SECONDS = S.intRange("player_capture_seconds", 300, 10, 86400,
            "A captured player frees themselves after this many seconds of game time");
    public static final ConfigValue<Integer> RANSOM_COMMON = S.intRange("ransom_common", 15, 0, 1000000,
            "Ransom in doubloons for a common prisoner");
    public static final ConfigValue<Integer> RANSOM_MERCHANT = S.intRange("ransom_merchant", 60, 0, 1000000,
            "Ransom in doubloons for a captured merchant");
    public static final ConfigValue<Integer> RANSOM_NAVY_OFFICER = S.intRange("ransom_navy_officer", 120, 0, 1000000,
            "Ransom in doubloons for a captured navy officer");
    public static final ConfigValue<Double> CAPTAIN_RANSOM_MULTIPLIER = S.doubleRange("captain_ransom_multiplier", 3.0, 1.0, 100.0,
            "Ransom multiplier for a captured captain");
    public static final ConfigValue<Double> PRESS_GANG_MORALE = S.doubleRange("press_gang_morale", 0.2, 0.0, 1.0,
            "Starting morale (0..1) of a press-ganged sailor, for the crew system");

    private BrigConfig() {
    }

    public static void init() {
    }

    public static RansomRules.Params ransomParams() {
        return new RansomRules.Params(RANSOM_COMMON.get(), RANSOM_MERCHANT.get(), RANSOM_NAVY_OFFICER.get(), CAPTAIN_RANSOM_MULTIPLIER.get());
    }

    public static EscapeRule.Params escapeParams() {
        int interval = CHECK_INTERVAL_TICKS.get();
        return new EscapeRule.Params(EscapeRule.perCheck(ESCAPE_CHANCE_PER_MINUTE.get(), interval),
                EscapeRule.perCheck(LOW_MORALE_ESCAPE_CHANCE_PER_MINUTE.get(), interval));
    }
}
