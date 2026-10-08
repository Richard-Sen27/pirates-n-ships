package com.richardsenger.piratesnships.mob.squad;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.mob.MobConfig;

/**
 * Server config section {@code mobs.squad} (MOB2, docs/design.md §9, §17): navy officers leading squads of their
 * outpost's garrison on patrol.
 */
public final class SquadConfig {

    private static final ConfigSection S = MobConfig.SQUAD;

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "A navy outpost's officer leads a squad of its garrison on patrol around the fort (off = the garrison keeps its posts)");
    public static final ConfigValue<Integer> SIZE = S.intRange("size", 3, 0, 8,
            "Soldiers of the garrison that follow the officer on patrol");
    public static final ConfigValue<Integer> PATROL_INTERVAL_MINUTES = S.intRange("patrol_interval_minutes", 10, 1, 240,
            "Minutes the squad stands at its posts between two patrols");
    public static final ConfigValue<Integer> POST_PAUSE_SECONDS = S.intRange("post_pause_seconds", 20, 0, 600,
            "Seconds the squad stops at each waypoint of its patrol route");
    public static final ConfigValue<Boolean> NIGHT_AT_POSTS = S.bool("night_at_posts", true,
            "At night the squad returns to the garrison posts and starts no patrol (a patrol ordered by command keeps going)");

    private SquadConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code SquadModule.registerConfig()}. */
    public static void init() {
    }

    public static int patrolIntervalTicks() {
        return PATROL_INTERVAL_MINUTES.get() * 60 * 20;
    }

    public static int pauseTicks() {
        return POST_PAUSE_SECONDS.get() * 20;
    }
}
