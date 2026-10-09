package com.richardsenger.piratesnships.rpg.quest;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;
import com.richardsenger.piratesnships.rpg.reputation.Reputation;
import com.richardsenger.piratesnships.trade.market.PortKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Server config section {@code quests} (docs/design.md §15, §17). The pure generator and rules never read these
 * handles; {@link #params()} copies them into a {@link QuestParams}.
 */
public final class QuestConfig {

    private static final ConfigSection S = ModConfigs.server("quests",
            "Quests offered at every port's harbor desk: hunts, monsters, prisoners, deliveries and treasure");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Offer quests. Off = no Quests tab, no new offers, accepting is refused and active quests stand still");
    public static final ConfigValue<Integer> OFFERS_PER_PORT = S.intRange("offers_per_port", 3, 0, 12,
            "Open quest offers a port keeps; missing ones are made at the first look on a new day");
    public static final ConfigValue<Integer> OFFER_DAYS = S.intRange("offer_days", 2, 1, 30,
            "In-game days an offer stays open before the port replaces it");
    public static final ConfigValue<Integer> MAX_ACTIVE = S.intRange("max_active", 3, 1, 20,
            "Quests a player can have active at once");
    public static final ConfigValue<Integer> DEADLINE_DAYS = S.intRange("deadline_days", 5, 1, 100,
            "In-game days after accepting until a quest fails (a delivery's contract has the same deadline)");
    public static final ConfigValue<Double> REWARD_SCALE = S.doubleRange("reward_scale", 1.0, 0.0, 100.0,
            "Multiplier on every quest's doubloon reward");
    public static final ConfigValue<Integer> HUNT_COUNT_MIN = S.intRange("hunt_count_min", 3, 1, 100,
            "Fewest pirates or navy sailors a hunt asks for");
    public static final ConfigValue<Integer> HUNT_COUNT_MAX = S.intRange("hunt_count_max", 8, 1, 100,
            "Most pirates or navy sailors a hunt asks for");
    public static final ConfigValue<Integer> MONSTER_COUNT_MIN = S.intRange("monster_count_min", 2, 1, 100,
            "Fewest sharks a monster quest asks for");
    public static final ConfigValue<Integer> MONSTER_COUNT_MAX = S.intRange("monster_count_max", 4, 1, 100,
            "Most sharks a monster quest asks for");
    public static final ConfigValue<Integer> TURN_IN_COUNT_MAX = S.intRange("turn_in_count_max", 2, 1, 20,
            "Most shackled pirates a turn-in quest asks for (at least 1)");
    public static final ConfigValue<Double> KRAKEN_CHANCE = S.doubleRange("kraken_chance", 0.15, 0.0, 1.0,
            "Chance that a monster quest asks for the kraken instead of sharks (always the kraken in a cold climate)");
    public static final ConfigValue<Boolean> TREASURE_QUEST_ENABLED = S.bool("treasure_quest_enabled", true,
            "Offer treasure hunts (they hand out a bound treasure map; also needs world.treasure_maps.enabled)");
    public static final ConfigValue<Integer> CAPTAIN_HUNT_RADIUS = S.intRange("captain_hunt_radius", 3000, 0, 100_000,
            "Blocks from a village or navy outpost within which the nearest pirate island's living captain can be hunted "
                    + "(hunt_captain; none in reach = no captain hunt offered)");
    public static final ConfigValue<Integer> POLL_TICKS = S.intRange("poll_ticks", 20, 1, 1200,
            "How often (ticks) deliveries, treasure hunts, escorts and deadlines of online players are checked");
    private static final QuestParams.Sea SEA = QuestParams.Sea.DEFAULTS;
    public static final ConfigValue<Boolean> SEA_QUESTS = S.bool("sea_quests", SEA.enabled(),
            "Offer quests at sea: escorts (villages, navy outposts), convoy raids and patrol hunts (pirate islands), ship hunts "
                    + "(navy outposts). Off = none offered; quests already accepted still finish");
    public static final ConfigValue<Integer> SEA_COUNT_MIN = S.intRange("sea_count_min", SEA.countMin(), 1, 50,
            "Fewest ships a convoy raid, patrol hunt or ship hunt asks for");
    public static final ConfigValue<Integer> SEA_COUNT_MAX = S.intRange("sea_count_max", SEA.countMax(), 1, 50,
            "Most ships a convoy raid, patrol hunt or ship hunt asks for");
    public static final ConfigValue<Integer> ESCORT_RADIUS = S.intRange("escort_radius", SEA.escortRadius(), 8, 1024,
            "Blocks from an escorted convoy within which you count as sailing with it");
    public static final ConfigValue<Double> ESCORT_FRACTION = S.doubleRange("escort_fraction", SEA.escortFraction(), 0.0, 1.0,
            "Share of the convoy's legs you must sail within escort_radius of it for the escort to count when it arrives");
    public static final ConfigValue<Integer> ESCORT_MAX_DISTANCE = S.intRange("escort_max_distance", SEA.escortMaxDistance(), 100, 100_000,
            "Farthest destination (blocks, straight line) a port sends an escorted convoy to");
    public static final ConfigValue<Integer> ESCORT_CHART_TIMEOUT_TICKS = S.intRange("escort_chart_timeout_ticks",
            QuestRules.DEFAULT_CHART_TIMEOUT_TICKS, 20, 72_000,
            "Ticks an accepted escort waits for the harbor master to chart its sea lane (searched off the tick, a few"
                    + " milliseconds per voyage check) before it is called off");

    private static final ConfigSection REWARDS = S.section("rewards", "Doubloons per quest, before reward_scale");
    public static final ConfigValue<Integer> PER_PIRATE = REWARDS.intRange("per_pirate", 30, 0, 100_000, "Per pirate of a hunt");
    public static final ConfigValue<Integer> PER_NAVY = REWARDS.intRange("per_navy", 35, 0, 100_000, "Per navy sailor of a hunt");
    public static final ConfigValue<Integer> PER_PRISONER = REWARDS.intRange("per_prisoner", 50, 0, 100_000,
            "Per pirate turned in (on top of the officer's turn-in pay)");
    public static final ConfigValue<Integer> PER_SHARK = REWARDS.intRange("per_shark", 25, 0, 100_000, "Per shark");
    public static final ConfigValue<Integer> KRAKEN = REWARDS.intRange("kraken", 500, 0, 1_000_000, "For the kraken");
    public static final ConfigValue<Integer> TREASURE = REWARDS.intRange("treasure", 100, 0, 1_000_000,
            "For finding a treasure (the chest's loot comes on top)");
    public static final ConfigValue<Integer> CAPTAIN = REWARDS.intRange("captain", 400, 0, 1_000_000,
            "For bringing down a named pirate captain (the navy's bounty on him comes on top)");
    public static final ConfigValue<Double> DELIVER_MULTIPLIER = REWARDS.doubleRange("deliver_multiplier", 1.5, 0.0, 100.0,
            "A delivery quest pays this times what an ordinary contract for the same cargo would (paid on delivery, no deposit)");
    public static final ConfigValue<Integer> PER_CONVOY = REWARDS.intRange("per_convoy", (int) SEA.perConvoy(), 0, 100_000,
            "Per merchant convoy plundered for a convoy raid");
    public static final ConfigValue<Integer> PER_PATROL = REWARDS.intRange("per_patrol", (int) SEA.perPatrol(), 0, 100_000,
            "Per navy patrol sunk or captured for a patrol hunt");
    public static final ConfigValue<Integer> PER_SHIP = REWARDS.intRange("per_ship", (int) SEA.perShip(), 0, 100_000,
            "Per pirate ship sunk or captured for a ship hunt");
    public static final ConfigValue<Integer> ESCORT = REWARDS.intRange("escort", (int) SEA.escort(), 0, 1_000_000,
            "Base reward of an escort");
    public static final ConfigValue<Integer> ESCORT_PER_1000 = REWARDS.intRange("escort_per_1000", (int) SEA.escortPer1000(), 0, 1_000_000,
            "Escort reward per 1000 blocks between the two ports (straight line)");

    private static final ConfigSection TYPES = S.section("types",
            "Quest types each port kind offers (hunt_pirates, kill_monster, turn_in, deliver, find_treasure, hunt_navy, "
                    + "hunt_captain, escort, plunder_convoy, hunt_patrol, hunt_ship). hunt_navy, plunder_convoy and hunt_patrol are "
                    + "only offered at pirate islands, turn_in and hunt_ship only at navy outposts, hunt_pirates, hunt_captain and "
                    + "escort never at pirate islands");

    /** {@code quests.types.<kind>}. */
    public static final Map<PortKind, ConfigValue<List<String>>> TYPE_LISTS;

    static {
        Map<PortKind, ConfigValue<List<String>>> m = new EnumMap<>(PortKind.class);
        for (PortKind k : PortKind.values()) {
            m.put(k, TYPES.stringList(k.getSerializedName(), QuestParams.DEFAULT_TYPES.get(k).stream().map(QuestType::id).toList(),
                    "Quest types offered at " + k.getSerializedName().replace('_', ' ') + "s"));
        }
        TYPE_LISTS = Collections.unmodifiableMap(m);
    }

    private QuestConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code QuestModule.registerConfig()}. */
    public static void init() {
    }

    public static boolean enabled() {
        return ENABLED.get();
    }

    /** The current config as pure parameters. */
    public static QuestParams params() {
        Map<PortKind, List<QuestType>> types = new EnumMap<>(PortKind.class);
        for (PortKind k : PortKind.values()) {
            List<QuestType> list = new ArrayList<>();
            for (String s : TYPE_LISTS.get(k).get()) QuestType.byId(s.trim()).ifPresent(list::add);
            types.put(k, list);
        }
        return new QuestParams(OFFERS_PER_PORT.get(), OFFER_DAYS.get(), DEADLINE_DAYS.get(), MAX_ACTIVE.get(), REWARD_SCALE.get(),
                HUNT_COUNT_MIN.get(), HUNT_COUNT_MAX.get(), MONSTER_COUNT_MIN.get(), MONSTER_COUNT_MAX.get(), TURN_IN_COUNT_MAX.get(),
                KRAKEN_CHANCE.get(), PER_PIRATE.get(), PER_NAVY.get(), PER_PRISONER.get(), PER_SHARK.get(), KRAKEN.get(), TREASURE.get(),
                CAPTAIN.get(), DELIVER_MULTIPLIER.get(), CAPTAIN_HUNT_RADIUS.get(), TREASURE_QUEST_ENABLED.get(), Reputation.enabled(), types,
                sea());
    }

    /** The sea quests' settings (QST2). */
    public static QuestParams.Sea sea() {
        return new QuestParams.Sea(SEA_QUESTS.get(), SEA_COUNT_MIN.get(), SEA_COUNT_MAX.get(), PER_CONVOY.get(), PER_PATROL.get(),
                PER_SHIP.get(), ESCORT.get(), ESCORT_PER_1000.get(), ESCORT_MAX_DISTANCE.get(), ESCORT_FRACTION.get(), ESCORT_RADIUS.get());
    }
}
