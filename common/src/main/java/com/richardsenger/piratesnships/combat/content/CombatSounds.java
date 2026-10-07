package com.richardsenger.piratesnships.combat.content;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.SoundEntries;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.sounds.SoundEvent;

import java.util.List;

/**
 * Sound events of firearms, cannons and swords (docs/design.md §8.1, §8.2, §8.5, §16). Registered with their files
 * (G1); gameplay uses them once firearms, cannons (milestone 5) and the melee input layer work. Files come from
 * {@code tools/sounds/manifest.json}; an event with several files picks one at random each time it plays. The melee
 * events play through {@code combat.melee.sound.MeleeSoundPlayer} (P7); {@code disarm} and {@code weapon_break} stay
 * unused until those mechanics exist.
 */
public final class CombatSounds {

    public static final RegistryEntry<SoundEvent, SoundEvent> PISTOL_SHOT = ModRegistry.sound("combat.pistol_shot");
    public static final RegistryEntry<SoundEvent, SoundEvent> PISTOL_EMPTY = ModRegistry.sound("combat.pistol_empty");
    public static final RegistryEntry<SoundEvent, SoundEvent> CANNON_SHOT = ModRegistry.sound("combat.cannon_shot");
    public static final RegistryEntry<SoundEvent, SoundEvent> CANNON_VOLLEY = ModRegistry.sound("combat.cannon_volley");

    public static final RegistryEntry<SoundEvent, SoundEvent> MELEE_SWING = ModRegistry.sound("combat.melee.swing");
    public static final RegistryEntry<SoundEvent, SoundEvent> MELEE_PARRY = ModRegistry.sound("combat.melee.parry");
    public static final RegistryEntry<SoundEvent, SoundEvent> MELEE_HIT_ARMOR = ModRegistry.sound("combat.melee.hit_armor");
    public static final RegistryEntry<SoundEvent, SoundEvent> MELEE_HIT_HEAVY = ModRegistry.sound("combat.melee.hit_heavy");
    public static final RegistryEntry<SoundEvent, SoundEvent> MELEE_UNSHEATHE = ModRegistry.sound("combat.melee.unsheathe");
    public static final RegistryEntry<SoundEvent, SoundEvent> MELEE_DISARM = ModRegistry.sound("combat.melee.disarm");
    public static final RegistryEntry<SoundEvent, SoundEvent> MELEE_WEAPON_BREAK = ModRegistry.sound("combat.melee.weapon_break");

    /** Event, sound files (variants) and English subtitle of every combat sound. */
    public record Def(RegistryEntry<SoundEvent, SoundEvent> event, List<String> files, String subtitle) {
        public String subtitleKey() {
            return "subtitles." + Constants.MOD_ID + "." + event.id().getPath();
        }
    }

    private static final String M = Constants.MOD_ID + ":combat/melee/";

    public static final List<Def> ALL = List.of(
            new Def(PISTOL_SHOT, List.of(Constants.MOD_ID + ":combat/pistol_shot"), "Pistol fires"),
            new Def(PISTOL_EMPTY, List.of(Constants.MOD_ID + ":combat/pistol_empty"), "Pistol clicks"),
            new Def(CANNON_SHOT, List.of(Constants.MOD_ID + ":combat/cannon_shot"), "Cannon fires"),
            new Def(CANNON_VOLLEY, List.of(Constants.MOD_ID + ":combat/cannon_volley"), "Cannons fire in the distance"),
            new Def(MELEE_SWING, List.of(M + "swing1", M + "swing2", M + "swing3", M + "swing4"), "Sword swings"),
            new Def(MELEE_PARRY, List.of(M + "parry"), "Blades clash"),
            new Def(MELEE_HIT_ARMOR, List.of(M + "hit_armor"), "Armour rings"),
            new Def(MELEE_HIT_HEAVY, List.of(M + "hit_heavy1", M + "hit_heavy2", M + "hit_heavy3"), "Sword hits"),
            new Def(MELEE_UNSHEATHE, List.of(M + "unsheathe"), "Sword drawn"),
            new Def(MELEE_DISARM, List.of(M + "disarm"), "Sword clatters to the ground"),
            new Def(MELEE_WEAPON_BREAK, List.of(M + "weapon_break"), "Sword breaks"));

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
        for (Def d : ALL) entries.event(d.event()).subtitle(d.subtitleKey()).sounds(d.files().toArray(String[]::new));
    }
}
