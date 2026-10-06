package com.richardsenger.piratesnships.crew.galley;

import com.richardsenger.piratesnships.crew.provisions.CrewHeadcount;
import com.richardsenger.piratesnships.crew.provisions.ProvisionLot;
import com.richardsenger.piratesnships.crew.provisions.ProvisionRules;
import com.richardsenger.piratesnships.crew.provisions.ProvisionSettings;
import com.richardsenger.piratesnships.crew.provisions.ProvisionStore;
import com.richardsenger.piratesnships.crew.provisions.ProvisionType;
import com.richardsenger.piratesnships.crew.provisions.ProvisionUpdate;
import com.richardsenger.piratesnships.crew.provisions.ProvisioningState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProvisionPoolTest {

    static final long DAY = ProvisionSettings.TICKS_PER_DAY;
    static final ProvisionSettings S = ProvisionSettings.DEFAULTS;
    static final ProvisionType TACK = ProvisionType.food("pirates_n_ships:hardtack", 4, true, false, 0.25, 0);
    static final ProvisionType BEEF = ProvisionType.food("minecraft:cooked_beef", 8, false, false, 0.25, 5 * DAY);
    static final ProvisionType BOTTLE = ProvisionType.water("minecraft:potion", 1, 1.0);
    static final ProvisionType BUCKET = ProvisionType.water("minecraft:water_bucket", 3, 3.0);
    static final ProvisionType RUM = ProvisionType.rum("pirates_n_ships:rum", 1, 0.5);

    static ProvisionStore pantryA() {
        return ProvisionStore.of(new ProvisionLot(TACK, 20, 0), new ProvisionLot(BEEF, 3, 4 * DAY), new ProvisionLot(BEEF, 5, DAY));
    }

    static ProvisionStore pantryB() {
        return ProvisionStore.of(new ProvisionLot(BEEF, 4, 2 * DAY), new ProvisionLot(BOTTLE, 4, 0), new ProvisionLot(RUM, 10, 0),
                new ProvisionLot(BUCKET, 2, 0));
    }

    static ProvisionStore barrel() {
        return WaterBarrelRules.store(16, S);
    }

    static List<ProvisionStore> ship() {
        return List.of(pantryA(), pantryB(), barrel());
    }

    static ProvisionUpdate advance(List<ProvisionStore> stores, int crew, long ticks) {
        return ProvisionRules.advance(ProvisionPool.combine(stores), ProvisioningState.INITIAL, CrewHeadcount.crew(crew), S, ticks);
    }

    @Test
    void piecesAddUpToTheRulesStore() {
        for (long days : new long[]{1, 3, 6, 12}) {
            List<ProvisionStore> stores = ship();
            ProvisionUpdate u = advance(stores, 4, days * DAY);
            List<ProvisionPool.Share> shares = ProvisionPool.split(stores, u.outcome());
            List<ProvisionStore> after = new ArrayList<>();
            for (int i = 0; i < stores.size(); i++) {
                after.add(ProvisionPool.remaining(stores.get(i), shares.get(i), days * DAY));
            }
            assertEquals(u.store(), ProvisionPool.combine(after), "after " + days + " days");
        }
    }

    @Test
    void sharesAddUpAndNeverExceedAContainer() {
        List<ProvisionStore> stores = ship();
        ProvisionUpdate u = advance(stores, 1, 7 * DAY);
        List<ProvisionPool.Share> shares = ProvisionPool.split(stores, u.outcome());
        Map<String, Integer> consumed = new TreeMap<>(), spoiled = new TreeMap<>();
        for (int i = 0; i < stores.size(); i++) {
            ProvisionPool.Share sh = shares.get(i);
            sh.consumed().forEach((k, v) -> consumed.merge(k, v, Integer::sum));
            sh.spoiled().forEach((k, v) -> spoiled.merge(k, v, Integer::sum));
            for (Map.Entry<String, Integer> e : sh.removed().entrySet()) {
                assertTrue(e.getValue() <= stores.get(i).units(e.getKey()), "container " + i + " gives more " + e.getKey() + " than it has");
            }
        }
        assertEquals(new TreeMap<>(u.outcome().consumed()), consumed);
        assertEquals(new TreeMap<>(u.outcome().spoiled()), spoiled);
        assertTrue(!spoiled.isEmpty(), "the scenario should include spoilage");
    }

    @Test
    void waterComesFromWhereItWasDrunk() {
        List<ProvisionStore> stores = ship();
        // bottles first (smallest unit, id before barrel water), then the barrel, buckets last
        ProvisionUpdate u = advance(stores, 4, 2 * DAY);
        List<ProvisionPool.Share> shares = ProvisionPool.split(stores, u.outcome());
        int water = (int) Math.ceil(2 * 4 * S.waterPerCrewPerDay() - 1e-9);
        assertEquals(Math.min(4, water), shares.get(1).consumed().getOrDefault(BOTTLE.id(), 0), "bottles from pantry B");
        assertEquals(Math.max(0, water - 4), shares.get(2).consumed().getOrDefault(WaterBarrelRules.WATER_ID, 0), "barrel water from the barrel");
        assertEquals(0, shares.get(1).consumed().getOrDefault(BUCKET.id(), 0), "buckets are drunk last");
        assertTrue(shares.get(0).consumed().keySet().stream().noneMatch(k -> k.equals(BOTTLE.id()) || k.equals(WaterBarrelRules.WATER_ID)));
    }

    @Test
    void oldestLotsGoFirstAcrossContainers() {
        List<ProvisionStore> stores = ship();
        // 5 beef: the 3 oldest (4 days, pantry A), then 2 of the 2-day-old lot in pantry B
        List<ProvisionPool.Share> shares = ProvisionPool.split(stores, Map.of(BEEF.id(), 5), Map.of());
        assertEquals(3, shares.get(0).consumed().get(BEEF.id()));
        assertEquals(2, shares.get(1).consumed().get(BEEF.id()));
        ProvisionStore a = ProvisionPool.remaining(stores.get(0), shares.get(0), 0);
        assertEquals(List.of(new ProvisionLot(BEEF, 5, DAY), new ProvisionLot(TACK, 20, 0)), a.lots());
    }

    @Test
    void unknownOrExcessUnitsAreNotRemovedTwice() {
        List<ProvisionStore> stores = ship();
        List<ProvisionPool.Share> shares = ProvisionPool.split(stores, Map.of("minecraft:cake", 3, RUM.id(), 50), Map.of(BEEF.id(), 2));
        assertEquals(10, shares.get(1).consumed().get(RUM.id()));
        assertTrue(shares.stream().noneMatch(s -> s.removed().containsKey("minecraft:cake")));
        assertEquals(2, shares.get(0).spoiled().get(BEEF.id()), "spoiled units come from the oldest lot");
    }

    @Test
    void rewindingMakesLotsYoungerButNotNegative() {
        ProvisionStore r = ProvisionPool.rewound(pantryA(), 2 * DAY);
        assertEquals(List.of(new ProvisionLot(BEEF, 3, 2 * DAY), new ProvisionLot(BEEF, 5, 0), new ProvisionLot(TACK, 20, 0)), r.lots());
        assertEquals(pantryA(), ProvisionPool.rewound(pantryA(), 0));
    }

    @Test
    void combinedWeightIsTheSumOfTheContainers() {
        double sum = pantryA().totalWeight() + pantryB().totalWeight() + barrel().totalWeight();
        assertEquals(sum, ProvisionPool.combine(ship()).totalWeight(), 1e-9);
        assertEquals(16 * S.waterWeightPerRation(), barrel().totalWeight(), 1e-9);
    }
}
