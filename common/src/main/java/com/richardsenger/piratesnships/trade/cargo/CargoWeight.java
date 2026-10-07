package com.richardsenger.piratesnships.trade.cargo;

import com.richardsenger.piratesnships.trade.good.TradeGoodIndex;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.function.Function;

/**
 * Cargo weight and load level (design.md §4.9). Units are the {@code crew.provisions} weight units (one food item =
 * 0.25, one water ration = 1, one rum bottle = 0.5): a trade good weighs its definition's {@code weight} per item, any
 * other item {@link Params#defaultItemWeight()}.
 *
 * <p><b>Ship total</b> = {@code CargoWeight.total(cargo stacks)} + {@code ProvisionStore.totalWeight()}, where the cargo
 * stacks are everything in cargo containers that is <i>not</i> in the provision store (so nothing is counted twice).
 * Then {@link #shipEffect} applies the toggle and the weight factor, and {@link LoadLevel#of} the capacity.
 */
public final class CargoWeight {

    /**
     * @param affectsShips       "cargo weight affects ships": off = {@link #shipEffect} is 0 (load level still shown)
     * @param weightFactor       multiplier from cargo weight to the weight applied to the ship
     * @param defaultItemWeight  weight of one item that isn't a trade good
     * @param ladenAt            load ratio (weight / capacity) from which a ship is laden
     * @param heavyAt            ratio from which it is heavily laden
     * @param overloadedAt       ratio above which it is overloaded
     */
    public record Params(boolean affectsShips, double weightFactor, double defaultItemWeight,
                         double ladenAt, double heavyAt, double overloadedAt) {
        public static final Params DEFAULTS = new Params(true, 1.0, 0.25, 0.33, 0.75, 1.0);
    }

    public enum LoadLevel {
        LIGHT, LADEN, HEAVILY_LADEN, OVERLOADED;

        /** Translation key for the HUD and helm GUI. */
        public String translationKey() {
            return "pirates_n_ships.load_level." + name().toLowerCase(Locale.ROOT);
        }

        /** The level for {@code weight} on a ship that carries {@code capacity} (≤ 0 capacity = overloaded if anything is aboard). */
        public static LoadLevel of(double weight, double capacity, Params p) {
            if (weight <= 0) return LIGHT;
            if (capacity <= 0) return OVERLOADED;
            double ratio = weight / capacity;
            if (ratio > p.overloadedAt()) return OVERLOADED;
            if (ratio >= p.heavyAt()) return HEAVILY_LADEN;
            if (ratio >= p.ladenAt()) return LADEN;
            return LIGHT;
        }
    }

    /**
     * Default ship capacity per block [weight units] (CW1): the starter sloop (680 blocks) holds 5,440 units, so its six
     * cargo containers at their nominal weight ({@link CargoMass}, 5,376 units) make it heavily laden, and anything on
     * top overloads it.
     */
    public static final double CAPACITY_PER_BLOCK = 8.0;

    private CargoWeight() {
    }

    /** A ship's cargo capacity: {@code blocks × perBlock} (0 for an empty ship). */
    public static double shipCapacity(int blocks, double perBlock) {
        return Math.max(0, blocks) * Math.max(0, perBlock);
    }

    /** Weight of {@code count} items with id {@code item}. */
    public static double weightOf(ResourceLocation item, int count, TradeGoodIndex goods, Params p) {
        if (count <= 0) return 0;
        double perItem = goods.byItem(item).map(e -> e.good().weight()).orElse(p.defaultItemWeight());
        return perItem * count;
    }

    /** Total weight of stacks (empty stacks weigh nothing). */
    public static double total(Iterable<ItemStack> stacks, TradeGoodIndex goods, Params p) {
        return total(stacks, s -> BuiltInRegistries.ITEM.getKey(s.getItem()), goods, p);
    }

    static double total(Iterable<ItemStack> stacks, Function<ItemStack, ResourceLocation> idOf, TradeGoodIndex goods, Params p) {
        double w = 0;
        for (ItemStack s : stacks) {
            if (s.isEmpty()) continue;
            w += weightOf(idOf.apply(s), s.getCount(), goods, p);
        }
        return w;
    }

    /** Cargo plus provisions (see the class comment). */
    public static double shipTotal(double cargoWeight, double provisionsWeight) {
        return Math.max(0, cargoWeight) + Math.max(0, provisionsWeight);
    }

    /** The weight the ship physics should apply: 0 when the toggle is off, else weight × factor. */
    public static double shipEffect(double totalWeight, Params p) {
        return p.affectsShips() ? Math.max(0, totalWeight) * Math.max(0, p.weightFactor()) : 0.0;
    }
}
