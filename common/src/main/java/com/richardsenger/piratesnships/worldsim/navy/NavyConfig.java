package com.richardsenger.piratesnships.worldsim.navy;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.worldsim.WorldSimConfig;

/**
 * Server config {@code world_simulation.navy} (WS4b, design.md §10.4, §17): navy patrols and the hunt. How many
 * patrols set out is {@code world_simulation.patrols_per_day} ({@link WorldSimConfig#PATROLS_PER_DAY}), scaled by the
 * navy's aggression.
 */
public final class NavyConfig {

    private static final ConfigSection S = WorldSimConfig.sub("navy",
            "Navy patrols between the outposts that hunt Jolly Roger ships and wanted captains");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Navy patrols set out and hunt. Off = no new patrols, and patrols at sea stop hunting and sail their route");
    public static final ConfigValue<Integer> HUNT_RADIUS = S.intRange("hunt_radius", 256, 16, 2048,
            "A patrol sights a player's ship within this many blocks (horizontally) and gives chase if it is hunted");
    public static final ConfigValue<Integer> HUNT_BOUNTY_MINIMUM = S.intRange("hunt_bounty_minimum", 50, 0, 1_000_000,
            "Doubloons of bounty on a ship's owner from which the navy hunts the ship whatever flag it flies "
                    + "(a ship is also hunted under the Jolly Roger, with its cover blown, or with its owner wanted at law.world.navy_hostility_threshold)");
    public static final ConfigValue<Integer> GIVE_UP_TICKS = S.intRange("give_up_ticks", 2400, 20, 720_000,
            "A patrol that has not come within contact_distance of its quarry for this many ticks breaks off the chase");
    public static final ConfigValue<Integer> CONTACT_DISTANCE = S.intRange("contact_distance", 64, 4, 1024,
            "Blocks within which a patrol counts as in contact with its quarry (resets the give-up timer)");
    public static final ConfigValue<Integer> LOSE_DISTANCE = S.intRange("lose_distance", 384, 16, 4096,
            "A quarry farther than this many blocks from the patrol is lost, and the patrol returns to its route");
    public static final ConfigValue<Integer> STANDOFF_DISTANCE = S.intRange("standoff_distance", 20, 4, 256,
            "Distance in blocks a patrol keeps from its quarry: it circles it at this range");
    public static final ConfigValue<Integer> COURSE_INTERVAL_TICKS = S.intRange("course_interval_ticks", 40, 5, 1200,
            "Ticks between two course updates of a patrol ship chasing its quarry");
    public static final ConfigValue<Integer> COURSE_REFRESH_DISTANCE = S.intRange("course_refresh_distance", 6, 0, 256,
            "A patrol ship's course is only given anew when its quarry moved at least this many blocks since the last one");
    public static final ConfigValue<Integer> PATROL_RADIUS = S.intRange("patrol_radius", 400, 16, 10_000,
            "A lone outpost's patrol sails this many blocks out along the lane toward the nearest pirate island and back");
    public static final ConfigValue<Integer> MAX_OUTPOST_DISTANCE = S.intRange("max_outpost_distance", 3000, 0, 100_000,
            "A patrol sails to another navy outpost at most this far away (blocks between centres); else it sails out and back");
    public static final ConfigValue<Integer> SURRENDER_LINGER_TICKS = S.intRange("surrender_linger_ticks", 600, 0, 72_000,
            "Ticks a patrol shadows a ship that struck its colours, guns silent, before it returns to its route");
    public static final ConfigValue<Boolean> ANNOUNCE = S.bool("announce", true,
            "Tell a hunted ship's owner when a patrol gives chase, breaks off, or accepts the surrender");

    private NavyConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    /** Whether patrols set out and hunt (both toggles). */
    public static boolean active() {
        return WorldSimConfig.ENABLED.get() && ENABLED.get();
    }

    /** The hunt's parameters at the current config. */
    public static HuntRules.Params params() {
        return new HuntRules.Params(HUNT_RADIUS.get(), HUNT_BOUNTY_MINIMUM.get(), LOSE_DISTANCE.get(), CONTACT_DISTANCE.get(),
                GIVE_UP_TICKS.get(), SURRENDER_LINGER_TICKS.get());
    }
}
