package com.richardsenger.piratesnships.worldsim.materialize;

import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.worldsim.faction.FactionEvent;

import java.util.Locale;

/**
 * A player's deed against an NPC ship (WS3b). {@link VoyageEndings} records it as the REP1 reputation deed
 * {@link #reputationDeed()} (when the player is online) and reports its faction side through
 * {@code Factions.reportDeed} with {@link #factionEvent()} (there is no deed-to-faction adapter on main yet).
 */
public enum VoyageDeed {
    SINK_MERCHANT(Faction.MERCHANTS),
    SINK_NAVY(Faction.NAVY),
    SINK_PIRATE(Faction.PIRATES),
    CAPTURE_MERCHANT(Faction.MERCHANTS),
    CAPTURE_NAVY(Faction.NAVY),
    CAPTURE_PIRATE(Faction.PIRATES),
    PLUNDER_MERCHANT(Faction.MERCHANTS);

    private final Faction victim;

    VoyageDeed(Faction victim) {
        this.victim = victim;
    }

    /** The faction the deed was done against. */
    public Faction victim() {
        return victim;
    }

    /** The REP1 deed id ({@code sink_merchant}, …). */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Sinking a ship of {@code faction}. */
    public static VoyageDeed sink(Faction faction) {
        return switch (faction) {
            case MERCHANTS -> SINK_MERCHANT;
            case NAVY -> SINK_NAVY;
            case PIRATES -> SINK_PIRATE;
        };
    }

    /** Capturing a ship of {@code faction}. */
    public static VoyageDeed capture(Faction faction) {
        return switch (faction) {
            case MERCHANTS -> CAPTURE_MERCHANT;
            case NAVY -> CAPTURE_NAVY;
            case PIRATES -> CAPTURE_PIRATE;
        };
    }

    /**
     * The reputation deed (REP1) recorded for this act; empty for {@link #CAPTURE_MERCHANT}, which the law reports as
     * the crime {@code piracy} and REP1's {@code LawDeeds} turns into {@code plunder_merchant}.
     */
    public java.util.Optional<com.richardsenger.piratesnships.rpg.deeds.Deed> reputationDeed() {
        return java.util.Optional.ofNullable(switch (this) {
            case SINK_MERCHANT -> com.richardsenger.piratesnships.rpg.deeds.Deed.SINK_MERCHANT;
            case SINK_NAVY -> com.richardsenger.piratesnships.rpg.deeds.Deed.SINK_NAVY;
            case SINK_PIRATE -> com.richardsenger.piratesnships.rpg.deeds.Deed.SINK_PIRATE;
            case CAPTURE_MERCHANT -> null;
            case CAPTURE_NAVY -> com.richardsenger.piratesnships.rpg.deeds.Deed.CAPTURE_NAVY;
            case CAPTURE_PIRATE -> com.richardsenger.piratesnships.rpg.deeds.Deed.CAPTURE_PIRATE;
            case PLUNDER_MERCHANT -> com.richardsenger.piratesnships.rpg.deeds.Deed.PLUNDER_MERCHANT;
        });
    }

    /** The faction event that describes this deed's effect on the faction state. */
    public FactionEvent factionEvent() {
        return this == PLUNDER_MERCHANT ? FactionEvent.MERCHANT_PLUNDERED : EndingRules.lostEvent(victim);
    }
}
