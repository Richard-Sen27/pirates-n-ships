package com.richardsenger.piratesnships.rpg.career;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;
import java.util.Optional;

/**
 * The navy rank ladder (docs/design.md §15, CAR1). {@link #NONE} = not in the navy. Ranks are stored in the
 * {@link CareerRecord} (they are events: desertion takes them away), promotions are decided by {@link CareerRules}.
 */
public enum NavyRank implements StringRepresentable {
    NONE(false, 0),
    MIDSHIPMAN(false, 0),
    LIEUTENANT(true, 5),
    CAPTAIN(true, 10),
    COMMODORE(true, 15),
    ADMIRAL(true, 20);

    public static final Codec<NavyRank> CODEC = StringRepresentable.fromEnum(NavyRank::values);

    private final boolean flagRight;
    private final int standingBonus;

    NavyRank(boolean flagRight, int standingBonus) {
        this.flagRight = flagRight;
        this.standingBonus = standingBonus;
    }

    /** Config key and command argument: {@code midshipman}, {@code lieutenant}, ... */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String nameKey() {
        return Constants.MOD_ID + ".career.navy." + id();
    }

    /** Whether this rank may fly the navy flag without the false-flag rule (§4.7; used by CAR2). */
    public boolean flagRight() {
        return flagRight;
    }

    /** Extra navy standing this rank lends (CAR2 reads it; nothing in CAR1 does). */
    public int standingBonus() {
        return standingBonus;
    }

    /** The rank above, or empty at the top. */
    public Optional<NavyRank> next() {
        return ordinal() + 1 < values().length ? Optional.of(values()[ordinal() + 1]) : Optional.empty();
    }

    public boolean atLeast(NavyRank other) {
        return ordinal() >= other.ordinal();
    }

    @Override
    public String getSerializedName() {
        return id();
    }

    public static Optional<NavyRank> byId(String id) {
        for (NavyRank r : values()) if (r.id().equals(id)) return Optional.of(r);
        return Optional.empty();
    }
}
