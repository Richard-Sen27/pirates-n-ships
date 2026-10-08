package com.richardsenger.piratesnships.crew.hiring;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.crew.CrewConfig;
import com.richardsenger.piratesnships.rpg.career.InfamyRank;

/**
 * Server config sub-section {@code crew.hiring} (CRW1, docs/design.md §7.1, §17): the Crew tab of the harbor desk.
 */
public final class HiringConfig {

    private static final ConfigSection S = CrewConfig.sub("hiring",
            "Hiring crew at the Crew tab of a harbor desk: sailors at villages, pirates at islands, navy ratings at outposts (CRW1)");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Harbor desks have a Crew tab where crew can be hired, and sneak-using the whistle on a crew member dismisses it. Off = no tab, no hiring, no dismissal");
    public static final ConfigValue<Integer> CANDIDATES_PER_PORT = S.intRange("candidates_per_port", 3, 0, 12,
            "Candidates a port offers each day (hired ones are gone until the next day)");
    public static final ConfigValue<Integer> FEE_SAILOR = S.intRange("fee_sailor", 10, 0, 100_000,
            "Doubloons a sailor asks to sign on at a seafarer village");
    public static final ConfigValue<Integer> FEE_PIRATE = S.intRange("fee_pirate", 20, 0, 100_000,
            "Doubloons a pirate asks to sign on at a pirate island");
    public static final ConfigValue<Integer> FEE_NAVY = S.intRange("fee_navy", 15, 0, 100_000,
            "Doubloons a navy rating asks to sign on at a navy outpost");
    public static final ConfigValue<InfamyRank> PIRATE_MIN_INFAMY = S.enumValue("pirate_min_infamy", InfamyRank.BUCCANEER,
            "Infamy rank from which pirates sign on with a captain they are not friendly with (careers on)");
    public static final ConfigValue<Boolean> NAVY_REQUIRES_ENLISTMENT = S.bool("navy_requires_enlistment", true,
            "Navy ratings sign on only with an enlisted captain (Midshipman or higher; ignored while careers are off)");
    public static final ConfigValue<Integer> SHIP_RADIUS = S.intRange("ship_radius", 32, 0, 512,
            "Blocks around a port's area in which the player's ship counts as moored there");
    public static final ConfigValue<Boolean> REQUIRE_BUNKS = S.bool("require_bunks", true,
            "A ship takes on crew only while it has a free bunk (hammocks x crew.max_crew_multiplier). Off = at least max_without_bunks crew");
    public static final ConfigValue<Integer> MAX_WITHOUT_BUNKS = S.intRange("max_without_bunks", 2, 0, 100,
            "Crew a ship may take on without hammocks while require_bunks is off");

    private HiringConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code HiringModule.registerConfig()}. */
    public static void init() {
    }

    public static boolean enabled() {
        return ENABLED.get();
    }

    public static int fee(CandidateKind kind) {
        return switch (kind) {
            case SAILOR -> FEE_SAILOR.get();
            case PIRATE -> FEE_PIRATE.get();
            case NAVY -> FEE_NAVY.get();
        };
    }

    public static HiringRules.Settings settings() {
        return new HiringRules.Settings(PIRATE_MIN_INFAMY.get(), NAVY_REQUIRES_ENLISTMENT.get());
    }
}
