package com.richardsenger.piratesnships.worldsim.materialize;

import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.worldsim.faction.FactionEvent;

import java.util.Locale;

/**
 * A player's deed against an NPC ship (WS3b). The reputation layer (REP1, {@code rpg/deeds/Deeds}) records these
 * under the same ids once it is merged ({@link VoyageEndings#setDeedRecorder}); until then the faction side of the deed
 * is reported through {@code Factions.reportDeed} with {@link #factionEvent()}.
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

    /** The faction event that describes this deed's effect on the faction state. */
    public FactionEvent factionEvent() {
        return this == PLUNDER_MERCHANT ? FactionEvent.MERCHANT_PLUNDERED : EndingRules.lostEvent(victim);
    }
}
