package com.richardsenger.piratesnships.ship.decor.flag;

import net.minecraft.util.StringRepresentable;

/**
 * Which part of a pole a flagpole block shows (VIS1a, docs/design.md §4.7 "Tall poles"), from its flagpole neighbours
 * above and below (pure, the vanilla fence/wall pattern): a lone block is {@link #SINGLE} (truck, finial and cleat),
 * the foot of a run {@link #BOTTOM} (the cleat), the blocks between {@link #MIDDLE} (the bare pole) and the head
 * {@link #TOP} (truck and finial). Only the look: the server's rules ({@link FlagpoleRun}) read the blocks themselves,
 * so a part that is briefly stale (a structure piece, a pole saved before VIS1a) never changes who owns the flag.
 */
public enum FlagpolePart implements StringRepresentable {
    SINGLE("single"), BOTTOM("bottom"), MIDDLE("middle"), TOP("top");

    private final String id;

    FlagpolePart(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** The part of a block with or without a flagpole under it ({@code below}) and on it ({@code above}). */
    public static FlagpolePart of(boolean below, boolean above) {
        if (below) return above ? MIDDLE : TOP;
        return above ? BOTTOM : SINGLE;
    }

    /** Whether a flagpole stands on this block. */
    public boolean hasAbove() {
        return this == BOTTOM || this == MIDDLE;
    }

    /** Whether this block stands on a flagpole. */
    public boolean hasBelow() {
        return this == MIDDLE || this == TOP;
    }
}
