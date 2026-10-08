package com.richardsenger.piratesnships.mob.captain;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Names of pirate captains (BOS1, docs/design.md §15), pure: an epithet, a first name and a surname
 * ("Black-Tooth Bartholomew Crowe"), chosen by a hash of the island's port id and the captain's generation (0 for the
 * first captain, then 1, 2, ... for successors). The same island always names the same captain on every server and in
 * every chunk, and a successor never carries his predecessor's name.
 */
public final class CaptainNames {

    static final List<String> EPITHETS = List.of(
            "Black-Tooth", "Red-Hand", "One-Eyed", "Iron-Hook", "Mad", "Gentleman", "Bloody", "Salt-Beard",
            "Grim", "Silver-Tongue", "Cutthroat", "Long", "Barnacle", "Storm-Born", "Dead-Eye", "Scurvy",
            "Old", "Lucky", "Rum-Soaked", "Peg-Leg", "Smiling", "Crimson", "Thunder", "Ghost");
    static final List<String> FIRST_NAMES = List.of(
            "Bartholomew", "Silas", "Ezekiel", "Jonas", "Ambrose", "Cornelius", "Tobias", "Jasper",
            "Obadiah", "Rufus", "Gideon", "Horatio", "Isaiah", "Barnaby", "Ignatius", "Mordecai",
            "Anne", "Mary", "Grace", "Jacquotte", "Isabel", "Hester", "Molly", "Nell");
    static final List<String> SURNAMES = List.of(
            "Crowe", "Blackwood", "Thorne", "Halloran", "Grimsby", "Marsh", "Vex", "Ashby",
            "Ravensworth", "Quill", "Hargrave", "Drummond", "Kettle", "Morrow", "Pike", "Slade",
            "Brimstone", "Gallows", "Locke", "Fenwick", "Tarrow", "Wrackham", "Holloway", "Stroud");

    private CaptainNames() {
    }

    /** The name of the captain of {@code generation} at the island with port id {@code portId}. */
    public static String name(String portId, int generation) {
        String name = pick(seed(portId, generation));
        // a successor never inherits the name: step the seed until it differs from the one before him
        if (generation > 0) {
            String before = pick(seed(portId, generation - 1));
            long s = seed(portId, generation);
            while (name.equals(before)) {
                s = mix(s + 1);
                name = pick(s);
            }
        }
        return name;
    }

    /** A stable 64-bit seed (FNV-1a over the UTF-8 id, then the generation mixed in). */
    static long seed(String portId, int generation) {
        long h = 0xcbf29ce484222325L;
        for (byte b : portId.getBytes(StandardCharsets.UTF_8)) {
            h ^= b & 0xff;
            h *= 0x100000001b3L;
        }
        return mix(h ^ (generation * 0x9E3779B97F4A7C15L));
    }

    /** SplitMix64's finaliser. */
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private static String pick(long seed) {
        long a = mix(seed);
        long b = mix(a);
        long c = mix(b);
        return EPITHETS.get(index(a, EPITHETS.size())) + " " + FIRST_NAMES.get(index(b, FIRST_NAMES.size())) + " "
                + SURNAMES.get(index(c, SURNAMES.size()));
    }

    private static int index(long bits, int size) {
        return (int) Math.floorMod(bits, (long) size);
    }
}
