package com.richardsenger.piratesnships.combat.melee.net;

/** A player's melee input, as sent to the server (docs/design.md §8.5, actions table). Wire order is the ordinal. */
public enum MeleeAction {
    SLASH,
    THRUST,
    GUARD_DOWN,
    GUARD_UP,
    PARRY;

    private static final MeleeAction[] VALUES = values();

    /** The action with this ordinal, or {@code null} for an unknown one (newer or modified client). */
    public static @org.jetbrains.annotations.Nullable MeleeAction byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
    }
}
