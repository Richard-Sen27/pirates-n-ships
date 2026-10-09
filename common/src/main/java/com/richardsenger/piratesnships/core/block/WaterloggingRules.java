package com.richardsenger.piratesnships.core.block;

import java.util.Map;

/**
 * The waterlogging rule (WLOG1, docs/design.md §4.8), without world access: every block of ours whose outline or
 * collision shape is not a full cube in some state is waterloggable, except the named {@link #EXCEPTIONS}. The registry
 * walk that feeds it is {@code WaterloggingGameTests#everyPartialBlockIsWaterloggable} (mod blocks exist only with a
 * loader, so a JUnit test cannot build them); {@code WaterloggingRulesTest} covers the verdicts.
 */
public final class WaterloggingRules {

    /**
     * Partial blocks of ours that stay dry, by registry path, with the reason. Keep this list short and every entry
     * explained; an entry that no longer applies (the block became a full cube or waterloggable, or is gone) fails the
     * registry walk too.
     */
    public static final Map<String, String> EXCEPTIONS = Map.of(
            // A door (DoorBlock): vanilla doors are not waterloggable either; a two-block door would need its own
            // half-aware water handling, and the hull analysis treats every door as an opening anyway.
            "brig_door", "a door, like vanilla's doors");

    /** The outcome of the rule for one block. */
    public enum Verdict {
        /** Waterloggable, as the rule asks. */
        WATERLOGGABLE,
        /** A full cube in every state: water has no room in it, nothing to do. */
        FULL_CUBE,
        /** Partial but listed in {@link #EXCEPTIONS}. */
        EXCEPTION,
        /** Partial, not waterloggable and not listed: a violation. */
        MISSING,
        /** Listed in {@link #EXCEPTIONS} although it is waterloggable or a full cube: the list is stale. */
        STALE_EXCEPTION
    }

    private WaterloggingRules() {
    }

    /**
     * The verdict for the block {@code path}: {@code fullCube} when every state's outline and collision shape is a
     * full cube, {@code waterloggable} when it is a {@code SimpleWaterloggedBlock} with the {@code waterlogged}
     * property.
     */
    public static Verdict check(String path, boolean fullCube, boolean waterloggable) {
        boolean excepted = EXCEPTIONS.containsKey(path);
        if (excepted) {
            return fullCube || waterloggable ? Verdict.STALE_EXCEPTION : Verdict.EXCEPTION;
        }
        if (waterloggable) return Verdict.WATERLOGGABLE;
        return fullCube ? Verdict.FULL_CUBE : Verdict.MISSING;
    }

    /** Whether {@code verdict} breaks the rule. */
    public static boolean violates(Verdict verdict) {
        return verdict == Verdict.MISSING || verdict == Verdict.STALE_EXCEPTION;
    }
}
