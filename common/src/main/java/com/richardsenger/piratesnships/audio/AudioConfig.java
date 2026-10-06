package com.richardsenger.piratesnships.audio;

import com.richardsenger.piratesnships.audio.music.MusicSelector;
import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Client config section {@code audio} (docs/design.md §17, group "Audio"; §16), declared by {@link AudioModule}.
 * The music values are read by {@code audio.music.SeaMusic} through {@link #musicSettings()}. {@code ambience_volume}
 * is not read yet: the hull creak is played by the server through vanilla's {@code playSound}, so it follows the
 * player's "Blocks" volume slider; the ambience volume needs client-side sound playback (later).
 */
public final class AudioConfig {

    private static final ConfigSection S = ModConfigs.client("audio", "Sea music and ambience");

    public static final ConfigValue<Boolean> MUSIC_ENABLED = S.bool("music_enabled", true,
            "Play the mod's sea, shanty, battle and harbor music. Off = only vanilla music plays");
    public static final ConfigValue<Double> MUSIC_VOLUME = S.doubleRange("music_volume", 1.0, 0.0, 1.0,
            "Volume of the mod's music, on top of the game's music volume slider (0 to 1)");
    public static final ConfigValue<Boolean> SHANTIES_ABOARD = S.bool("shanties_aboard", true,
            "Aboard a ship the shanties play. Off = the ambient sea music plays aboard too");
    public static final ConfigValue<Integer> MIN_GAP_SECONDS = S.intRange("min_gap_seconds", 120, 0, 3600,
            "Shortest silence between two of the mod's music tracks, in seconds");
    public static final ConfigValue<Integer> MAX_GAP_SECONDS = S.intRange("max_gap_seconds", 300, 0, 3600,
            "Longest silence between two of the mod's music tracks, in seconds (raised to min_gap_seconds if lower)");
    public static final ConfigValue<Double> AMBIENCE_VOLUME = S.doubleRange("ambience_volume", 1.0, 0.0, 1.0,
            "Volume of the mod's sea ambience such as creaking hulls, wind and waves (0 to 1)");

    private AudioConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    /** Current music settings from the client config. */
    public static MusicSelector.Settings musicSettings() {
        return new MusicSelector.Settings(MUSIC_ENABLED.get(), SHANTIES_ABOARD.get(), MIN_GAP_SECONDS.get(), MAX_GAP_SECONDS.get());
    }
}
