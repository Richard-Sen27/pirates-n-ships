package com.richardsenger.piratesnships.trade.good;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** Coarse kind of a trade good. Port kinds use it to decide what they produce and demand (see {@code ProfileDeriver}). */
public enum GoodCategory implements StringRepresentable {
    /** Food and staples: fish, grain. */
    FOOD,
    /** Plantation crops: sugar, tobacco, cocoa. */
    CROP,
    /** Bulk raw materials: timber, hides. */
    RAW_MATERIAL,
    /** Metals: iron. */
    METAL,
    /** Goods made in workshops: cloth, rum, gunpowder. */
    MANUFACTURED,
    /** Light, expensive goods: spices. */
    LUXURY;

    public static final Codec<GoodCategory> CODEC = StringRepresentable.fromEnum(GoodCategory::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
