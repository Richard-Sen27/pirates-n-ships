package com.richardsenger.piratesnships.rpg.quest;

import com.richardsenger.piratesnships.trade.market.PortKind;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The quest settings the pure generator and rules need, copied from {@code QuestConfig} (or {@link #DEFAULTS} in
 * tests). Rewards are doubloons before {@code rewardScale}.
 *
 * @param types       the quest types each port kind lists ({@code quests.types.<kind>}); {@link QuestType#allowedAt}
 *                    still filters them
 * @param captain       reward of a captain hunt (QST1b)
 * @param captainRadius blocks from the port within which a living captain can be hunted ({@code quests.captain_hunt_radius})
 * @param deedsTracked whether deeds are recorded ({@code reputation.enabled}); deed-tracked types are offered only then
 * @param sea          the sea quests' settings (QST2)
 */
public record QuestParams(int offersPerPort, int offerDays, int deadlineDays, int maxActive, double rewardScale,
                          int huntMin, int huntMax, int monsterMin, int monsterMax, int turnInMax, double krakenChance,
                          long perPirate, long perNavy, long perPrisoner, long perShark, long kraken, long treasure,
                          long captain, double deliverMultiplier, int captainRadius, boolean treasureEnabled, boolean deedsTracked,
                          Map<PortKind, List<QuestType>> types, Sea sea) {

    /**
     * The sea quests' settings (QST2, {@code quests.sea_quests} and below).
     *
     * @param enabled           offer escorts, convoy raids, patrol hunts and ship hunts at all
     * @param countMin          fewest ships a convoy raid, patrol hunt or ship hunt asks for
     * @param countMax          most ships such a hunt asks for
     * @param perConvoy         reward per plundered convoy
     * @param perPatrol         reward per navy patrol sunk or captured
     * @param perShip           reward per pirate ship sunk or captured
     * @param escort            base reward of an escort
     * @param escortPer1000     escort reward per 1000 blocks between the two ports (straight line)
     * @param escortMaxDistance farthest destination (blocks, straight line) an escort goes to
     * @param escortFraction    share of the convoy's legs the player must sail close by
     * @param escortRadius      blocks from the convoy that count as close by
     */
    public record Sea(boolean enabled, int countMin, int countMax, long perConvoy, long perPatrol, long perShip, long escort,
                      long escortPer1000, int escortMaxDistance, double escortFraction, int escortRadius) {
        public static final Sea DEFAULTS = new Sea(true, 1, 3, 90, 150, 120, 120, 60, 2500, 0.5, 96);

        public Sea withEnabled(boolean on) {
            return new Sea(on, countMin, countMax, perConvoy, perPatrol, perShip, escort, escortPer1000, escortMaxDistance, escortFraction,
                    escortRadius);
        }
    }

    public static final Map<PortKind, List<QuestType>> DEFAULT_TYPES = defaultTypes();

    public static final QuestParams DEFAULTS = new QuestParams(3, 2, 5, 3, 1.0,
            3, 8, 2, 4, 2, 0.15,
            30, 35, 50, 25, 500, 100,
            400, 1.5, 3000, true, true, DEFAULT_TYPES, Sea.DEFAULTS);

    public QuestParams {
        Map<PortKind, List<QuestType>> t = new EnumMap<>(PortKind.class);
        for (PortKind k : PortKind.values()) t.put(k, List.copyOf(types.getOrDefault(k, List.of())));
        types = Collections.unmodifiableMap(t);
        if (sea == null) sea = Sea.DEFAULTS;
    }

    /** The settings without the sea quests' (their defaults). */
    public QuestParams(int offersPerPort, int offerDays, int deadlineDays, int maxActive, double rewardScale,
                       int huntMin, int huntMax, int monsterMin, int monsterMax, int turnInMax, double krakenChance,
                       long perPirate, long perNavy, long perPrisoner, long perShark, long kraken, long treasure,
                       long captain, double deliverMultiplier, int captainRadius, boolean treasureEnabled, boolean deedsTracked,
                       Map<PortKind, List<QuestType>> types) {
        this(offersPerPort, offerDays, deadlineDays, maxActive, rewardScale, huntMin, huntMax, monsterMin, monsterMax, turnInMax,
                krakenChance, perPirate, perNavy, perPrisoner, perShark, kraken, treasure, captain, deliverMultiplier, captainRadius,
                treasureEnabled, deedsTracked, types, Sea.DEFAULTS);
    }

    /** The same settings with other type lists (the operator's single offer, tests). */
    public QuestParams withTypes(Map<PortKind, List<QuestType>> other) {
        return new QuestParams(offersPerPort, offerDays, deadlineDays, maxActive, rewardScale, huntMin, huntMax, monsterMin, monsterMax,
                turnInMax, krakenChance, perPirate, perNavy, perPrisoner, perShark, kraken, treasure, captain, deliverMultiplier,
                captainRadius, treasureEnabled, deedsTracked, other, sea);
    }

    /** The same settings with other sea quest settings (tests). */
    public QuestParams withSea(Sea other) {
        return new QuestParams(offersPerPort, offerDays, deadlineDays, maxActive, rewardScale, huntMin, huntMax, monsterMin, monsterMax,
                turnInMax, krakenChance, perPirate, perNavy, perPrisoner, perShark, kraken, treasure, captain, deliverMultiplier,
                captainRadius, treasureEnabled, deedsTracked, types, other);
    }

    public List<QuestType> types(PortKind kind) {
        return types.get(kind);
    }

    private static Map<PortKind, List<QuestType>> defaultTypes() {
        Map<PortKind, List<QuestType>> m = new EnumMap<>(PortKind.class);
        m.put(PortKind.SEAFARER_VILLAGE, List.of(QuestType.DELIVER, QuestType.KILL_MONSTER, QuestType.HUNT_PIRATES, QuestType.FIND_TREASURE,
                QuestType.HUNT_CAPTAIN, QuestType.ESCORT));
        m.put(PortKind.NAVY_OUTPOST, List.of(QuestType.HUNT_PIRATES, QuestType.TURN_IN, QuestType.KILL_MONSTER, QuestType.DELIVER,
                QuestType.HUNT_CAPTAIN, QuestType.ESCORT, QuestType.HUNT_SHIP));
        m.put(PortKind.PIRATE_ISLAND, List.of(QuestType.HUNT_NAVY, QuestType.FIND_TREASURE, QuestType.DELIVER, QuestType.KILL_MONSTER,
                QuestType.PLUNDER_CONVOY, QuestType.HUNT_PATROL));
        return Collections.unmodifiableMap(m);
    }
}
