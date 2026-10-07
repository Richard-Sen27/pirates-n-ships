package com.richardsenger.piratesnships.combat.cannon;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Which half of the two-block cannon a block is (docs/design.md §8.2, P2), like a bed's head and foot: the
 * {@link #FRONT} block is the master (muzzle end, block entity, station, model), the {@link #REAR} block the back of the
 * carriage behind it, which draws nothing and forwards every use to the master. A block state.
 */
public enum CannonPart implements StringRepresentable {
    FRONT, REAR;

    public boolean isMaster() {
        return this == FRONT;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
