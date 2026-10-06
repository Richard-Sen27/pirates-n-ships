package com.richardsenger.piratesnships.combat.melee.rules;

/** Result of applying an input: the new state (unchanged when refused) and the reason for a refusal. */
public record InputResult(CombatState state, Refusal refusal) {

    public static InputResult accepted(CombatState state) {
        return new InputResult(state, Refusal.NONE);
    }

    public static InputResult refused(CombatState state, Refusal why) {
        return new InputResult(state, why);
    }

    public boolean accepted() {
        return refusal == Refusal.NONE;
    }
}
