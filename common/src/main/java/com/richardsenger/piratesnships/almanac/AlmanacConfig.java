package com.richardsenger.piratesnships.almanac;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/** Server config section {@code almanac} (docs/design.md §3.4, §17): the Captain's Almanac, book and placed tome. */
public final class AlmanacConfig {

    private static final ConfigSection SECTION = ModConfigs.server("almanac", "The Captain's Almanac: the in-game guide book and its placed tome");

    public static final ConfigValue<Boolean> ENABLED = SECTION.bool("enabled", true,
            "The Captain's Almanac can be read, in hand or placed (off = the tome is decoration: it never opens or animates)");
    public static final ConfigValue<Integer> OPEN_TICKS = SECTION.intRange("open_ticks", 100, 10, 1200,
            "How long a placed almanac lies open after a right-click at the least, in ticks (20 ticks = 1 second); it stays open longer while the reader still has it open");
    /** ALM2: the last page per player ({@code almanac.data.AlmanacDataConfig} forwards to it). */
    public static final ConfigValue<Boolean> REMEMBER_PAGE = SECTION.bool("remember_page", true,
            "The Almanac opens at the page each player last read, also after death and relog (false: nothing is stored, it opens at the start)");
    /** GUIDE2: the first-join gift ({@link AlmanacGift}), formerly {@code guide.give_on_first_join}. */
    public static final ConfigValue<Boolean> GIVE_ON_FIRST_JOIN = SECTION.bool("give_on_first_join", true,
            "A player who joins for the first time gets one Captain's Almanac, once");

    private AlmanacConfig() {
    }

    public static void init() {
    }
}
