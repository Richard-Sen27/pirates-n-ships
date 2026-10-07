package com.richardsenger.piratesnships.law.flag;

/**
 * What the ship someone is aboard tells NPCs about them (FL2, docs/design.md §4.7 "In the world"). Input for the mob
 * hostility rules ({@code mob.HostilityRules}); only set for people aboard a ship (players and crew members), and
 * {@link #NONE} whenever {@code law.flags.enabled} is off.
 */
public enum ShipStance {
    /** Not aboard a ship, or its flag makes no difference to NPCs (none, merchant, custom, a navy flag taken at face value). */
    NONE,
    /** The ship flies the Jolly Roger: the navy attacks its people on sight, pirates leave them alone. */
    JOLLY_ROGER,
    /** The navy has seen through the ship's colours ({@code blown_cover_ticks}): it attacks whatever the ship flies. */
    UNMASKED,
    /** The ship has struck its colours (surrendered): NPC duelists and gunners do not target its people. */
    SURRENDERED;

    /**
     * The stance of a ship (pure). Surrender wins over everything (a ship that strikes its colours after being unmasked
     * has still surrendered), then a blown cover, then the Jolly Roger.
     *
     * @param shown      the flag that flies ({@code NONE} while struck or without a flag)
     * @param struck     the ship's winning flag is struck
     * @param coverBlown the navy currently sees through the ship's colours
     */
    public static ShipStance of(FlagKind shown, boolean struck, boolean coverBlown) {
        if (struck) return SURRENDERED;
        if (coverBlown) return UNMASKED;
        if (shown == FlagKind.JOLLY_ROGER) return JOLLY_ROGER;
        return NONE;
    }
}
