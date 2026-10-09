package com.richardsenger.piratesnships.combat.cannon;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * What a cannon fires (CAN3, docs/design.md §8.2): a round {@link #BALL} that holes hulls, {@link #CHAIN} shot (two balls on
 * a chain) that tears sail cloth and cuts rigging but breaches no hull, or {@link #GRAPE}shot (a canvas bag of small balls)
 * that leaves the muzzle as a cone of pellets hitting the people on a deck and no block. A block state of the cannon
 * ({@link CannonBlock#SHOT}, meaningful while it is {@link CannonLoad#LOADED}), so it is saved and synced with the load.
 */
public enum ShotKind implements StringRepresentable {
    BALL, CHAIN, GRAPE;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
