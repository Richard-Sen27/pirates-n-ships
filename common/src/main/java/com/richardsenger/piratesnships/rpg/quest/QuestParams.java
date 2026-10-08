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
 */
public record QuestParams(int offersPerPort, int offerDays, int deadlineDays, int maxActive, double rewardScale,
                          int huntMin, int huntMax, int monsterMin, int monsterMax, int turnInMax, double krakenChance,
                          long perPirate, long perNavy, long perPrisoner, long perShark, long kraken, long treasure,
                          long captain, double deliverMultiplier, int captainRadius, boolean treasureEnabled, boolean deedsTracked,
                          Map<PortKind, List<QuestType>> types) {

    public static final Map<PortKind, List<QuestType>> DEFAULT_TYPES = defaultTypes();

    public static final QuestParams DEFAULTS = new QuestParams(3, 2, 5, 3, 1.0,
            3, 8, 2, 4, 2, 0.15,
            30, 35, 50, 25, 500, 100,
            400, 1.5, 3000, true, true, DEFAULT_TYPES);

    public QuestParams {
        Map<PortKind, List<QuestType>> t = new EnumMap<>(PortKind.class);
        for (PortKind k : PortKind.values()) t.put(k, List.copyOf(types.getOrDefault(k, List.of())));
        types = Collections.unmodifiableMap(t);
    }

    /** The same settings with other type lists (the operator's single offer, tests). */
    public QuestParams withTypes(Map<PortKind, List<QuestType>> other) {
        return new QuestParams(offersPerPort, offerDays, deadlineDays, maxActive, rewardScale, huntMin, huntMax, monsterMin, monsterMax,
                turnInMax, krakenChance, perPirate, perNavy, perPrisoner, perShark, kraken, treasure, captain, deliverMultiplier,
                captainRadius, treasureEnabled, deedsTracked, other);
    }

    public List<QuestType> types(PortKind kind) {
        return types.get(kind);
    }

    private static Map<PortKind, List<QuestType>> defaultTypes() {
        Map<PortKind, List<QuestType>> m = new EnumMap<>(PortKind.class);
        m.put(PortKind.SEAFARER_VILLAGE, List.of(QuestType.DELIVER, QuestType.KILL_MONSTER, QuestType.HUNT_PIRATES, QuestType.FIND_TREASURE,
                QuestType.HUNT_CAPTAIN));
        m.put(PortKind.NAVY_OUTPOST, List.of(QuestType.HUNT_PIRATES, QuestType.TURN_IN, QuestType.KILL_MONSTER, QuestType.DELIVER,
                QuestType.HUNT_CAPTAIN));
        m.put(PortKind.PIRATE_ISLAND, List.of(QuestType.HUNT_NAVY, QuestType.FIND_TREASURE, QuestType.DELIVER, QuestType.KILL_MONSTER));
        return Collections.unmodifiableMap(m);
    }
}
