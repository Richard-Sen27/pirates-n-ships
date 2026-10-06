package com.richardsenger.piratesnships.audio;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.sounds.SoundEvent;

/** Sound events of the {@code audio} module. Their {@code sounds.json} entries come from {@link AudioModule#gatherData}. */
public final class AudioSounds {

    /** The creak of a rolling ship's planks (docs/design.md §16). Placeholder sounds: see {@link AudioModule}. */
    public static final RegistryEntry<SoundEvent, SoundEvent> SHIP_CREAK = ModRegistry.sound("ship.creak");

    private AudioSounds() {
    }

    /** Loads the class so the entries above are registered. Called from {@code registerContent()}. */
    public static void init() {
    }
}
