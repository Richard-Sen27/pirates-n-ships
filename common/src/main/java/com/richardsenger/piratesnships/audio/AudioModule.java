package com.richardsenger.piratesnships.audio;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.audio.creak.ShipCreaks;
import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.SoundEntries;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import java.util.List;

/**
 * The {@code audio} module (docs/design.md §16): the mod's sound events and the hull creaking of rolling ships. Owns
 * the client section {@code audio} ({@link AudioConfig}, taken over from {@code core.settings}) and the server section
 * {@code hull_creaking} ({@link CreakConfig}).
 *
 * <h2>Hull creak</h2>
 * {@code pirates_n_ships:ship.creak} plays one of {@link #CREAK_SOUNDS} with equal weight: two slices of one Pixabay
 * wood creak recording, cut and loudness-normalised by {@code tools/convert_sounds.py} (entries in
 * {@code tools/sounds/manifest.json}). The trigger pitches them down (0.5 to 0.8) and varies the volume, so repeated
 * creaks don't sound alike. A resource pack can still replace them with its own {@code "ship.creak"} entry
 * ({@code "replace": true}).
 *
 * <h2>Music</h2>
 * {@code music.sea} and {@code music.shanty} ({@link AudioSounds}) are chosen on the client by
 * {@code audio.music.SeaMusic} (§16, "Pools"). Their tracks are streamed.
 */
public final class AudioModule implements ModModule {

    /** Sound files of {@code ship.creak} (from tools/sounds/manifest.json), equal weights, full volume. */
    static final List<String> CREAK_SOUNDS = List.of(
            Constants.MOD_ID + ":ship/creak_1", Constants.MOD_ID + ":ship/creak_2");
    static final String CREAK_SUBTITLE = "subtitles." + Constants.MOD_ID + ".ship.creak";

    @Override
    public String id() {
        return "audio";
    }

    @Override
    public void registerConfig() {
        AudioConfig.init();
        CreakConfig.init();
    }

    @Override
    public void registerContent() {
        AudioSounds.init();
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.audio.music.SeaMusic.init();
    }

    @Override
    public void registerEvents() {
        CommonEvents.LEVEL_TICK_END.register(ShipCreaks::onLevelTick);
        CommonEvents.SERVER_STOPPED.register(server -> ShipCreaks.onServerStopped());
        SableShips.onShipRemoved((level, ship, destroyed) -> ShipCreaks.onShipRemoved(level, ship));
    }

    @Override
    public void gatherData(DataContributions data) {
        data.sounds(AudioModule::sounds);
        data.lang(lang -> lang.add(CREAK_SUBTITLE, "Ship creaks"));
    }

    static void sounds(SoundEntries s) {
        SoundEntries.Event creak = s.event(AudioSounds.SHIP_CREAK).subtitle(CREAK_SUBTITLE);
        for (String name : CREAK_SOUNDS) creak.sound(SoundEntries.file(name));
        SoundEntries.Event sea = s.event(AudioSounds.MUSIC_SEA);
        for (String name : AudioSounds.SEA_TRACKS) sea.sound(SoundEntries.file(name).stream());
        SoundEntries.Event shanty = s.event(AudioSounds.MUSIC_SHANTY);
        for (String name : AudioSounds.SHANTY_TRACKS) shanty.sound(SoundEntries.file(name).stream());
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(AudioGameTests.class);
    }
}
