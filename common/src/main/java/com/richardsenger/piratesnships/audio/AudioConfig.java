package com.richardsenger.piratesnships.audio;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Client config section {@code audio} (docs/design.md §17, group "Audio"; §16), declared by {@link AudioModule}.
 * Nothing reads these values yet: the hull creak is played by the server through vanilla's {@code playSound}, so it
 * follows the player's "Blocks" volume slider; {@code ambience_volume} needs client-side sound playback (later).
 */
public final class AudioConfig {

    private static final ConfigSection S = ModConfigs.client("audio", "Sea music and ambience");

    public static final ConfigValue<Boolean> MUSIC_ENABLED = S.bool("music_enabled", true,
            "Play the mod's sea, shanty, battle and harbor music. Off = only vanilla music plays");
    public static final ConfigValue<Double> MUSIC_VOLUME = S.doubleRange("music_volume", 1.0, 0.0, 1.0,
            "Volume of the mod's music, on top of the game's music volume slider (0 to 1)");
    public static final ConfigValue<Double> AMBIENCE_VOLUME = S.doubleRange("ambience_volume", 1.0, 0.0, 1.0,
            "Volume of the mod's sea ambience such as creaking hulls, wind and waves (0 to 1)");

    private AudioConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
