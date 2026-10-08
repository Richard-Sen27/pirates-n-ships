package com.richardsenger.piratesnships.law.crime;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.util.StringRepresentable;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * The crime catalogue (docs/design.md §13.1, §4.7, §13.3, §15). Each crime has a default severity (criminal score
 * points) and a default repeat cooldown: the same crime against the same victim within the cooldown is not counted
 * again, so one sword fight is one crime, not twenty (see {@link CriminalRecord#addCrime}). Both are config values
 * ({@code law.severity.*}, {@code law.repeat_cooldown_seconds.*}); the numbers here are only the defaults.
 */
public enum CrimeType implements StringRepresentable {
    /** Hitting a navy sailor, officer or navy NPC. */
    ATTACK_NAVY("attack_navy", 10, 30),
    /** Killing a navy sailor or officer. Kills are never deduplicated: each victim dies once. */
    KILL_NAVY("kill_navy", 30, 0),
    /** Hitting a villager. */
    ATTACK_VILLAGER("attack_villager", 5, 30),
    /** Killing a villager. */
    KILL_VILLAGER("kill_villager", 20, 0),
    /** Firing on / ramming a ship that flies a neutral or merchant flag. Victim = the ship. */
    ATTACK_NEUTRAL_SHIP("attack_neutral_ship", 15, 120),
    /** Taking items from a village chest. Victim = the chest owner / village, if known. */
    THEFT("theft", 5, 60),
    /** Capturing a non-pirate ship. Victim = the ship. */
    PIRACY("piracy", 50, 600),
    /** Being seen by navy or merchants while flying the Jolly Roger. Usually reported without victim. */
    SEEN_UNDER_JOLLY_ROGER("seen_under_jolly_roger", 10, 300),
    /** Being caught flying false colors. "Raises the criminal score heavily" (§4.7). */
    CAUGHT_FALSE_COLORS("caught_false_colors", 40, 300),
    /** Attacking a ship that has struck its colors. Victim = the ship. */
    ATTACK_STRUCK_COLORS("attack_struck_colors", 40, 120),
    /** Press-ganging a captured sailor into the crew (§13.3). Victim = the prisoner. */
    PRESS_GANG("press_gang", 15, 0),
    /** Attacking navy or merchant ships while in navy service (§15). */
    DESERTION("desertion", 60, 600),
    /**
     * <b>Legacy</b> (G13, produced by nothing since LAW3): selling plundered goods that a navy port noticed. Since LAW3
     * ports refuse plunder instead ({@link #SELLING_PLUNDER}); the constant only stays so saved criminal records that
     * hold it still load. Never offered as a crime one can commit ({@link #committable()}).
     */
    FENCE_PLUNDER("fence_plunder", 15, 60),
    /**
     * Offering plunder-marked goods at a village or navy outpost desk, which refuses them and reports the seller
     * (LAW3, §13.4). Victim = the port ({@code law.world.PlunderCrimes#portVictim}); the repeat window of one in-game
     * day (1200 s) keeps it to the first refusal per player, port and day. Default points: a third of {@link #PIRACY}.
     */
    SELLING_PLUNDER("selling_plunder", 17, 1200),
    /**
     * A navy observer saw more than {@code law.plunder_notice_units} plunder-marked units in a ship's containers (LAW3,
     * §13.4). Victim = the ship; the repeat window of one in-game day keeps it to once per ship and day.
     */
    SUSPECTED_PIRACY("suspected_piracy", 15, 1200);

    public static final Codec<CrimeType> CODEC = StringRepresentable.fromEnum(CrimeType::values);

    private final String id;
    private final int defaultSeverity;
    private final int defaultCooldownSeconds;

    CrimeType(String id, int defaultSeverity, int defaultCooldownSeconds) {
        this.id = id;
        this.defaultSeverity = defaultSeverity;
        this.defaultCooldownSeconds = defaultCooldownSeconds;
    }

    /** snake_case id, used in config paths, saves and commands. */
    public String id() {
        return id;
    }

    /** Translation key of the crime's display name ("Offering stolen goods"). */
    public String nameKey() {
        return "crime." + Constants.MOD_ID + "." + id;
    }

    public int defaultSeverity() {
        return defaultSeverity;
    }

    public int defaultCooldownSeconds() {
        return defaultCooldownSeconds;
    }

    /** Kept only so old saves load; nothing records it any more (LAW3b). */
    public boolean legacy() {
        return this == FENCE_PLUNDER;
    }

    /** The crimes the game can still record, in catalogue order (what commands suggest and accept). */
    public static Stream<CrimeType> committable() {
        return Arrays.stream(values()).filter(t -> !t.legacy());
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
