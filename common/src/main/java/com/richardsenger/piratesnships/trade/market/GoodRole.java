package com.richardsenger.piratesnships.trade.market;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** What a port does with a good. */
public enum GoodRole implements StringRepresentable {
    /** Made here: cheap, large stock, absorbs little. */
    PRODUCES,
    /** Traded at the base price. */
    NEUTRAL,
    /** Wanted here: expensive, small stock, absorbs a lot. */
    DEMANDS,
    /** Neither bought nor sold here. */
    NOT_TRADED;

    public static final Codec<GoodRole> CODEC = StringRepresentable.fromEnum(GoodRole::values);

    public boolean traded() {
        return this != NOT_TRADED;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
