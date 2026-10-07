package com.richardsenger.piratesnships.combat.content;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.SoundEntries;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.sounds.SoundEvent;

import java.util.Arrays;
import java.util.List;

/**
 * Sound events of firearms, cannons and swords (docs/design.md §8.1, §8.2, §8.5, §16). Registered with their files
 * (G1); gameplay uses them once firearms, cannons (milestone 5) and the melee input layer work. Files come from
 * {@code tools/sounds/manifest.json}; an event with several files picks one at random each time it plays (equal
 * weights). The melee events play through {@code combat.melee.sound.MeleeSoundPlayer}, one per attack by its outcome
 * (P8): {@code miss} ("Sword swipes": six single whooshes cut from one clip, A2, played lower for a thrust),
 * {@code hit} ("Sword Slice"), {@code hit_heavy} ("Violent Sword Slice"), {@code clash} ("Sword Clashhit" and single
 * clangs cut from the two "Sword Fight" clips), {@code hit_armor}; {@code disarm} and {@code weapon_break} stay unused
 * until those mechanics exist.
 */
public final class CombatSounds {

    public static final RegistryEntry<SoundEvent, SoundEvent> PISTOL_SHOT = ModRegistry.sound("combat.pistol_shot");
    public static final RegistryEntry<SoundEvent, SoundEvent> PISTOL_EMPTY = ModRegistry.sound("combat.pistol_empty");
    public static final RegistryEntry<SoundEvent, SoundEvent> CANNON_SHOT = ModRegistry.sound("combat.cannon_shot");
    public static final RegistryEntry<SoundEvent, SoundEvent> CANNON_VOLLEY = ModRegistry.sound("combat.cannon_volley");

    public static final RegistryEntry<SoundEvent, SoundEvent> MELEE_MISS = ModRegistry.sound("combat.melee.miss");
    public static final RegistryEntry<SoundEvent, SoundEvent> MELEE_HIT = ModRegistry.sound("combat.melee.hit");
    public static final RegistryEntry<SoundEvent, SoundEvent> MELEE_CLASH = ModRegistry.sound("combat.melee.clash");
    public static final RegistryEntry<SoundEvent, SoundEvent> MELEE_HIT_ARMOR = ModRegistry.sound("combat.melee.hit_armor");
    public static final RegistryEntry<SoundEvent, SoundEvent> MELEE_HIT_HEAVY = ModRegistry.sound("combat.melee.hit_heavy");
    public static final RegistryEntry<SoundEvent, SoundEvent> MELEE_UNSHEATHE = ModRegistry.sound("combat.melee.unsheathe");
    public static final RegistryEntry<SoundEvent, SoundEvent> MELEE_DISARM = ModRegistry.sound("combat.melee.disarm");
    public static final RegistryEntry<SoundEvent, SoundEvent> MELEE_WEAPON_BREAK = ModRegistry.sound("combat.melee.weapon_break");

    /** Event, sounds (variants: our files, or another sound event) and English subtitle of every combat sound. */
    public record Def(RegistryEntry<SoundEvent, SoundEvent> event, List<SoundEntries.Sound> sounds, String subtitle) {

        Def(RegistryEntry<SoundEvent, SoundEvent> event, String subtitle, String... files) {
            this(event, Arrays.stream(files).map(SoundEntries::file).toList(), subtitle);
        }

        public String subtitleKey() {
            return "subtitles." + Constants.MOD_ID + "." + event.id().getPath();
        }
    }

    private static final String M = Constants.MOD_ID + ":combat/melee/";

    public static final List<Def> ALL = List.of(
            new Def(PISTOL_SHOT, "Pistol fires", Constants.MOD_ID + ":combat/pistol_shot"),
            new Def(PISTOL_EMPTY, "Pistol clicks", Constants.MOD_ID + ":combat/pistol_empty"),
            new Def(CANNON_SHOT, "Cannon fires", Constants.MOD_ID + ":combat/cannon_shot"),
            new Def(CANNON_VOLLEY, "Cannons fire in the distance", Constants.MOD_ID + ":combat/cannon_volley"),
            new Def(MELEE_MISS, "Sword misses", M + "miss_1", M + "miss_2", M + "miss_3", M + "miss_4", M + "miss_5", M + "miss_6"),
            new Def(MELEE_HIT, "Sword hits", M + "hit1", M + "hit2"),
            new Def(MELEE_HIT_HEAVY, "Sword hits", M + "hit_heavy1", M + "hit_heavy2"),
            new Def(MELEE_CLASH, "Blades clash", M + "clash1", M + "clash2", M + "clash3", M + "clash4", M + "clash5", M + "clash6"),
            new Def(MELEE_HIT_ARMOR, "Armour rings", M + "hit_armor"),
            new Def(MELEE_UNSHEATHE, "Sword drawn", M + "unsheathe"),
            new Def(MELEE_DISARM, "Sword clatters to the ground", M + "disarm"),
            new Def(MELEE_WEAPON_BREAK, "Sword breaks", M + "weapon_break"));

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
        for (Def d : ALL) {
            SoundEntries.Event e = entries.event(d.event()).subtitle(d.subtitleKey());
            d.sounds().forEach(e::sound);
        }
    }
}
