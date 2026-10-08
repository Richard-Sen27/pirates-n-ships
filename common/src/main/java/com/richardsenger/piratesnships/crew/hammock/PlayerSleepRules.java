package com.richardsenger.piratesnships.crew.hammock;

/**
 * Whether a player may lie down in a hammock or in a cot on a ship (SLP1, docs/design.md §7.1). Pure: the caller
 * measures, this decides in vanilla's order ({@code BedBlock#useWithoutItem}, then
 * {@code ServerPlayer#startSleepInBed}), so the same refusals come up as at a bed on land.
 */
public final class PlayerSleepRules {

    /** Why a player cannot lie down, or {@link #NONE}. */
    public enum Refusal {
        NONE,
        /** Already asleep or dead: silent, as in vanilla. */
        OTHER,
        /** A crew member sleeps in this hammock. */
        CREW_IN_IT,
        /** Another player sleeps in it. */
        OCCUPIED,
        /** A dimension without day and night (vanilla's "not possible here"). */
        NOT_HERE,
        TOO_FAR_AWAY,
        OBSTRUCTED,
        /** Daytime without a thunderstorm. */
        NOT_NOW,
        /** Monsters nearby. */
        NOT_SAFE
    }

    /** What the caller measured at the bunk. */
    public record Check(boolean sleepingOrDead, boolean crewInIt, boolean occupied, boolean naturalDimension,
                        boolean inReach, boolean obstructed, boolean day, boolean monstersNear, boolean creative) { }

    /** Vanilla's reach for a bed: 3 blocks across, 2 up or down, from the bottom centre of either half. */
    public static final double REACH_XZ = 3.0, REACH_Y = 2.0;

    private PlayerSleepRules() {
    }

    /** The first refusal in vanilla's order (the bed's own occupied check first), or {@link Refusal#NONE}. */
    public static Refusal refusal(Check c) {
        if (c.crewInIt()) return Refusal.CREW_IN_IT;
        if (c.occupied()) return Refusal.OCCUPIED;
        if (c.sleepingOrDead()) return Refusal.OTHER;
        if (!c.naturalDimension()) return Refusal.NOT_HERE;
        if (!c.inReach()) return Refusal.TOO_FAR_AWAY;
        if (c.obstructed()) return Refusal.OBSTRUCTED;
        if (c.day()) return Refusal.NOT_NOW;
        if (c.monstersNear() && !c.creative()) return Refusal.NOT_SAFE;
        return Refusal.NONE;
    }

    /**
     * Whether the use sets the respawn point: vanilla sets it once the bed is free, reachable and clear, before it
     * looks at the clock and for monsters, so a bed used by day still sets the spawn.
     */
    public static boolean setsSpawn(Refusal r) {
        return r == Refusal.NONE || r == Refusal.NOT_NOW || r == Refusal.NOT_SAFE;
    }

    /**
     * Whether a player {@code dx, dy, dz} away from the bottom centre of a bunk half (in the bunk's own frame: the
     * ship's plot on a ship) reaches it.
     */
    public static boolean inReach(double dx, double dy, double dz) {
        return Math.abs(dx) <= REACH_XZ && Math.abs(dy) <= REACH_Y && Math.abs(dz) <= REACH_XZ;
    }
}
