package com.richardsenger.piratesnships.world.port;

import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Proper names of ports (NAME3), pure: a name from the world seed, the port's kind and its centre (x, z), English and
 * in the mod's tone. Villages read "Port Marlow", "Greyhaven Harbour", "Falmouth", "Saltmarsh Quay", "St. Brendan's",
 * "Kettle Cove"; navy outposts "Fort Anson", "Fort St. Anne", "Gullsend Battery"; pirate islands "Bone Cay",
 * "Dead Man's Reach", "Rum Row", "Kidd's Folly".
 *
 * <p><b>Uniqueness rule</b> ({@link #name}): candidate 0, 1, 2, ... ({@link #ATTEMPTS} of them, each from its own hash)
 * until one is not taken (compared case-insensitively, {@link #key}); if every candidate is taken, candidate 0 with
 * the first free numeral " II", " III", ... The caller (the port registry) knows the taken names, so the same world
 * gives the same names as long as its ports register in the same order; a port's name is stored with it once given.
 */
public final class PortNames {

    /** Candidates tried before the numeral fallback. */
    public static final int ATTEMPTS = 48;

    /** Founders and old families (villages, "Port Marlow", and outposts, "Fort Penrose"). */
    static final List<String> FOUNDERS = List.of(
            "Marlow", "Penrose", "Ashby", "Tremayne", "Carew", "Fairweather", "Hawkins", "Frobisher",
            "Grenville", "Pendleton", "Barlow", "Lockyer", "Merriweather", "Kingsley", "Hollis", "Carrow",
            "Trelawney", "Colville", "Danby", "Everard", "Fenwick", "Halloran", "Ingram", "Jessop",
            "Kemble", "Lyndon", "Mallory", "Oakes", "Prideaux", "Quiller", "Rowe", "Sallow",
            "Tregarthen", "Upton", "Vosper", "Whitlock");
    /** Saints ("St. Brendan's", "Fort St. Anne"). */
    static final List<String> SAINTS = List.of(
            "Brendan", "Anne", "Agnes", "Columba", "Piran", "Michael", "Nicholas", "Elmo",
            "Clement", "Andrew", "Brigid", "Mary", "Peter", "Jude", "Ives", "Mawes",
            "Austell", "Catherine", "Erasmus", "Lucy", "Hilda", "Ninian", "Swithin", "Kilda",
            "Margaret", "Martin", "George", "Patrick", "Christopher", "Bride");
    /** First halves of compound place names ("Grey" + "haven"). */
    static final List<String> PREFIXES = List.of(
            "Grey", "Salt", "Gull", "Storm", "Wind", "Brine", "Tide", "Gale",
            "Mist", "Fog", "Kelp", "Cod", "Rock", "Sand", "Shingle", "Heron",
            "Tern", "Pike", "Ash", "Oak", "Elder", "Haw", "Thorn", "Mallow",
            "Gold", "Silver", "Raven", "Crab", "Whelk", "Pebble", "Seal", "Wren");
    /** Second halves of compound place names. */
    static final List<String> SUFFIXES = List.of(
            "haven", "marsh", "send", "wick", "by", "combe", "ford", "mere",
            "stead", "holm", "ness", "port", "sey", "ley", "ton", "well",
            "stow", "bury", "cliff", "strand");
    /** River names for "-mouth" ("Falmouth", "Wrenmouth"). */
    static final List<String> RIVERS = List.of(
            "Fal", "Wey", "Dart", "Lyn", "Teign", "Ex", "Sid", "Yar",
            "Ax", "Tyne", "Cole", "Brent", "Otter", "Alder", "Tam", "Lune",
            "Kenn", "Heron", "Plover", "Avon", "Fowey", "Looe", "Camel", "Tamar");
    /** Shore features of a village ("Kettle Cove", "Lantern Bay"). */
    static final List<String> FEATURES = List.of(
            "Kettle", "Anchor", "Lantern", "Mackerel", "Driftwood", "Lobster", "Oyster", "Gannet",
            "Puffin", "Cormorant", "Barnacle", "Lighthouse", "Mussel", "Pilchard", "Herring", "Bell",
            "Compass", "Netmender's", "Sailmaker's", "Cooper's", "Fisher's", "Mermaid's", "Whale", "Conch");
    static final List<String> FEATURE_PLACES = List.of("Cove", "Bay", "Haven", "Landing", "Point");
    /** Admirals for navy outposts ("Fort Anson", "Benbow Battery"). */
    static final List<String> ADMIRALS = List.of(
            "Anson", "Hawke", "Rodney", "Howe", "Benbow", "Raleigh", "Vernon", "Boscawen",
            "Keppel", "Duncan", "Jervis", "Collingwood", "Hood", "Cochrane", "Saumarez", "Pellew",
            "Blake", "Rooke", "Shovell", "Hardy", "Troubridge", "Codrington", "Exmouth", "Gambier");
    /** First words of pirate places ("Skull Cove", "Dead Man's Reach", "Rum Row"). */
    static final List<String> GRIM = List.of(
            "Skull", "Bone", "Gallows", "Cutlass", "Powder", "Black", "Rum", "Grog",
            "Shark", "Wreck", "Raven", "Crow", "Devil's", "Hangman's", "Dead Man's", "Widow's",
            "Brimstone", "Cannon", "Scurvy", "Doubloon", "Parrot", "Kraken", "Plank", "Mutineer's",
            "Cutthroat", "Corsair's", "Buccaneer's", "Jolly");
    static final List<String> ISLAND_PLACES = List.of(
            "Cay", "Cove", "Reach", "Key", "Point", "Rock", "Row", "Den",
            "Bight", "Shoal", "Hollow", "Rest", "Landing", "Sound", "Creek", "Bluff");
    /** Pirates of legend for "Kidd's Folly". */
    static final List<String> PIRATES = List.of(
            "Kidd", "Teach", "Morgan", "Bonny", "Avery", "Vane", "Rackham", "Roberts",
            "Flint", "Blackbeard", "Hornigold", "Tew", "Low", "Lafitte");
    static final List<String> PIRATE_PLACES = List.of("Folly", "Rest", "Landing", "Hideout", "Lookout", "Cache");

    private static final String[] NUMERALS = {"II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private PortNames() {
    }

    /**
     * The unique name of a port of {@code kind} centred at ({@code x}, {@code z}) in a world of {@code worldSeed}:
     * the first candidate {@code taken} rejects ({@code taken} is asked with {@link #key}s), else the numeral fallback.
     */
    public static String name(long worldSeed, PortKind kind, int x, int z, Predicate<String> taken) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            String c = candidate(worldSeed, kind, x, z, attempt);
            if (!taken.test(key(c))) return c;
        }
        String base = candidate(worldSeed, kind, x, z, 0);
        for (int n = 0; ; n++) {
            String c = base + " " + numeral(n);
            if (!taken.test(key(c))) return c;
        }
    }

    /** Candidate {@code attempt} for the port: the same arguments always give the same name. */
    public static String candidate(long worldSeed, PortKind kind, int x, int z, int attempt) {
        Stream rng = new Stream(mix(mix(mix(mix(worldSeed ^ 0x9E3779B97F4A7C15L) + kind.ordinal()) + x) * 31 + z) + attempt);
        return switch (kind) {
            case SEAFARER_VILLAGE -> village(rng);
            case NAVY_OUTPOST -> outpost(rng);
            case PIRATE_ISLAND -> island(rng);
        };
    }

    private static String village(Stream rng) {
        return switch (rng.next(10)) {
            case 0, 1 -> "Port " + root(rng);
            case 2, 3 -> root(rng) + " Harbour";
            case 4 -> rng.pick(RIVERS) + "mouth";
            case 5 -> root(rng) + " Quay";
            case 6, 7 -> compound(rng);
            case 8 -> "St. " + rng.pick(SAINTS) + "'s";
            default -> rng.pick(FEATURES) + " " + rng.pick(FEATURE_PLACES);
        };
    }

    private static String outpost(Stream rng) {
        return switch (rng.next(8)) {
            case 0, 1 -> "Fort " + rng.pick(ADMIRALS);
            case 2 -> "Fort " + rng.pick(FOUNDERS);
            case 3, 4 -> "Fort St. " + rng.pick(SAINTS);
            case 5 -> rng.pick(ADMIRALS) + " Battery";
            default -> compound(rng) + " Battery";
        };
    }

    private static String island(Stream rng) {
        return switch (rng.next(8)) {
            case 0, 1, 2, 3 -> rng.pick(GRIM) + " " + rng.pick(ISLAND_PLACES);
            case 4, 5 -> compound(rng) + " Cay";
            default -> possessive(rng.pick(PIRATES)) + " " + rng.pick(PIRATE_PLACES);
        };
    }

    /** A founder's name or a compound place name. */
    private static String root(Stream rng) {
        return rng.next(3) == 0 ? rng.pick(FOUNDERS) : compound(rng);
    }

    private static String compound(Stream rng) {
        return join(rng.pick(PREFIXES), rng.pick(SUFFIXES));
    }

    /** "Kidd's", but "Roberts'". */
    static String possessive(String name) {
        return name.endsWith("s") ? name + "'" : name + "'s";
    }

    /** "Grey" + "haven"; a doubled letter at the seam is written once ("Gull" + "ley" reads "Gulley"). */
    static String join(String prefix, String suffix) {
        if (Character.toLowerCase(prefix.charAt(prefix.length() - 1)) == suffix.charAt(0)) return prefix + suffix.substring(1);
        return prefix + suffix;
    }

    /** Every compound place name the lists can make (for the tests' word checks). */
    static List<String> allCompounds() {
        List<String> out = new ArrayList<>();
        for (String p : PREFIXES) for (String s : SUFFIXES) out.add(join(p, s));
        return out;
    }

    /** The comparison key of a name: lower case, single spaces. */
    public static String key(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** " II" for 0, " III" for 1, ...; beyond the table the number itself. */
    static String numeral(int n) {
        return n < NUMERALS.length ? NUMERALS[n] : Integer.toString(n + 2);
    }

    /**
     * The readable form of a port id as shown before NAME3 (and while {@code world.ports.names} is off):
     * {@code pirates_n_ships:village_1192_1530} reads "Village 1192 1530", {@code debug/port_royal} "Port Royal".
     */
    public static String fromId(ResourceLocation port) {
        String path = port.getPath();
        String last = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder sb = new StringBuilder();
        for (String word : last.split("[_\\-]+")) {
            if (word.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return sb.isEmpty() ? path : sb.toString();
    }

    /** SplitMix64's finalizer. */
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** A SplitMix64 stream, spelled out so the names never depend on a JDK's random implementation. */
    private static final class Stream {
        private long state;

        Stream(long seed) {
            this.state = seed;
        }

        int next(int bound) {
            state += 0x9E3779B97F4A7C15L;
            return (int) Math.floorMod(mix(state), (long) bound);
        }

        String pick(List<String> list) {
            return list.get(next(list.size()));
        }
    }
}
