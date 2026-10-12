package com.richardsenger.piratesnships.chart.data;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.trade.market.PortKind;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * What a structure on the chart is (work package CHART2): the three port kinds, a wreck, or another structure of this
 * mod. Picks the icon and the legend line. The ordinal is the network id: never reorder, add at the end.
 */
public enum StructureKind {
    VILLAGE,
    OUTPOST,
    ISLAND,
    WRECK,
    OTHER;

    private static final StructureKind[] VALUES = values();

    public static final StreamCodec<ByteBuf, StructureKind> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(StructureKind::of, Enum::ordinal);

    public static StructureKind of(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : OTHER;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Whether a structure of this kind is a port (it is drawn from the port registry, not from a sighting). */
    public boolean port() {
        return this == VILLAGE || this == OUTPOST || this == ISLAND;
    }

    public static StructureKind of(PortKind kind) {
        return switch (kind) {
            case SEAFARER_VILLAGE -> VILLAGE;
            case NAVY_OUTPOST -> OUTPOST;
            case PIRATE_ISLAND -> ISLAND;
        };
    }

    /**
     * The kind of a structure by its registry id: this mod's {@code seafarer_village}, {@code navy_outpost},
     * {@code pirate_island} and {@code wreck}; any other structure of this mod is {@link #OTHER}. {@code null} for a
     * structure of another namespace (vanilla structures never go on the chart).
     */
    public static StructureKind ofStructure(ResourceLocation id) {
        if (!Constants.MOD_ID.equals(id.getNamespace())) return null;
        return switch (id.getPath()) {
            case "seafarer_village" -> VILLAGE;
            case "navy_outpost" -> OUTPOST;
            case "pirate_island" -> ISLAND;
            case "wreck" -> WRECK;
            default -> OTHER;
        };
    }
}
