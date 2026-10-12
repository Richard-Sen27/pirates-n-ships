package com.richardsenger.piratesnships.mob.village;

import java.util.List;

/**
 * The names of the villagers (LIFE1), pure: a first name and a surname from a seed, women's first names for the
 * fishwife, either for the other roles. The crew's hiring lists ({@code HiringRules}) in spirit: plain English names
 * of the age of sail.
 */
public final class VillageNames {

    static final List<String> MEN = List.of("Tom", "Will", "Jack", "Ned", "Sam", "Harry", "Ben", "Joe", "Kit", "Abel", "Amos",
            "Caleb", "Eli", "Hugh", "Jem", "Luke", "Matt", "Nat", "Owen", "Ralph", "Seth", "Walt", "Silas", "Josiah");
    static final List<String> WOMEN = List.of("Anne", "Bess", "Kate", "Meg", "Nan", "Peg", "Sally", "Ruth", "Martha", "Mary",
            "Hannah", "Molly", "Polly", "Grace", "Alice", "Joan", "Susan", "Tabitha");
    static final List<String> SURNAMES = List.of("Barker", "Cobb", "Dunn", "Fletcher", "Gale", "Hobbs", "Lane", "Moss", "Nash",
            "Oakes", "Penn", "Quinn", "Rook", "Shaw", "Tate", "Wade", "Yates", "Cotton", "Fry", "Grey", "Hale", "Jory",
            "Lowe", "Marlow", "Pike", "Reed", "Stone", "Ward", "Pengelly", "Trelawny", "Kemp", "Netley");

    private VillageNames() {
    }

    /** The name of a villager of {@code role} from {@code seed}: the same seed always gives the same name. */
    public static String name(VillageRole role, long seed) {
        long a = mix(seed), b = mix(a ^ 0x9E3779B97F4A7C15L), c = mix(b ^ 0xC2B2AE3D27D4EB4FL);
        boolean woman = role == VillageRole.FISHWIFE || (role != VillageRole.LAMPLIGHTER && Long.remainderUnsigned(c, 2) == 0);
        List<String> first = woman ? WOMEN : MEN;
        return first.get((int) Long.remainderUnsigned(a, first.size())) + " " + SURNAMES.get((int) Long.remainderUnsigned(b, SURNAMES.size()));
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
