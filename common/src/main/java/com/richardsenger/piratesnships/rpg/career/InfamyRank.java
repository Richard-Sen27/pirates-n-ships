package com.richardsenger.piratesnships.rpg.career;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;
import java.util.Optional;

/**
 * The pirate infamy ladder (docs/design.md §15, CAR1), from {@link #DECKHAND} (no infamy: everyone starts here) to
 * {@link #PIRATE_LORD}. Stored in the {@link CareerRecord}, promoted by {@link CareerRules}.
 */
public enum InfamyRank implements StringRepresentable {
    DECKHAND(0),
    BUCCANEER(5),
    DREAD_CAPTAIN(10),
    PIRATE_LORD(20);

    public static final Codec<InfamyRank> CODEC = StringRepresentable.fromEnum(InfamyRank::values);

    private final int standingBonus;

    InfamyRank(int standingBonus) {
        this.standingBonus = standingBonus;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String nameKey() {
        return Constants.MOD_ID + ".career.infamy." + id();
    }

    /** Extra standing with the pirates this rank lends (CAR2 reads it; nothing in CAR1 does). */
    public int standingBonus() {
        return standingBonus;
    }

    public Optional<InfamyRank> next() {
        return ordinal() + 1 < values().length ? Optional.of(values()[ordinal() + 1]) : Optional.empty();
    }

    public boolean atLeast(InfamyRank other) {
        return ordinal() >= other.ordinal();
    }

    @Override
    public String getSerializedName() {
        return id();
    }

    public static Optional<InfamyRank> byId(String id) {
        for (InfamyRank r : values()) if (r.id().equals(id)) return Optional.of(r);
        return Optional.empty();
    }
}
