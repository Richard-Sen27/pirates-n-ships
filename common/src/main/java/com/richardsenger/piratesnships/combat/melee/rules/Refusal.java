package com.richardsenger.piratesnships.combat.melee.rules;

/** Why an input was not accepted. */
public enum Refusal {
    /** Accepted. */
    NONE,
    /** Skill-based combat is switched off in the config. */
    DISABLED,
    /** Another action is in progress (attacking, parrying, or the input makes no sense right now). */
    BUSY,
    /** Not enough stamina (attacks need their full cost, guard and parry need more than zero). */
    NO_STAMINA,
    /** A failed parry blocks the next one for a moment. */
    LOCKED_OUT,
    /** Staggered: no actions. */
    STAGGERED
}
