package com.richardsenger.piratesnships.combat.cannon;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** What is in the barrel (docs/design.md §8.2): nothing, powder, or powder and a ball (ready to fire). A block state. */
public enum CannonLoad implements StringRepresentable {
    EMPTY, POWDER, LOADED;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
