package com.richardsenger.piratesnships.audio.music;

/** Where the player is, as far as the sea music cares (docs/design.md §16, "Pools"). */
public enum MusicSituation {
    /** On or riding a ship, or seated at one of its stations. */
    ABOARD,
    /** Not aboard, in an ocean, deep ocean or beach biome. */
    AT_SEA,
    /** Anywhere else: vanilla music plays. */
    NONE;

    /** Aboard wins over the biome. */
    public static MusicSituation of(boolean aboard, boolean seaBiome) {
        return aboard ? ABOARD : seaBiome ? AT_SEA : NONE;
    }
}
