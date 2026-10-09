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

    /** HON1: the title as a name prefix through one vanilla scoreboard team per title ({@link CareerTeams}). */
    public static final ConfigValue<Boolean> NAME_PREFIX = S.bool("name_prefix", true,
            "Show the career title (Lt., Capt., Privateer, Dread Pirate, ...) in front of the player's name in chat, the "
                    + "tab list and over the head, through a scoreboard team per title. Players on another team keep it. "
                    + "Off = players are taken off the title teams when they next log in");
    /** HON1: the title in front of a ship's name when its owner names it ({@link CareerShipTitles}). */
    public static final ConfigValue<Boolean> TITLE_ON_SHIP = S.bool("title_on_ship", true,
            "Naming a ship you own with a name tag puts your career title in front of the name (\"Capt. Black Gull\"); "
                    + "a title typed into the name tag is always replaced by your own");

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
    /** QST2: prize money for a pirate ship, paid at once through the wallet ({@link ShipPrizes}). */
    public static final ConfigValue<Integer> PRIZE_MONEY = S.intRange("prize_money", (int) ShipPrizes.DEFAULT_PRIZE, 0, 100_000,
            "Doubloons paid at once to a holder of a valid letter of marque who sinks (last cannon hit) or captures a pirate "
                    + "ship at sea, once per ship. 0 = none");

    // ------------------------------------------------------------------ CAR2: rank rewards

    private static final CareerRewardRules.Params R = CareerRewardRules.Params.defaults();
    private static final ConfigSection REWARDS = S.section("rewards",
            "What the ranks give (CAR2): the navy flag right, free docking, the navy shipyard, promotion gifts, fence prices and "
                    + "the pirates' friendship");

    public static final ConfigValue<Boolean> FLAG_RIGHT = REWARDS.bool("flag_right", R.flagRight(),
            "Navy officers from flag_right_rank up fly the navy flag legitimately whatever their navy reputation (a bounty still "
                    + "makes it false colours)");
    public static final ConfigValue<NavyRank> FLAG_RIGHT_RANK = REWARDS.enumValue("flag_right_rank", R.flagRightRank(),
            "Lowest navy rank with the right to fly the navy flag");
    public static final ConfigValue<Boolean> FEE_WAIVER = REWARDS.bool("fee_waiver", R.feeWaiver(),
            "Navy officers from fee_waiver_rank up pay no docking fee at navy outposts");
    public static final ConfigValue<NavyRank> FEE_WAIVER_RANK = REWARDS.enumValue("fee_waiver_rank", R.feeWaiverRank(),
            "Lowest navy rank that docks at navy outposts for free");
    public static final ConfigValue<Boolean> NAVY_SHIPYARD = REWARDS.bool("navy_shipyard", R.navyShipyard(),
            "The harbor master's desk of a navy outpost takes ship orders from navy officers of navy_orders_min_rank and up");
    public static final ConfigValue<NavyRank> NAVY_ORDERS_MIN_RANK = REWARDS.enumValue("navy_orders_min_rank", R.ordersMinRank(),
            "Lowest navy rank the navy shipyard builds for");
    private static final ConfigSection SHIP_PRICE = REWARDS.section("navy_ship_price",
            "Price factor (on top of ships.order_price_factor) of a ship ordered at a navy outpost, by navy rank");
    /** {@code careers.rewards.navy_ship_price.<rank>} for every rank from midshipman up. */
    public static final Map<NavyRank, ConfigValue<Double>> NAVY_SHIP_PRICE;
    public static final ConfigValue<Boolean> PROMOTION_GIFTS = REWARDS.bool("promotion_gifts", true,
            "A new rank comes with the items of <rank>_items (once per rank and player; dropped at the feet if the inventory is full)");
    /** {@code careers.rewards.<rank>_items} for every navy rank from midshipman up and every infamy rank from buccaneer up. */
    public static final Map<String, ConfigValue<List<String>>> RANK_ITEMS;
    public static final ConfigValue<Boolean> INFAMY_PRICES = REWARDS.bool("infamy_prices", R.infamyPrices(),
            "Fences at pirate islands give the infamous better prices (on top of the pirates' reputation)");
    public static final ConfigValue<Integer> INFAMY_PRICE_BONUS = REWARDS.intRange("infamy_price_bonus", R.infamyPriceBonus(), 0, 200,
            "Price score (as pirate reputation points, see reputation.price_swing) a Pirate Lord gains at fences; lower infamy "
                    + "ranks get a linear share (Buccaneer a third, Dread Captain two thirds)");
    public static final ConfigValue<Boolean> INFAMY_PIRATES_FRIENDLY = REWARDS.bool("infamy_pirates_friendly", R.infamyFriendly(),
            "Pirates leave players of infamy_pirates_friendly_rank and up alone until attacked, whatever their reputation");
    public static final ConfigValue<InfamyRank> INFAMY_PIRATES_FRIENDLY_RANK = REWARDS.enumValue("infamy_pirates_friendly_rank",
            R.piratesFriendlyRank(), "Lowest infamy rank the pirates treat as one of their own");

    static {
        Map<NavyRank, ConfigValue<Double>> prices = new EnumMap<>(NavyRank.class);
        Map<String, ConfigValue<List<String>>> items = new java.util.LinkedHashMap<>();
        for (NavyRank r : NavyRank.values()) {
            if (r == NavyRank.NONE) continue;
            prices.put(r, SHIP_PRICE.doubleRange(r.id(), CareerRewardRules.defaultShipPrice(r), 0.0, 1.0,
                    "Price factor for the navy rank " + r.id()));
            items.put(CareerRewardRules.giftKey(r), REWARDS.stringList(r.id() + "_items", defaultItems(r),
                    "Item ids given on reaching the navy rank " + r.id() + " (repeat an id for more than one)"));
        }
        for (InfamyRank r : InfamyRank.values()) {
            if (r == InfamyRank.DECKHAND) continue;
            items.put(CareerRewardRules.giftKey(r), REWARDS.stringList(r.id() + "_items", List.of(),
                    "Item ids given on reaching the infamy rank " + r.id() + " (repeat an id for more than one)"));
        }
        NAVY_SHIP_PRICE = Collections.unmodifiableMap(prices);
        RANK_ITEMS = Collections.unmodifiableMap(items);
    }

    // ------------------------------------------------------------------ SHP1: ship grants

    private static final ShipGrantRules.Params G = ShipGrantRules.Params.defaults();
    private static final ConfigSection SHIP_GRANTS = S.section("ship_grants",
            "Fighting ships by rank (SHP1): the navy grants a ship to a new Captain, the brethren one to a new Dread Captain, "
                    + "as a commission redeemed at a navy outpost's officer or desk (a pirate island's desk). Never sold by shipwrights");

    public static final ConfigValue<Boolean> SHIP_GRANTS_ENABLED = SHIP_GRANTS.bool("enabled", G.enabled(),
            "Reaching navy_rank or infamy_rank hands out a ship commission (once per player and ladder), and commissions are "
                    + "redeemed. Off = no commissions are handed out and held ones are refused (and kept)");
    public static final ConfigValue<NavyRank> SHIP_GRANTS_NAVY_RANK = SHIP_GRANTS.enumValue("navy_rank", G.navyRank(),
            "The navy rank whose first reaching grants a navy ship");
    public static final ConfigValue<InfamyRank> SHIP_GRANTS_INFAMY_RANK = SHIP_GRANTS.enumValue("infamy_rank", G.infamyRank(),
            "The infamy rank whose first reaching grants a pirate ship");
    public static final ConfigValue<String> SHIP_GRANTS_NAVY_TEMPLATE = SHIP_GRANTS.string("navy_template", G.navyTemplate().toString(),
            "Ship template (pirates_n_ships/ship_template id) of the navy grant");
    public static final ConfigValue<String> SHIP_GRANTS_PIRATE_TEMPLATE = SHIP_GRANTS.string("pirate_template", G.pirateTemplate().toString(),
            "Ship template (pirates_n_ships/ship_template id) of the pirate grant");

    /** The ship grants' view of the current config (a bad template id falls back to the default). */
    public static ShipGrantRules.Params shipGrants() {
        return new ShipGrantRules.Params(SHIP_GRANTS_ENABLED.get() && ENABLED.get(), SHIP_GRANTS_NAVY_RANK.get(),
                SHIP_GRANTS_INFAMY_RANK.get(), template(SHIP_GRANTS_NAVY_TEMPLATE.get(), G.navyTemplate()),
                template(SHIP_GRANTS_PIRATE_TEMPLATE.get(), G.pirateTemplate()));
    }

    private static net.minecraft.resources.ResourceLocation template(String id, net.minecraft.resources.ResourceLocation fallback) {
        net.minecraft.resources.ResourceLocation parsed = net.minecraft.resources.ResourceLocation.tryParse(id.trim());
        return parsed == null ? fallback : parsed;
    }

    /** Default gifts: a Lieutenant receives the officer's bicorne, a saber and the officer's coat; every other rank nothing. */
    static List<String> defaultItems(NavyRank rank) {
        return rank == NavyRank.LIEUTENANT
                ? List.of("pirates_n_ships:officer_hat", "pirates_n_ships:saber", "pirates_n_ships:officers_coat")
                : List.of();
    }

    /** The rewards' view of the current config. */
    public static CareerRewardRules.Params rewards() {
        Map<NavyRank, Double> prices = new EnumMap<>(NavyRank.class);
        NAVY_SHIP_PRICE.forEach((r, v) -> prices.put(r, v.get()));
        return new CareerRewardRules.Params(FLAG_RIGHT.get(), FLAG_RIGHT_RANK.get(), FEE_WAIVER.get(), FEE_WAIVER_RANK.get(),
                NAVY_SHIPYARD.get(), NAVY_ORDERS_MIN_RANK.get(), prices, INFAMY_PRICES.get(), INFAMY_PRICE_BONUS.get(),
                INFAMY_PIRATES_FRIENDLY.get(), INFAMY_PIRATES_FRIENDLY_RANK.get());
    }

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
