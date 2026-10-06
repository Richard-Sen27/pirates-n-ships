package com.richardsenger.piratesnships.crew.provisions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;

/**
 * A number of units of one provision type that all have the same age. A store can hold several lots of the same type
 * (bread bought last week and bread bought today).
 *
 * @param ageTicks how long this lot has been aging in the store (only advances while spoilage is on)
 */
public record ProvisionLot(ProvisionType type, int units, long ageTicks) {

    public static final Codec<ProvisionLot> CODEC = RecordCodecBuilder.create(i -> i.group(
            ProvisionType.CODEC.fieldOf("type").forGetter(ProvisionLot::type),
            Codec.INT.fieldOf("units").forGetter(ProvisionLot::units),
            Codec.LONG.optionalFieldOf("age", 0L).forGetter(ProvisionLot::ageTicks)
    ).apply(i, ProvisionLot::new));

    public ProvisionLot {
        Objects.requireNonNull(type, "type");
        if (units < 0 || ageTicks < 0) {
            throw new IllegalArgumentException("Negative units or age in lot of " + type.id());
        }
    }

    /** Ticks until this lot spoils, or {@link Long#MAX_VALUE} if it never does. */
    public long remainingShelfLife() {
        return type.perishable() ? Math.max(0, type.shelfLifeTicks() - ageTicks) : Long.MAX_VALUE;
    }

    public double totalValue() {
        return units * type.valuePerUnit();
    }

    public double totalWeight() {
        return units * type.weightPerUnit();
    }

    public ProvisionLot withUnits(int newUnits) {
        return new ProvisionLot(type, newUnits, ageTicks);
    }
}
