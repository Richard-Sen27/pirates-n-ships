package com.richardsenger.piratesnships.world.port;

import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** NAME3: port names are deterministic, fit their kind, never repeat within a world, and the word lists are clean. */
class PortNamesTest {

    private static final long SEED = 8_675_309L;

    private static final Pattern VILLAGE = Pattern.compile(
            "Port \\S+|\\S+ Harbour|\\S+mouth|\\S+ Quay|[A-Z][a-z]+|St\\. \\S+'s|\\S+ (Cove|Bay|Haven|Landing|Point)");
    private static final Pattern OUTPOST = Pattern.compile("Fort \\S+|Fort St\\. \\S+|\\S+ Battery");
    private static final Pattern ISLAND = Pattern.compile("[A-Z][a-z]+('s)?( Man's)? \\S+|\\S+ Cay|\\S+'s? \\S+");

    /** Words no name may contain (lower case substrings), checked over every name the lists can make. */
    private static final List<String> BLOCKED = List.of("fuck", "shit", "cunt", "cock", "dick", "piss", "twat", "wank",
            "slut", "whore", "bitch", "bastard", "nigg", "fag", "rape", "penis", "anus", "porn", "sex", "cum", "tits",
            "arse", "boob", "crap", "damn", "nazi", "kill");

    @Test
    void sameSeedAndPlaceGiveTheSameName() {
        for (PortKind kind : PortKind.values()) {
            for (int i = 0; i < 50; i++) {
                assertEquals(PortNames.candidate(SEED, kind, i * 97, -i * 31, 0), PortNames.candidate(SEED, kind, i * 97, -i * 31, 0));
            }
        }
        assertEquals(PortNames.name(SEED, PortKind.SEAFARER_VILLAGE, 1192, 1530, k -> false),
                PortNames.name(SEED, PortKind.SEAFARER_VILLAGE, 1192, 1530, k -> false));
    }

    @Test
    void seedPlaceAndAttemptChangeTheName() {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 40; i++) names.add(PortNames.candidate(SEED + i, PortKind.SEAFARER_VILLAGE, 1192, 1530, 0));
        assertTrue(names.size() > 30, "other seeds give other names: " + names);
        names.clear();
        for (int i = 0; i < 40; i++) names.add(PortNames.candidate(SEED, PortKind.SEAFARER_VILLAGE, 1192, 1530, i));
        assertTrue(names.size() > 30, "other attempts give other names: " + names);
    }

    @Test
    void namesFitTheirKind() {
        Random r = new Random(1);
        for (int i = 0; i < 2_000; i++) {
            int x = r.nextInt(60_000) - 30_000, z = r.nextInt(60_000) - 30_000;
            String v = PortNames.candidate(SEED, PortKind.SEAFARER_VILLAGE, x, z, 0);
            String o = PortNames.candidate(SEED, PortKind.NAVY_OUTPOST, x, z, 0);
            String p = PortNames.candidate(SEED, PortKind.PIRATE_ISLAND, x, z, 0);
            assertTrue(VILLAGE.matcher(v).matches(), "village " + v);
            assertTrue(OUTPOST.matcher(o).matches(), "outpost " + o);
            assertTrue(ISLAND.matcher(p).matches(), "island " + p);
            assertFalse(o.contains("Harbour") || p.startsWith("Fort ") || v.startsWith("Fort "), v + " / " + o + " / " + p);
        }
    }

    @Test
    void everyPatternTurnsUp() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 3_000; i++) {
            String v = PortNames.candidate(SEED, PortKind.SEAFARER_VILLAGE, i, 7 * i, 0);
            if (v.startsWith("Port ")) seen.add("port");
            if (v.endsWith(" Harbour")) seen.add("harbour");
            if (v.endsWith("mouth")) seen.add("mouth");
            if (v.endsWith(" Quay")) seen.add("quay");
            if (v.startsWith("St. ")) seen.add("saint");
            if (!v.contains(" ") && !v.endsWith("mouth")) seen.add("compound");
            String o = PortNames.candidate(SEED, PortKind.NAVY_OUTPOST, i, 7 * i, 0);
            if (o.startsWith("Fort St. ")) seen.add("fort_saint");
            else if (o.startsWith("Fort ")) seen.add("fort");
            if (o.endsWith(" Battery")) seen.add("battery");
            String p = PortNames.candidate(SEED, PortKind.PIRATE_ISLAND, i, 7 * i, 0);
            if (p.endsWith(" Cay")) seen.add("cay");
            if (p.equals("Skull Cove") || p.equals("Dead Man's Reach") || p.equals("Rum Row")) seen.add("classic");
        }
        assertEquals(Set.of("port", "harbour", "mouth", "quay", "saint", "compound", "fort_saint", "fort", "battery", "cay", "classic"), seen);
    }

    @Test
    void aThousandPortsInOneWorldNeverShareAName() {
        Random r = new Random(42);
        Set<String> taken = new HashSet<>();
        List<String> order = new ArrayList<>();
        PortKind[] kinds = PortKind.values();
        for (int i = 0; i < 1_000; i++) {
            PortKind kind = kinds[r.nextInt(kinds.length)];
            String name = PortNames.name(SEED, kind, r.nextInt(60_000) - 30_000, r.nextInt(60_000) - 30_000, taken::contains);
            assertTrue(taken.add(PortNames.key(name)), "repeated " + name);
            order.add(name);
        }
        // the same world registering in the same order names the same
        Random again = new Random(42);
        Set<String> taken2 = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            PortKind kind = kinds[again.nextInt(kinds.length)];
            String name = PortNames.name(SEED, kind, again.nextInt(60_000) - 30_000, again.nextInt(60_000) - 30_000, taken2::contains);
            taken2.add(PortNames.key(name));
            assertEquals(order.get(i), name);
        }
    }

    @Test
    void aThousandVillagesAtOnePlaceFallBackToNumerals() {
        Set<String> taken = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String name = PortNames.name(SEED, PortKind.SEAFARER_VILLAGE, 5, 5, taken::contains);
            assertTrue(taken.add(PortNames.key(name)), "repeated " + name);
        }
        String base = PortNames.candidate(SEED, PortKind.SEAFARER_VILLAGE, 5, 5, 0);
        assertTrue(taken.contains(PortNames.key(base + " II")), "the fallback numbers the first candidate");
    }

    @Test
    void takenIsCaseInsensitive() {
        String first = PortNames.name(SEED, PortKind.NAVY_OUTPOST, 10, 20, k -> false);
        String upper = first.toUpperCase(Locale.ROOT);
        String second = PortNames.name(SEED, PortKind.NAVY_OUTPOST, 10, 20, k -> k.equals(PortNames.key(upper)));
        assertNotEquals(first, second);
    }

    @Test
    void listsHaveNoDuplicates() {
        for (List<String> list : List.of(PortNames.FOUNDERS, PortNames.SAINTS, PortNames.PREFIXES, PortNames.SUFFIXES,
                PortNames.RIVERS, PortNames.FEATURES, PortNames.FEATURE_PLACES, PortNames.ADMIRALS, PortNames.GRIM,
                PortNames.ISLAND_PLACES, PortNames.PIRATES, PortNames.PIRATE_PLACES)) {
            assertEquals(new HashSet<>(list).size(), list.size(), "duplicate in " + list);
        }
        List<String> compounds = PortNames.allCompounds();
        assertEquals(new HashSet<>(compounds).size(), compounds.size(), "two prefix/suffix pairs spell the same name");
    }

    @Test
    void noNameContainsABlockedWord() {
        List<String> words = new ArrayList<>(PortNames.allCompounds());
        for (List<String> list : List.of(PortNames.FOUNDERS, PortNames.SAINTS, PortNames.RIVERS, PortNames.FEATURES,
                PortNames.ADMIRALS, PortNames.GRIM, PortNames.ISLAND_PLACES, PortNames.PIRATES, PortNames.PIRATE_PLACES)) {
            words.addAll(list);
        }
        for (String r : PortNames.RIVERS) words.add(r + "mouth");
        for (String w : words) {
            String lower = w.toLowerCase(Locale.ROOT);
            for (String bad : BLOCKED) assertFalse(lower.contains(bad), w + " contains " + bad);
        }
        for (int i = 0; i < 5_000; i++) {
            for (PortKind kind : PortKind.values()) {
                String lower = PortNames.candidate(SEED, kind, i, -i, i % 7).toLowerCase(Locale.ROOT);
                for (String bad : BLOCKED) assertFalse(lower.contains(bad), lower + " contains " + bad);
            }
        }
    }

    @Test
    void seamLettersAreWrittenOnce() {
        assertEquals("Greyhaven", PortNames.join("Grey", "haven"));
        assertEquals("Gulley", PortNames.join("Gull", "ley"));
        assertEquals("Saltmarsh", PortNames.join("Salt", "marsh"));
    }

    @Test
    void readableIdsAsBefore() {
        assertEquals("Village 1192 1530", PortNames.fromId(ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "village_1192_1530")));
        assertEquals("Port Royal", PortNames.fromId(ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "debug/port_royal")));
    }

    @Test
    void possessives() {
        assertEquals("Kidd's", PortNames.possessive("Kidd"));
        assertEquals("Roberts'", PortNames.possessive("Roberts"));
    }

    @Test
    void numeralsCountOnFromTwo() {
        assertEquals("II", PortNames.numeral(0));
        assertEquals("X", PortNames.numeral(8));
        assertEquals("11", PortNames.numeral(9));
    }
}
