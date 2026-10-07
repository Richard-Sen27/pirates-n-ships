package com.richardsenger.piratesnships.combat.cannon;

/** What goes into a swivel gun after the powder (config {@code cannons.swivel.ammo}, docs/design.md §8.2, P2). */
public enum SwivelAmmo {
    /** One cannonball (the default): the same item as the big gun's. */
    CANNONBALL,
    /** A handful of lead shot, the firearms' ammunition. */
    LEAD_SHOT
}
