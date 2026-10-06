package com.richardsenger.piratesnships.law.world;

import com.richardsenger.piratesnships.law.crime.WantedLevel;

/**
 * Pure answer to "should this navy entity attack that entity?" (docs/design.md §9: navy soldiers are "hostile to
 * players with a bounty"). The world-facing entry point is {@code LawService.navyShouldAttack}.
 */
public final class NavyHostility {

    private NavyHostility() {
    }

    /**
     * @param lawEnabled   criminal score enabled in config (off = nobody is wanted, the navy attacks nobody)
     * @param threshold    lowest wanted level the navy attacks
     * @param targetLevel  the target's wanted level
     * @param targetIsNavy the target is navy itself (never attacked, whatever its record)
     * @param sameEntity   navy and target are the same entity
     * @param exempt       the target is a creative or spectator player
     */
    public static boolean shouldAttack(boolean lawEnabled, WantedLevel threshold, WantedLevel targetLevel,
                                       boolean targetIsNavy, boolean sameEntity, boolean exempt) {
        if (!lawEnabled || sameEntity || targetIsNavy || exempt) return false;
        return targetLevel.atLeast(threshold);
    }
}
