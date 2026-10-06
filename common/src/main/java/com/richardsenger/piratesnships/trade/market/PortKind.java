package com.richardsenger.piratesnships.trade.market;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.trade.good.GoodCategory;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * The three kinds of port (design.md §10.1). The weights shape {@link ProfileDeriver}: how likely a port of this kind
 * produces or demands a good of a category, and how likely it doesn't trade a good at all. They are design constants
 * of the profile derivation, not balance values (prices and amounts are config).
 */
public enum PortKind implements StringRepresentable {
    //                     produce:  FOOD CROP RAW  METAL MANUF LUX     demand:  FOOD CROP RAW  METAL MANUF LUX    not traded
    SEAFARER_VILLAGE(new double[]{1.0, 1.0, 1.0, 0.6, 0.3, 0.8}, new double[]{0.6, 0.6, 0.6, 1.2, 1.5, 1.2}, 0.4),
    /** Supplied from home: produces metal and manufactured goods, needs food and timber for the garrison. */
    NAVY_OUTPOST(new double[]{0.2, 0.2, 0.3, 1.5, 1.5, 0.1}, new double[]{1.5, 0.5, 1.5, 0.4, 0.5, 0.8}, 1.0),
    /** Produces little, wants rum, powder, cloth and luxuries, and trades the fewest goods. */
    PIRATE_ISLAND(new double[]{0.4, 0.5, 0.4, 0.1, 0.2, 0.2}, new double[]{1.2, 0.6, 0.5, 1.0, 1.8, 1.5}, 1.6);

    public static final Codec<PortKind> CODEC = StringRepresentable.fromEnum(PortKind::values);

    private final double[] produce;
    private final double[] demand;
    private final double notTraded;

    PortKind(double[] produce, double[] demand, double notTraded) {
        this.produce = produce;
        this.demand = demand;
        this.notTraded = notTraded;
    }

    public double produceWeight(GoodCategory category) {
        return produce[category.ordinal()];
    }

    public double demandWeight(GoodCategory category) {
        return demand[category.ordinal()];
    }

    public double notTradedWeight() {
        return notTraded;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
