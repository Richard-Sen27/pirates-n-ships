package com.richardsenger.piratesnships.core.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The entries of the mod's one {@code assets/pirates_n_ships/sounds.json}, collected from every module through
 * {@link DataContributions#sounds}. An entry is keyed by the sound event's path; registering the same key twice
 * (from two modules or one) fails the data run.
 *
 * <pre>{@code
 * data.sounds(s -> s.event(AudioSounds.SHIP_CREAK)
 *         .subtitle("subtitles.pirates_n_ships.ship.creak")
 *         .sound(SoundEntries.file("minecraft:block/wooden_door/open1").volume(0.8f)));
 * data.sounds(s -> s.event(AudioSounds.MUSIC_SEA)
 *         .sound(SoundEntries.file("pirates_n_ships:music/sea/track").stream()));
 * }</pre>
 *
 * A sound name points at {@code assets/<ns>/sounds/<path>.ogg}, or with {@link Sound#asEvent()} at another sound
 * event. Pure apart from {@link ResourceLocation}, so JUnit can test it.
 */
public final class SoundEntries {

    private final Map<String, Event> events = new LinkedHashMap<>();

    /** Starts the entry of one of our sound events. Throws if another contribution already added it. */
    public Event event(RegistryEntry<SoundEvent, ? extends SoundEvent> sound) {
        ResourceLocation id = sound.id();
        if (!Constants.MOD_ID.equals(id.getNamespace())) {
            throw new IllegalArgumentException("sounds.json of " + Constants.MOD_ID + " can't define " + id);
        }
        return event(id.getPath());
    }

    /** Starts the entry with the key {@code path} (the sound event {@code pirates_n_ships:<path>}). */
    public synchronized Event event(String path) {
        if (!ResourceLocation.isValidPath(path) || path.isEmpty()) {
            throw new IllegalArgumentException("Invalid sound event path '" + path + "'");
        }
        Event e = new Event(path);
        if (events.putIfAbsent(path, e) != null) {
            throw new IllegalStateException("Duplicate sounds.json entry '" + path
                    + "': two data.sounds(...) contributions register it. Each sound event needs exactly one owner.");
        }
        return e;
    }

    /** A sound file {@code ns:path} ({@code assets/<ns>/sounds/<path>.ogg}). */
    public static Sound file(String name) {
        return new Sound(checkName(name), null, null, null, false, false);
    }

    /** All entries in registration order. */
    public synchronized List<Event> events() {
        return List.copyOf(events.values());
    }

    public synchronized boolean isEmpty() {
        return events.isEmpty();
    }

    /** The whole {@code sounds.json}. Entries without sounds are rejected. */
    public synchronized JsonObject toJson() {
        JsonObject root = new JsonObject();
        for (Event e : events.values()) root.add(e.path, e.toJson());
        return root;
    }

    private static String checkName(String name) {
        if (ResourceLocation.tryParse(name) == null || !name.contains(":")) {
            throw new IllegalArgumentException("Sound name '" + name + "' must be a namespaced id such as pirates_n_ships:music/sea/track");
        }
        return name;
    }

    /** One {@code sounds.json} entry. */
    public static final class Event {
        private final String path;
        private final List<Sound> sounds = new ArrayList<>();
        private @Nullable String subtitle;
        private boolean replace;

        private Event(String path) {
            this.path = path;
        }

        public String path() {
            return path;
        }

        /** Adds one sound (picked at random by weight each time the event plays). */
        public Event sound(Sound sound) {
            sounds.add(sound);
            return this;
        }

        /** Adds plain sound files without options. */
        public Event sounds(String... names) {
            for (String n : names) sound(file(n));
            return this;
        }

        /** Translation key of the subtitle shown with "Show Subtitles". */
        public Event subtitle(String key) {
            this.subtitle = key;
            return this;
        }

        /** {@code "replace": true}: replaces the sounds lower resource packs list for this event instead of adding. */
        public Event replace(boolean replace) {
            this.replace = replace;
            return this;
        }

        public List<Sound> soundList() {
            return List.copyOf(sounds);
        }

        public @Nullable String subtitle() {
            return subtitle;
        }

        JsonObject toJson() {
            if (sounds.isEmpty()) throw new IllegalStateException("sounds.json entry '" + path + "' has no sounds");
            JsonObject o = new JsonObject();
            if (replace) o.addProperty("replace", true);
            JsonArray arr = new JsonArray();
            for (Sound s : sounds) arr.add(s.toJson());
            o.add("sounds", arr);
            if (subtitle != null) o.addProperty("subtitle", subtitle);
            return o;
        }
    }

    /**
     * One sound of an entry. Unset options are left out of the JSON so vanilla's defaults apply (volume 1, pitch 1,
     * weight 1, not streamed).
     */
    public record Sound(String name, @Nullable Float volume, @Nullable Float pitch, @Nullable Integer weight,
                        boolean streamed, boolean event) {

        public Sound volume(float v) {
            return new Sound(name, v, pitch, weight, streamed, event);
        }

        public Sound pitch(float p) {
            return new Sound(name, volume, p, weight, streamed, event);
        }

        public Sound weight(int w) {
            if (w < 1) throw new IllegalArgumentException("Sound weight must be at least 1: " + name);
            return new Sound(name, volume, pitch, w, streamed, event);
        }

        /** Streams the file from disk instead of loading it whole; use for music and long sounds. */
        public Sound stream() {
            return new Sound(name, volume, pitch, weight, true, event);
        }

        /** {@code "type": "event"}: {@link #name} is another sound event, not a file. */
        public Sound asEvent() {
            return new Sound(name, volume, pitch, weight, streamed, true);
        }

        JsonElement toJson() {
            if (volume == null && pitch == null && weight == null && !streamed && !event) return new JsonPrimitive(name);
            JsonObject o = new JsonObject();
            o.addProperty("name", name);
            if (volume != null) o.addProperty("volume", volume);
            if (pitch != null) o.addProperty("pitch", pitch);
            if (weight != null) o.addProperty("weight", weight);
            if (streamed) o.addProperty("stream", true);
            if (event) o.addProperty("type", "event");
            return o;
        }
    }
}
