package com.richardsenger.piratesnships.station.lookout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/** LKO2: the lookout's action-bar line, in English, never longer than 40 characters. */
class SightingCueTest {

    private static final UnaryOperator<String> EN = k -> LookoutLang.ENGLISH.getOrDefault(k, k);

    private static String cue(SightingPayload.What what, double relative, int distance) {
        return SightingCue.text(what, "", relative, distance, EN);
    }

    @Test
    void everyKindAndBearingFitsFortyCharacters() {
        int[] distances = {1, 9, 14, 20, 140, 160, 990, 1020, 99999};
        for (SightingPayload.What what : SightingPayload.What.values()) {
            for (int deg = -180; deg <= 180; deg++) {
                for (int d : distances) {
                    String line = cue(what, deg, d);
                    assertTrue(line.length() <= SightingCue.MAX, what + " " + deg + " " + d + ": '" + line + "' " + line.length());
                    assertFalse(line.contains("message."), "an untranslated key: " + line);
                    assertTrue(line.contains(Integer.toString(d)) || d == 99999, "the distance is lost: " + line);
                }
            }
        }
    }

    @Test
    void pointsWhenTheyFitTheSideOtherwise() {
        assertEquals("Sail two points to starboard, 140 blocks", cue(SightingPayload.What.SAIL, 22.5, 140));
        assertEquals("Land ahead, 300 blocks", cue(SightingPayload.What.LAND, 3, 300));
        assertEquals("Wreck astern, 60 blocks", cue(SightingPayload.What.WRECK, 179, 60));
        assertEquals("Shark abeam to port, 12 blocks", cue(SightingPayload.What.SHARK, -90, 12));
        assertEquals("Land one point to port, 90 blocks", cue(SightingPayload.What.LAND, -11, 90));
        assertEquals("Merchant to starboard, 140 blocks", cue(SightingPayload.What.MERCHANT, 22.5, 140));
        assertEquals("Kraken on the port quarter, 40 blocks", cue(SightingPayload.What.KRAKEN, -135, 40));
        assertEquals("Pirate ship aft to starboard, 160 blocks", cue(SightingPayload.What.PIRATE, 120, 160));
        assertEquals("Navy ship abeam to starboard, 20 blocks", cue(SightingPayload.What.NAVY, 90, 20));
        assertEquals("Pirate ship aft to starboard, 1020", cue(SightingPayload.What.PIRATE, 120, 1020));
    }

    @Test
    void aKnownShipIsNamedWhenItFits() {
        assertEquals("Sea Wolf four points to port, 140 blocks", SightingCue.text(SightingPayload.What.PIRATE, "Sea Wolf", -40, 140, EN));
        assertEquals("Sea Wolf ahead, 80 blocks", SightingCue.text(SightingPayload.What.PIRATE, "Sea Wolf", 0, 80, EN));
        String longName = "Captain Bartholomew Blackwater's Revenge of the Deep";
        assertEquals("Pirate ship to port, 140 blocks", SightingCue.text(SightingPayload.What.PIRATE, longName, -40, 140, EN));
    }

    @Test
    void aLanguageTooLongIsCut() {
        UnaryOperator<String> wordy = k -> k.equals(LookoutLang.KEY_WHAT_SHARK) ? "A truly enormous shark of the deep blue sea" : EN.apply(k);
        String line = SightingCue.text(SightingPayload.What.SHARK, "", 0, 10, wordy);
        assertEquals(SightingCue.MAX, line.length());
        assertTrue(line.startsWith("A truly enormous shark"));
    }

    @Test
    void markTints() {
        assertEquals(0xFFFFFFFF, SightingPayload.What.LAND.tint());
        assertEquals(0xFFE03C32, SightingPayload.What.PIRATE.tint());
        assertEquals(0xFF4A86F0, SightingPayload.What.NAVY.tint());
        for (SightingPayload.What w : new SightingPayload.What[] {SightingPayload.What.SAIL, SightingPayload.What.MERCHANT,
                SightingPayload.What.WRECK, SightingPayload.What.SHARK, SightingPayload.What.KRAKEN}) {
            assertEquals(0xFFA0A0A0, w.tint(), w.name());
        }
    }
}
