package com.richardsenger.piratesnships.crew.galley;

import com.richardsenger.piratesnships.crew.provisions.ProvisionKind;
import com.richardsenger.piratesnships.crew.provisions.ProvisionLot;
import com.richardsenger.piratesnships.crew.provisions.ProvisionSettings;
import com.richardsenger.piratesnships.crew.provisions.ProvisionStore;

import java.util.Optional;

/**
 * What a pantry (or a group of containers) holds in provisions terms, for the info text and the debug command. Pure.
 *
 * @param foodItems     food items
 * @param nutrition     their total nutrition
 * @param waterRations  water rations
 * @param rumItems      rum items
 * @param weight        cargo weight (design.md §4.9)
 * @param nextSpoil     the lot that spoils next, if spoilage is on and something can spoil
 */
public record PantryInfo(int foodItems, double nutrition, double waterRations, int rumItems, double weight,
                         Optional<NextSpoil> nextSpoil) {

    /** {@code units} of item {@code id} spoil in {@code ticks}. */
    public record NextSpoil(String id, int units, long ticks) {
        public double days() {
            return ticks / (double) ProvisionSettings.TICKS_PER_DAY;
        }
    }

    public static PantryInfo of(ProvisionStore store, ProvisionSettings s) {
        int food = 0, rum = 0;
        ProvisionLot next = null;
        for (ProvisionLot lot : store.lots()) {
            switch (lot.type().kind()) {
                case FOOD -> food += lot.units();
                case RUM -> rum += lot.units();
                case WATER -> { }
            }
            if (s.spoilageEnabled() && s.consumptionEnabled() && lot.type().perishable()
                    && (next == null || lot.remainingShelfLife() < next.remainingShelfLife())) {
                next = lot;
            }
        }
        ProvisionLot n = next;
        Optional<NextSpoil> spoil = Optional.ofNullable(n).map(l -> new NextSpoil(l.type().id(), l.units(), l.remainingShelfLife()));
        return new PantryInfo(food, store.totalValue(ProvisionKind.FOOD), store.totalValue(ProvisionKind.WATER), rum,
                store.totalWeight(), spoil);
    }

    public boolean isEmpty() {
        return foodItems == 0 && waterRations == 0 && rumItems == 0;
    }
}
