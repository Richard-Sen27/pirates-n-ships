package com.richardsenger.piratesnships.audio.music;

import org.jetbrains.annotations.Nullable;

/**
 * Maps a {@link MusicSituation} and the client music settings to the music pool that should play (docs/design.md §16,
 * "Pools"). Pure: no Minecraft classes, tested by {@code MusicSelectorTest}.
 */
public final class MusicSelector {

    private static final int TICKS_PER_SECOND = 20;

    /** The two music pools of G1: {@code music.sea} and {@code music.shanty}. */
    public enum Pool { SEA, SHANTY }

    /**
     * Client config values, see {@code AudioConfig}.
     *
     * @param enabled        {@code music_enabled}; off = never override vanilla
     * @param shantiesAboard {@code shanties_aboard}; off = the sea pool plays aboard too
     * @param minGapSeconds  {@code min_gap_seconds}: shortest silence between two tracks
     * @param maxGapSeconds  {@code max_gap_seconds}: longest silence between two tracks (raised to the minimum if lower)
     */
    public record Settings(boolean enabled, boolean shantiesAboard, int minGapSeconds, int maxGapSeconds) {
    }

    /** The pool to play and the gap before the next track, in ticks (what vanilla's {@code Music} needs). */
    public record Choice(Pool pool, int minGapTicks, int maxGapTicks) {
    }

    private MusicSelector() {
    }

    /** The music to play instead of vanilla's choice, or null to leave vanilla's music alone. */
    public static @Nullable Choice select(MusicSituation situation, Settings settings) {
        if (!settings.enabled()) return null;
        Pool pool = switch (situation) {
            case ABOARD -> settings.shantiesAboard() ? Pool.SHANTY : Pool.SEA;
            case AT_SEA -> Pool.SEA;
            case NONE -> null;
        };
        if (pool == null) return null;
        int min = Math.max(0, settings.minGapSeconds()) * TICKS_PER_SECOND;
        int max = Math.max(min, Math.max(0, settings.maxGapSeconds()) * TICKS_PER_SECOND);
        return new Choice(pool, min, max);
    }

    /**
     * Channel volume of one of our music tracks: what vanilla's sound engine sets (the sound's own volume times the
     * game's music slider, clamped to 0..1) scaled by {@code music_volume}.
     */
    public static float channelVolume(float soundVolume, float musicSlider, double musicVolume) {
        float vanilla = Math.max(0f, Math.min(1f, soundVolume * musicSlider));
        return (float) (vanilla * Math.max(0.0, Math.min(1.0, musicVolume)));
    }
}
