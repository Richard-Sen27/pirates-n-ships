package com.richardsenger.piratesnships.rpg.quest;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;
import java.util.Optional;

/**
 * What a quest asks for (docs/design.md §15, QST1, QST1b, QST2). The four sea quests ({@link #ESCORT},
 * {@link #PLUNDER_CONVOY}, {@link #HUNT_PATROL}, {@link #HUNT_SHIP}) follow the world simulation's NPC ships (WS3b
 * endings, WS4b patrols) and are offered only while {@code quests.sea_quests} is on ({@link #seaQuest}).
 */
public enum QuestType implements StringRepresentable {
    /** Kill N pirates (counted from the {@code kill_pirate} deed). Villages and navy outposts. */
    HUNT_PIRATES(true),
    /** Kill N sharks or the kraken (a {@code LIVING_DEATH} with the player as killer). */
    KILL_MONSTER(true),
    /** Hand N shackled pirates to a navy officer (the {@code turn_in_pirate} deed). Navy outposts. */
    TURN_IN(true),
    /** Bring cargo to another port: a quest-made delivery contract with a raised reward. */
    DELIVER(true),
    /** Dig up a buried treasure: the quest hands out a bound treasure map; done when the site is looted. */
    FIND_TREASURE(true),
    /** Kill N navy sailors (the {@code kill_navy} deed; a crime as always). Pirate islands only. */
    HUNT_NAVY(true),
    /**
     * Bring down one named pirate captain (QST1b): the nearest pirate island's living captain within
     * {@code quests.captain_hunt_radius}; done when the player kills him or turns him in alive, failed when he is lost
     * otherwise. Villages and navy outposts.
     */
    HUNT_CAPTAIN(true),
    /**
     * Sail with a convoy that leaves the giving port when the quest is accepted (QST2): done when it arrives and the
     * player was within {@code quests.escort_radius} of it on at least {@code quests.escort_fraction} of its legs;
     * failed when it is sunk, captured or lost. Villages and navy outposts.
     */
    ESCORT(true),
    /** Plunder N merchant convoys (the PLUNDERED ending with the player aboard). Pirate islands. */
    PLUNDER_CONVOY(true),
    /** Sink or capture N navy patrols (SUNK credits the last shooter, CAPTURED the capturer). Pirate islands. */
    HUNT_PATROL(true),
    /** Sink or capture N pirate ships (any voyage under the pirates' colours, WS5's raiders too). Navy outposts. */
    HUNT_SHIP(true);

    public static final Codec<QuestType> CODEC = StringRepresentable.fromEnum(QuestType::values);

    private final boolean available;

    QuestType(boolean available) {
        this.available = available;
    }

    /** Whether this package can track the type (the others are never offered). */
    public boolean available() {
        return available;
    }

    /** Whether ports of {@code kind} may offer the type at all (before the config lists). */
    public boolean allowedAt(PortKind kind) {
        return switch (this) {
            case HUNT_PIRATES, HUNT_CAPTAIN -> kind != PortKind.PIRATE_ISLAND;
            case TURN_IN -> kind == PortKind.NAVY_OUTPOST;
            case HUNT_NAVY, PLUNDER_CONVOY, HUNT_PATROL -> kind == PortKind.PIRATE_ISLAND;
            case ESCORT -> kind != PortKind.PIRATE_ISLAND;
            case HUNT_SHIP -> kind == PortKind.NAVY_OUTPOST;
            default -> available;
        };
    }

    /** Whether progress comes from deeds (which are only recorded while {@code reputation.enabled} is on). */
    public boolean deedTracked() {
        return this == HUNT_PIRATES || this == TURN_IN || this == HUNT_NAVY;
    }

    /** Whether the type follows NPC ships at sea (QST2; offered only while {@code quests.sea_quests} is on). */
    public boolean seaQuest() {
        return this == ESCORT || this == PLUNDER_CONVOY || this == HUNT_PATROL || this == HUNT_SHIP;
    }

    /** Config value and command argument: {@code hunt_pirates}, {@code find_treasure}, ... */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return id();
    }

    public static Optional<QuestType> byId(String id) {
        for (QuestType t : values()) if (t.id().equals(id)) return Optional.of(t);
        return Optional.empty();
    }
}
