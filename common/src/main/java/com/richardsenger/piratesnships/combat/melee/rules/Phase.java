package com.richardsenger.piratesnships.combat.melee.rules;

/**
 * What a combatant is doing. The parry lockout and the riposte window are timers in {@link CombatState} that run
 * alongside the phase ({@link CombatState#lockedOut()}, {@link CombatState#riposteReady()}).
 */
public enum Phase {
    IDLE,
    /** Attack telegraph, no hit yet. */
    WINDUP,
    /** Hit frames. */
    ACTIVE,
    /** After the hit frames, vulnerable (a thrust into this phase staggers). */
    RECOVERY,
    GUARDING,
    /** Parry window open. */
    PARRYING,
    /** No actions. */
    STAGGERED;

    public boolean attacking() {
        return this == WINDUP || this == ACTIVE || this == RECOVERY;
    }
}
