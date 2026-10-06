package com.richardsenger.piratesnships.combat.content;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.SoundEntries;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.sounds.SoundEvent;

import java.util.List;

/**
 * Sound events of firearms and cannons (docs/design.md §8.1, §8.2, §16). Registered with their files (G1); gameplay
 * uses them once firearms and cannons work (milestone 5). Files come from {@code tools/sounds/manifest.json}.
 */
public final class CombatSounds {

    public static final RegistryEntry<SoundEvent, SoundEvent> PISTOL_SHOT = ModRegistry.sound("combat.pistol_shot");
    public static final RegistryEntry<SoundEvent, SoundEvent> PISTOL_EMPTY = ModRegistry.sound("combat.pistol_empty");
    public static final RegistryEntry<SoundEvent, SoundEvent> CANNON_SHOT = ModRegistry.sound("combat.cannon_shot");
    public static final RegistryEntry<SoundEvent, SoundEvent> CANNON_VOLLEY = ModRegistry.sound("combat.cannon_volley");

    /** Event, sound file and English subtitle of every combat sound. */
    record Def(RegistryEntry<SoundEvent, SoundEvent> event, String file, String subtitle) {
        String subtitleKey() {
            return "subtitles." + Constants.MOD_ID + "." + event.id().getPath();
        }
    }

    static final List<Def> ALL = List.of(
            new Def(PISTOL_SHOT, "pirates_n_ships:combat/pistol_shot", "Pistol fires"),
            new Def(PISTOL_EMPTY, "pirates_n_ships:combat/pistol_empty", "Pistol clicks"),
            new Def(CANNON_SHOT, "pirates_n_ships:combat/cannon_shot", "Cannon fires"),
            new Def(CANNON_VOLLEY, "pirates_n_ships:combat/cannon_volley", "Cannons fire in the distance"));

    private CombatSounds() {
    }

    /** Loads the class so the entries above are registered. Called from {@code registerContent()}. */
    public static void init() {
    }

    static void gather(DataContributions data) {
        data.sounds(CombatSounds::sounds);
        data.lang(lang -> ALL.forEach(d -> lang.add(d.subtitleKey(), d.subtitle())));
    }

    static void sounds(SoundEntries entries) {
        for (Def d : ALL) entries.event(d.event()).subtitle(d.subtitleKey()).sounds(d.file());
    }
}
