package com.richardsenger.piratesnships.worldsim.captain;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.mob.captain.CaptainConfig;
import com.richardsenger.piratesnships.worldsim.WorldSimConfig;
import com.richardsenger.piratesnships.worldsim.navy.HuntRules;

/**
 * Server config {@code world_simulation.captain} (BOS2, design.md §10.4, §15, §17): the pirate captain's voyages from
 * his island and his hunt at sea. The captain himself (health, bounty, duel, successor) stays under {@code mobs.captain}.
 */
public final class CaptainVoyageConfig {

    private static final ConfigSection S = WorldSimConfig.sub("captain",
            "Pirate captains put to sea from their islands on their own ship and hunt players carrying a letter of marque or bounty proofs");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Pirate captains put to sea. Off = no new voyages; a captain at sea sails home and hunts nobody");
    public static final ConfigValue<Integer> VOYAGE_DAYS = S.intRange("voyage_days", 2, 1, 365,
            "Days between two chances of a captain at his post to put to sea");
    public static final ConfigValue<Double> VOYAGE_CHANCE = S.doubleRange("voyage_chance", 0.5, 0.0, 1.0,
            "Chance that a captain puts to sea when his day comes (every voyage_days days)");
    public static final ConfigValue<Integer> CRUISE_DISTANCE = S.intRange("cruise_distance", 600, 32, 10_000,
            "A captain sails this many blocks out (along the lane toward a village or outpost within this distance, "
                    + "else into open sea) and back to his island");
    public static final ConfigValue<Integer> HUNT_RADIUS = S.intRange("hunt_radius", 192, 0, 2048,
            "A captain at sea sights a hunted player's ship within this many blocks (0 = he hunts nobody)");
    public static final ConfigValue<Boolean> HUNT_LETTER = S.bool("hunt_letter", true,
            "A captain hunts the ship of a player who holds a letter of marque");
    public static final ConfigValue<Integer> HUNT_MIN_PROOFS = S.intRange("hunt_min_proofs", 1, 0, 64,
            "A captain hunts the ship of a player carrying at least this many bounty proofs (0 = proofs never draw him)");
    public static final ConfigValue<Integer> LOSE_DISTANCE = S.intRange("lose_distance", 320, 16, 4096,
            "A quarry farther than this many blocks from the captain is lost, and he returns to his course");
    public static final ConfigValue<Integer> CONTACT_DISTANCE = S.intRange("contact_distance", 64, 4, 1024,
            "Blocks within which the captain counts as in contact with his quarry (resets the give-up timer)");
    public static final ConfigValue<Integer> GIVE_UP_TICKS = S.intRange("give_up_ticks", 2400, 20, 720_000,
            "A captain who has not come within contact_distance of his quarry for this many ticks breaks off the chase");
    public static final ConfigValue<Integer> STANDOFF_DISTANCE = S.intRange("standoff_distance", 16, 4, 256,
            "Distance in blocks the captain's ship keeps from its quarry: it circles it at this range with its guns firing");
    public static final ConfigValue<Integer> SURRENDER_LINGER_TICKS = S.intRange("surrender_linger_ticks", 600, 0, 72_000,
            "Ticks the captain's ship lies by a quarry that struck its colours, guns silent, before it sails on");
    public static final ConfigValue<Integer> COURSE_INTERVAL_TICKS = S.intRange("course_interval_ticks", 40, 5, 1200,
            "Ticks between two course updates of a captain's ship chasing its quarry (and between calls to man the guns)");
    public static final ConfigValue<Integer> COURSE_REFRESH_DISTANCE = S.intRange("course_refresh_distance", 6, 0, 256,
            "A captain's chase course is only given anew when the quarry moved at least this many blocks since the last one");
    public static final ConfigValue<Boolean> ANNOUNCE = S.bool("announce", true,
            "Tell a hunted ship's owner when a captain gives chase or breaks off");

    private CaptainVoyageConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    /** Captains put to sea and hunt: this toggle, the world simulation's and the captains' own ({@code mobs.captain.enabled}). */
    public static boolean active() {
        return ENABLED.get() && WorldSimConfig.ENABLED.get() && CaptainConfig.enabled();
    }

    /** The scheduling parameters at the current config. */
    public static CaptainVoyageRules.Schedule schedule() {
        return new CaptainVoyageRules.Schedule(VOYAGE_DAYS.get(), VOYAGE_CHANCE.get());
    }

    /** Who the captain hunts at the current config. */
    public static CaptainVoyageRules.Quarry quarry() {
        return new CaptainVoyageRules.Quarry(HUNT_LETTER.get(), HUNT_MIN_PROOFS.get());
    }

    /** The chase parameters at the current config ({@link HuntRules}; the bounty rule is the captain's own). */
    public static HuntRules.Params huntParams() {
        return CaptainVoyageRules.huntParams(HUNT_RADIUS.get(), LOSE_DISTANCE.get(), CONTACT_DISTANCE.get(), GIVE_UP_TICKS.get(),
                SURRENDER_LINGER_TICKS.get());
    }
}
