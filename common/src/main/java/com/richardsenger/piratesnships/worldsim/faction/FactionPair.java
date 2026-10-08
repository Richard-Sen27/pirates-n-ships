package com.richardsenger.piratesnships.worldsim.faction;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.law.flag.Faction;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** An unordered pair of two different factions, the key of a tension value (design.md §10.4, WS1). */
public enum FactionPair implements StringRepresentable {
    NAVY_PIRATES(Faction.NAVY, Faction.PIRATES),
    NAVY_MERCHANTS(Faction.NAVY, Faction.MERCHANTS),
    PIRATES_MERCHANTS(Faction.PIRATES, Faction.MERCHANTS);

    public static final Codec<FactionPair> CODEC = StringRepresentable.fromEnum(FactionPair::values);

    private final Faction a;
    private final Faction b;

    FactionPair(Faction a, Faction b) {
        this.a = a;
        this.b = b;
    }

    public Faction a() {
        return a;
    }

    public Faction b() {
        return b;
    }

    /** Whether {@code f} is one of the two. */
    public boolean involves(Faction f) {
        return a == f || b == f;
    }

    /** The other faction of the pair; {@code f} must be one of the two. */
    public Faction other(Faction f) {
        if (f == a) return b;
        if (f == b) return a;
        throw new IllegalArgumentException(f + " is not in " + this);
    }

    /** The pair of {@code x} and {@code y} in any order; throws for the same faction twice. */
    public static FactionPair of(Faction x, Faction y) {
        for (FactionPair p : values()) {
            if ((p.a == x && p.b == y) || (p.a == y && p.b == x)) return p;
        }
        throw new IllegalArgumentException("No tension between " + x + " and itself");
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
