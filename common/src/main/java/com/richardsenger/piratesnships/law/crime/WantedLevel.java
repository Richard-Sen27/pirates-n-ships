package com.richardsenger.piratesnships.law.crime;

/** How wanted an entity is, derived from its criminal score. Used by navy AI and the HUD. */
public enum WantedLevel {
    /** Below the suspect threshold. */
    CLEAN,
    /** Some crimes on record; the navy watches but does not attack. */
    SUSPECT,
    /** At or above the bounty threshold: the navy places a bounty and attacks. */
    WANTED,
    /** Far above it: hunted (more patrols later, no fines by default). */
    NOTORIOUS;

    /** Each threshold is inclusive: a score equal to it already reaches that level. */
    public static WantedLevel of(double score, double suspect, double wanted, double notorious) {
        if (score >= notorious) return NOTORIOUS;
        if (score >= wanted) return WANTED;
        if (score >= suspect && score > 0) return SUSPECT;
        return CLEAN;
    }

    public boolean atLeast(WantedLevel other) {
        return ordinal() >= other.ordinal();
    }
}
