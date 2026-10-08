package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Server config section {@code careers} (docs/design.md §15, §17, CAR1). The pure rules never read these handles;
 * {@link #thresholds()} copies them into a {@link CareerThresholds}.
 */
public final class CareerConfig {

    private static final CareerThresholds D = CareerThresholds.defaults();

    private static final ConfigSection S = ModConfigs.server("careers",
            "Careers: the navy rank ladder, pirate infamy and the letter of marque");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Track careers. Off = deeds change no counters and promote nobody, officers neither enlist nor grant letters, "
                    + "and nobody deserts");
    public static final ConfigValue<Double> OFFICER_REACH = S.doubleRange("officer_reach", 4.0, 1.0, 16.0,
            "How close (blocks) a player must stay to the navy officer for the career screen's actions");
    public static final ConfigValue<Integer> MAX_PIRATE_REP_TO_ENLIST = S.intRange("max_pirate_rep_to_enlist", D.maxPirateRepToEnlist(),
            -100, 100, "Enlisting and navy promotions need a pirate reputation of at most this");
    public static final ConfigValue<List<String>> DESERTION_DEEDS = S.stringList("desertion_deeds", List.copyOf(D.desertionDeeds()),
            "Deeds (ids as in reputation.deeds) that are desertion while in navy service and void a letter of marque");
    public static final ConfigValue<Boolean> DESERTION_IS_CRIME = S.bool("desertion_is_crime", true,
            "Desertion is also the 'desertion' crime on the criminal record (the navy then sets a bounty by itself)");
    public static final ConfigValue<Integer> CAPTAIN_WEIGHT = S.intRange("captain_weight", D.captainWeight(), 1, 100,
            "How many pirate kills killing a pirate captain counts as");
    public static final ConfigValue<List<String>> CAPTAIN_KINDS = S.stringList("captain_kinds", List.of("pirates_n_ships:pirate_captain"),
            "Entity types that count as pirate captains (weighted kills, the captain's prize money)");
    public static final ConfigValue<List<String>> OFFICER_KINDS = S.stringList("officer_kinds", List.of("pirates_n_ships:navy_officer"),
            "Entity types that count as navy officers (a pirate's 'captures or captains')");

    private static final ConfigSection NAVY = S.section("navy", "What each navy rank needs. Midshipman = what enlisting needs");
    private static final ConfigSection INFAMY = S.section("infamy", "What each pirate infamy rank needs");

    public record NavyValues(ConfigValue<Integer> minNavyRep, ConfigValue<Integer> piratesKilled, ConfigValue<Integer> quests) {
    }

    public record InfamyValues(ConfigValue<Integer> minPirateRep, ConfigValue<Integer> plunderCoins, ConfigValue<Integer> captures) {
    }

    /** {@code careers.navy.<rank>.*} for every rank from midshipman up. */
    public static final Map<NavyRank, NavyValues> NAVY_STEPS;
    /** {@code careers.infamy.<rank>.*} for every rank from buccaneer up. */
    public static final Map<InfamyRank, InfamyValues> INFAMY_STEPS;

    static {
        Map<NavyRank, NavyValues> navy = new EnumMap<>(NavyRank.class);
        for (NavyRank r : NavyRank.values()) {
            if (r == NavyRank.NONE) continue;
            CareerThresholds.NavyStep d = CareerThresholds.defaultStep(r);
            ConfigSection s = NAVY.section(r.id(), "Navy rank " + r.id());
            navy.put(r, new NavyValues(
                    s.intRange("min_navy_rep", d.minNavyRep(), -100, 101, "Navy reputation needed (101 = never)"),
                    s.intRange("pirates_killed", (int) d.piratesDefeated(), 0, 100_000, "Pirates killed or turned in (a captain counts captain_weight)"),
                    s.intRange("quests", (int) d.quests(), 0, 10_000, "Navy quests completed")));
        }
        NAVY_STEPS = Collections.unmodifiableMap(navy);
        Map<InfamyRank, InfamyValues> infamy = new EnumMap<>(InfamyRank.class);
        for (InfamyRank r : InfamyRank.values()) {
            if (r == InfamyRank.DECKHAND) continue;
            CareerThresholds.InfamyStep d = CareerThresholds.defaultStep(r);
            ConfigSection s = INFAMY.section(r.id(), "Infamy rank " + r.id());
            infamy.put(r, new InfamyValues(
                    s.intRange("min_pirate_rep", d.minPirateRep(), -100, 101, "Pirate reputation needed (101 = never)"),
                    s.intRange("plunder_coins", (int) d.plunderCoins(), 0, 100_000_000, "Doubloons received for plunder at fences"),
                    s.intRange("captures_or_captains", (int) d.captures(), 0, 100_000,
                            "Merchants plundered, ships captured and navy officers killed, together")));
        }
        INFAMY_STEPS = Collections.unmodifiableMap(infamy);
    }

    private static final ConfigSection LETTER = S.section("letter", "The letter of marque: the navy's licence to hunt pirates for prize money");

    public static final ConfigValue<Boolean> LETTER_ENABLED = LETTER.bool("enabled", D.letter().enabled(),
            "Navy officers grant letters of marque");
    public static final ConfigValue<Integer> LETTER_MIN_NAVY_REP = LETTER.intRange("min_navy_rep", D.letter().minNavyRep(), -100, 101,
            "Navy reputation needed for a letter");
    public static final ConfigValue<InfamyRank> LETTER_MAX_INFAMY = LETTER.enumValue("max_infamy", D.letter().maxInfamy(),
            "Highest infamy rank that still gets a letter");
    public static final ConfigValue<Integer> LETTER_FEE = LETTER.intRange("fee", (int) D.letter().fee(), 0, 1_000_000,
            "Doubloons a letter costs");
    public static final ConfigValue<Double> LETTER_VOID_DAYS = LETTER.doubleRange("void_days",
            (double) D.letter().voidTicks() / CareerRules.TICKS_PER_DAY, 0.0, 365.0,
            "In-game days after a voided letter before the navy grants a new one");
    public static final ConfigValue<Integer> PRIZE_DECKHAND = LETTER.intRange("prize_deckhand", (int) D.letter().prizeDeckhand(), 0, 100_000,
            "Prize money (doubloons) per pirate killed under a letter");
    public static final ConfigValue<Integer> PRIZE_CAPTAIN = LETTER.intRange("prize_captain", (int) D.letter().prizeCaptain(), 0, 100_000,
            "Prize money (doubloons) per pirate captain killed under a letter");

    private CareerConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code CareerModule.registerConfig()}. */
    public static void init() {
    }

    /** The rules' view of the current config. */
    public static CareerThresholds thresholds() {
        Map<NavyRank, CareerThresholds.NavyStep> navy = new EnumMap<>(NavyRank.class);
        NAVY_STEPS.forEach((r, v) -> navy.put(r, new CareerThresholds.NavyStep(v.minNavyRep().get(), v.piratesKilled().get(), v.quests().get())));
        Map<InfamyRank, CareerThresholds.InfamyStep> infamy = new EnumMap<>(InfamyRank.class);
        INFAMY_STEPS.forEach((r, v) -> infamy.put(r, new CareerThresholds.InfamyStep(v.minPirateRep().get(), v.plunderCoins().get(), v.captures().get())));
        CareerThresholds.LetterTerms letter = new CareerThresholds.LetterTerms(LETTER_ENABLED.get(), LETTER_MIN_NAVY_REP.get(),
                LETTER_MAX_INFAMY.get(), LETTER_FEE.get(), Math.round(LETTER_VOID_DAYS.get() * CareerRules.TICKS_PER_DAY),
                PRIZE_DECKHAND.get(), PRIZE_CAPTAIN.get());
        return new CareerThresholds(navy, infamy, MAX_PIRATE_REP_TO_ENLIST.get(), new HashSet<>(DESERTION_DEEDS.get()),
                CAPTAIN_WEIGHT.get(), letter);
    }
}
