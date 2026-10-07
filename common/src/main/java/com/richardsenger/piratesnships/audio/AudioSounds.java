package com.richardsenger.piratesnships.audio;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import java.util.List;
import net.minecraft.sounds.SoundEvent;

/** Sound events of the {@code audio} module. Their {@code sounds.json} entries come from {@link AudioModule#gatherData}. */
public final class AudioSounds {

    /** The creak of a rolling ship's planks (docs/design.md §16). Sound files: see {@link AudioModule}. */
    public static final RegistryEntry<SoundEvent, SoundEvent> SHIP_CREAK = ModRegistry.sound("ship.creak");

    /** Ambient sea music, played at sea but not aboard (docs/design.md §16, pool {@code music.sea}). */
    public static final RegistryEntry<SoundEvent, SoundEvent> MUSIC_SEA = ModRegistry.sound("music.sea");
    /** Shanties, played aboard a ship (pool {@code music.shanty}). */
    public static final RegistryEntry<SoundEvent, SoundEvent> MUSIC_SHANTY = ModRegistry.sound("music.shanty");

    /** Sound files of {@code music.sea} (from tools/sounds/manifest.json). */
    public static final List<String> SEA_TRACKS = List.of(
            "pirates_n_ships:music/sea/there_be_pirates_the_quest",
            "pirates_n_ships:music/sea/there_be_pirates_lost_in_the_deep");
    /** Sound files of {@code music.shanty}. */
    public static final List<String> SHANTY_TRACKS = List.of(
            "pirates_n_ships:music/shanty/leave_her_johnny",
            "pirates_n_ships:music/shanty/ghost_of_gallows_reef");

    private AudioSounds() {
    }

    /** Loads the class so the entries above are registered. Called from {@code registerContent()}. */
    public static void init() {
    }
}
