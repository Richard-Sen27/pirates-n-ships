package com.richardsenger.piratesnships.trade.market;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Coarse climate of a port, derived from its biome by the port integration (e.g. jungle/mangrove = tropical,
 * desert/badlands/savanna = arid, snowy/taiga = cold, everything else = temperate).
 */
public enum Climate implements StringRepresentable {
    TROPICAL, TEMPERATE, ARID, COLD;

    public static final Codec<Climate> CODEC = StringRepresentable.fromEnum(Climate::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
