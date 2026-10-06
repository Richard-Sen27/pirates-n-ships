package com.richardsenger.piratesnships.law.flag;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** The flag a ship flies (docs/design.md §4.7). The flag block package stores one of these. */
public enum FlagKind implements StringRepresentable {
    NONE("none"),
    MERCHANT("merchant"),
    NAVY("navy"),
    JOLLY_ROGER("jolly_roger"),
    /** Made from banner patterns; treated as neutral. */
    CUSTOM("custom");

    public static final Codec<FlagKind> CODEC = StringRepresentable.fromEnum(FlagKind::values);

    private final String id;

    FlagKind(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
