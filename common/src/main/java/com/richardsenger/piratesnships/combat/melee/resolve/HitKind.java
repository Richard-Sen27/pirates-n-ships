package com.richardsenger.piratesnships.combat.melee.resolve;

/** Source of an incoming hit. Melee kinds can be parried and guarded; projectiles and other damage can't. */
public enum HitKind {
    /** A slash or thrust of a skill-based sword. */
    MOD_MELEE,
    /** A vanilla melee hit (mob or player attack without the skill system). */
    VANILLA_MELEE,
    /** Arrows, pistol and musket shots, ... */
    PROJECTILE,
    /** Fall, fire, explosions, magic, ... */
    OTHER;

    public boolean melee() {
        return this == MOD_MELEE || this == VANILLA_MELEE;
    }
}
