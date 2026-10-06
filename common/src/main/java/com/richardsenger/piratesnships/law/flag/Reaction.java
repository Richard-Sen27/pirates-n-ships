package com.richardsenger.piratesnships.law.flag;

/** How an observer treats a ship because of its flag. */
public enum Reaction {
    FRIENDLY,
    NEUTRAL,
    HOSTILE,
    /** Merchant NPC ships may surrender without a fight (the AI then runs its morale check). */
    MAY_SURRENDER
}
