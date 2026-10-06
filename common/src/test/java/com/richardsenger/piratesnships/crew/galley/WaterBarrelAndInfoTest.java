package com.richardsenger.piratesnships.crew.galley;

import com.richardsenger.piratesnships.crew.provisions.ProvisionKind;
import com.richardsenger.piratesnships.crew.provisions.ProvisionLot;
import com.richardsenger.piratesnships.crew.provisions.ProvisionSettings;
import com.richardsenger.piratesnships.crew.provisions.ProvisionStore;
import com.richardsenger.piratesnships.crew.provisions.ProvisionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterBarrelAndInfoTest {

    static final ProvisionSettings S = ProvisionSettings.DEFAULTS;
    static final long DAY = ProvisionSettings.TICKS_PER_DAY;

    @Test
    void capacityMatchesTheClassifiersFullBarrel() {
        assertEquals(S.waterBarrelRations(), WaterBarrelRules.capacity(S));
        assertEquals(S.waterBarrelRations(), WaterBarrelRules.store(S.waterBarrelRations(), S).totalValue(ProvisionKind.WATER), 1e-9);
        assertTrue(WaterBarrelRules.store(0, S).isEmpty());
    }

    @Test
    void fillAndDrainOnlyWholeAmounts() {
        assertEquals(3, WaterBarrelRules.fill(0, 3, 16));
        assertEquals(16, WaterBarrelRules.fill(13, 3, 16));
        assertEquals(-1, WaterBarrelRules.fill(14, 3, 16), "a bucket that does not fit");
        assertEquals(-1, WaterBarrelRules.fill(5, 0, 16));
        assertEquals(2, WaterBarrelRules.drain(5, 3));
        assertEquals(-1, WaterBarrelRules.drain(2, 3), "not enough for a bucket");
        assertEquals(0, WaterBarrelRules.drain(1, 1));
        int r = WaterBarrelRules.fill(0, 3, 16);
        r = WaterBarrelRules.fill(r, 1, 16);
        r = WaterBarrelRules.drain(r, 1);
        r = WaterBarrelRules.drain(r, 3);
        assertEquals(0, r, "water is neither created nor lost");
    }

    @Test
    void rainAddsOneRationWhenLuckyOpenAndEnabled() {
        assertEquals(5, WaterBarrelRules.rain(4, 16, true, true, 0.5, 0.2));
        assertEquals(4, WaterBarrelRules.rain(4, 16, true, true, 0.5, 0.7), "unlucky roll");
        assertEquals(4, WaterBarrelRules.rain(4, 16, false, true, 1.0, 0.0), "toggle off");
        assertEquals(4, WaterBarrelRules.rain(4, 16, true, false, 1.0, 0.0), "no rain above");
        assertEquals(16, WaterBarrelRules.rain(16, 16, true, true, 1.0, 0.0), "full");
        assertEquals(4, WaterBarrelRules.rain(4, 16, true, true, 0.0, 0.0), "chance 0");
    }

    @Test
    void fillLevelAndComparator() {
        assertEquals(0, WaterBarrelRules.fillLevel(0, 16));
        assertEquals(1, WaterBarrelRules.fillLevel(1, 16));
        assertEquals(2, WaterBarrelRules.fillLevel(8, 16));
        assertEquals(3, WaterBarrelRules.fillLevel(15, 16));
        assertEquals(4, WaterBarrelRules.fillLevel(16, 16));
        assertEquals(0, WaterBarrelRules.comparator(0, 16));
        assertEquals(1, WaterBarrelRules.comparator(1, 16));
        assertEquals(7, WaterBarrelRules.comparator(8, 16));
        assertEquals(15, WaterBarrelRules.comparator(16, 16));
        for (int i = 0; i < 16; i++) {
            assertTrue(WaterBarrelRules.comparator(i, 16) <= WaterBarrelRules.comparator(i + 1, 16));
            assertTrue(WaterBarrelRules.fillLevel(i, 16) <= WaterBarrelRules.fillLevel(i + 1, 16));
        }
    }

    @Test
    void infoSumsKindsAndFindsWhatSpoilsNext() {
        ProvisionType beef = ProvisionType.food("minecraft:cooked_beef", 8, false, false, 0.25, 5 * DAY);
        ProvisionType apple = ProvisionType.food("minecraft:apple", 4, false, true, 0.25, 5 * DAY);
        ProvisionType tack = ProvisionType.food("pirates_n_ships:hardtack", 4, true, false, 0.25, 0);
        ProvisionStore store = ProvisionStore.of(new ProvisionLot(beef, 3, DAY), new ProvisionLot(apple, 2, 4 * DAY),
                new ProvisionLot(tack, 10, 0), new ProvisionLot(ProvisionType.water("minecraft:potion", 1, 1), 4, 0),
                new ProvisionLot(ProvisionType.rum("pirates_n_ships:rum", 1, 0.5), 6, 0));
        PantryInfo info = PantryInfo.of(store, S);
        assertEquals(15, info.foodItems());
        assertEquals(3 * 8 + 2 * 4 + 10 * 4, info.nutrition(), 1e-9);
        assertEquals(4, info.waterRations(), 1e-9);
        assertEquals(6, info.rumItems());
        assertEquals(store.totalWeight(), info.weight(), 1e-9);
        assertTrue(info.nextSpoil().isPresent());
        assertEquals("minecraft:apple", info.nextSpoil().get().id());
        assertEquals(2, info.nextSpoil().get().units());
        assertEquals(1.0, info.nextSpoil().get().days(), 1e-9);

        assertTrue(PantryInfo.of(store, S.toBuilder().spoilageEnabled(false).build()).nextSpoil().isEmpty());
        assertTrue(PantryInfo.of(ProvisionStore.of(new ProvisionLot(tack, 1, 0)), S).nextSpoil().isEmpty());
        assertTrue(PantryInfo.of(ProvisionStore.EMPTY, S).isEmpty());
    }
}
