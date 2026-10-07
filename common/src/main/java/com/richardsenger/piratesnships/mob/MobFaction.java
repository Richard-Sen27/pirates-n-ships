package com.richardsenger.piratesnships.mob;

/** Who a humanoid mob sides with (docs/design.md §9). Pure. */
public enum MobFaction {
    /** Pirates: hostile to players (config) and to the navy. */
    PIRATE,
    /** Navy soldiers and officers: hostile to wanted players and NPCs, and to pirates. */
    NAVY,
    /** Sailors: never attack, flee from hostiles. */
    CIVILIAN
}
