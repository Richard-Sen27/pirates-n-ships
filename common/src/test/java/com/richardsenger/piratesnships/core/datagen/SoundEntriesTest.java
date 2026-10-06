package com.richardsenger.piratesnships.core.datagen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundEntriesTest {

    @Test
    void duplicateKeyFails() {
        SoundEntries s = new SoundEntries();
        s.event("ship.creak").sounds("minecraft:block/chest/open");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> s.event("ship.creak"));
        assertTrue(e.getMessage().contains("ship.creak"), e.getMessage());
    }

    @Test
    void entriesSerializeLikeVanilla() {
        SoundEntries s = new SoundEntries();
        s.event("ship.creak").subtitle("subtitles.pirates_n_ships.ship.creak")
                .sound(SoundEntries.file("minecraft:block/chest/open").volume(0.8f));
        s.event("music.sea").sound(SoundEntries.file("pirates_n_ships:music/sea/a").stream())
                .sound(SoundEntries.file("pirates_n_ships:music/sea/b").stream().weight(2));
        s.event("anchor.thud").replace(true).sounds("minecraft:dig/stone1")
                .sound(SoundEntries.file("minecraft:block.stone.break").asEvent().pitch(0.5f));

        JsonObject expected = JsonParser.parseString("""
                {
                  "ship.creak": {"sounds": [{"name": "minecraft:block/chest/open", "volume": 0.8}],
                                 "subtitle": "subtitles.pirates_n_ships.ship.creak"},
                  "music.sea": {"sounds": [{"name": "pirates_n_ships:music/sea/a", "stream": true},
                                           {"name": "pirates_n_ships:music/sea/b", "weight": 2, "stream": true}]},
                  "anchor.thud": {"replace": true, "sounds": ["minecraft:dig/stone1",
                                  {"name": "minecraft:block.stone.break", "pitch": 0.5, "type": "event"}]}
                }""").getAsJsonObject();
        assertEquals(expected, JsonParser.parseString(s.toJson().toString()));
    }

    @Test
    void entryWithoutSoundsFails() {
        SoundEntries s = new SoundEntries();
        s.event("empty");
        assertThrows(IllegalStateException.class, s::toJson);
    }

    @Test
    void namesMustBeNamespacedIds() {
        assertThrows(IllegalArgumentException.class, () -> SoundEntries.file("music/sea/a"));
        assertThrows(IllegalArgumentException.class, () -> SoundEntries.file("pirates_n_ships:Music"));
        assertThrows(IllegalArgumentException.class, () -> new SoundEntries().event("Bad Key"));
    }
}
