package com.richardsenger.piratesnships.crew.provisions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;

/**
 * One kind of provision, e.g. "minecraft:bread". Pure data: the classifier ({@link ProvisionClassifier}) derives it
 * from an item, tests build it directly.
 *
 * @param id             stable key, the item id for real items
 * @param kind           food, water or rum
 * @param valuePerUnit   nutrition (food), water rations (water) or rum rations (rum) per unit (item)
 * @param preserved      preserved food never spoils (always true for water and rum)
 * @param antiScurvy     eating it resets the scurvy clock (citrus, and fresh food when so configured)
 * @param weightPerUnit  cargo weight per unit (design.md §4.9)
 * @param shelfLifeTicks ticks until a unit spoils; {@code 0} = never spoils
 */
public record ProvisionType(String id, ProvisionKind kind, double valuePerUnit, boolean preserved, boolean antiScurvy,
                            double weightPerUnit, long shelfLifeTicks) {

    public static final Codec<ProvisionType> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(ProvisionType::id),
            ProvisionKind.CODEC.fieldOf("kind").forGetter(ProvisionType::kind),
            Codec.DOUBLE.fieldOf("value").forGetter(ProvisionType::valuePerUnit),
            Codec.BOOL.optionalFieldOf("preserved", false).forGetter(ProvisionType::preserved),
            Codec.BOOL.optionalFieldOf("anti_scurvy", false).forGetter(ProvisionType::antiScurvy),
            Codec.DOUBLE.optionalFieldOf("weight", 0.0).forGetter(ProvisionType::weightPerUnit),
            Codec.LONG.optionalFieldOf("shelf_life", 0L).forGetter(ProvisionType::shelfLifeTicks)
    ).apply(i, ProvisionType::new));

    public ProvisionType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        if (!(valuePerUnit > 0)) {
            throw new IllegalArgumentException("Provision value must be positive: " + id);
        }
        if (weightPerUnit < 0 || shelfLifeTicks < 0) {
            throw new IllegalArgumentException("Negative weight or shelf life: " + id);
        }
        if (kind != ProvisionKind.FOOD || preserved) {
            shelfLifeTicks = 0;
        }
    }

    /** Whether this provision can spoil at all (fresh food with a shelf life). */
    public boolean perishable() {
        return shelfLifeTicks > 0;
    }

    public static ProvisionType food(String id, double nutrition, boolean preserved, boolean antiScurvy, double weight, long shelfLifeTicks) {
        return new ProvisionType(id, ProvisionKind.FOOD, nutrition, preserved, antiScurvy, weight, shelfLifeTicks);
    }

    public static ProvisionType water(String id, double rations, double weight) {
        return new ProvisionType(id, ProvisionKind.WATER, rations, true, false, weight, 0);
    }

    public static ProvisionType rum(String id, double rations, double weight) {
        return new ProvisionType(id, ProvisionKind.RUM, rations, true, false, weight, 0);
    }
}
