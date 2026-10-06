package com.richardsenger.piratesnships.crew.galley;

import com.richardsenger.piratesnships.crew.provisions.ProvisionLot;
import com.richardsenger.piratesnships.crew.provisions.ProvisionOutcome;
import com.richardsenger.piratesnships.crew.provisions.ProvisionStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Combines the stores of several provision containers (pantries, water barrels) into one ship store and splits the
 * result of {@link com.richardsenger.piratesnships.crew.provisions.ProvisionRules#advance} back over them. Pure.
 *
 * <p>Split rule: per type id, units are taken from the <em>oldest</em> lots first, over all containers (equal ages: the
 * container listed first). That is the lot the rules themselves eat or spoil first, so every container ends up with
 * exactly the lots the combined store keeps. Spoiled units are split first, consumed units from what is left. Because
 * each container has its own type ids for its own kind of storage (barrel water is {@link WaterBarrelRules#WATER_ID},
 * a water bottle in a pantry is {@code minecraft:potion}), water is always taken from where the rules drank it.
 */
public final class ProvisionPool {

    private ProvisionPool() {
    }

    /** What to take out of one container. */
    public record Share(Map<String, Integer> consumed, Map<String, Integer> spoiled) {

        public static final Share NONE = new Share(Map.of(), Map.of());

        public Share {
            consumed = Map.copyOf(consumed);
            spoiled = Map.copyOf(spoiled);
        }

        /** Consumed plus spoiled. */
        public Map<String, Integer> removed() {
            Map<String, Integer> all = new TreeMap<>(consumed);
            spoiled.forEach((k, v) -> all.merge(k, v, Integer::sum));
            return all;
        }

        public boolean isEmpty() {
            return consumed.isEmpty() && spoiled.isEmpty();
        }
    }

    /** All lots of all stores in one store (same type and age merge). */
    public static ProvisionStore combine(List<ProvisionStore> stores) {
        List<ProvisionLot> all = new ArrayList<>();
        for (ProvisionStore s : stores) {
            all.addAll(s.lots());
        }
        return ProvisionStore.of(all);
    }

    /**
     * Splits an outcome's consumed and spoiled units over the containers whose stores were combined. Units no
     * container holds are dropped (they cannot be removed twice). Returns one share per store, in the same order.
     */
    public static List<Share> split(List<ProvisionStore> stores, ProvisionOutcome outcome) {
        return split(stores, outcome.consumed(), outcome.spoiled());
    }

    public static List<Share> split(List<ProvisionStore> stores, Map<String, Integer> consumed, Map<String, Integer> spoiled) {
        int n = stores.size();
        // remaining units per container and lot, mutated while splitting
        List<int[]> left = new ArrayList<>(n);
        for (ProvisionStore s : stores) {
            int[] units = new int[s.lots().size()];
            for (int i = 0; i < units.length; i++) {
                units[i] = s.lots().get(i).units();
            }
            left.add(units);
        }
        List<Map<String, Integer>> spoiledOut = takeAll(stores, left, spoiled);
        List<Map<String, Integer>> consumedOut = takeAll(stores, left, consumed);
        List<Share> shares = new ArrayList<>(n);
        for (int c = 0; c < n; c++) {
            shares.add(new Share(consumedOut.get(c), spoiledOut.get(c)));
        }
        return shares;
    }

    private static List<Map<String, Integer>> takeAll(List<ProvisionStore> stores, List<int[]> left, Map<String, Integer> wanted) {
        List<Map<String, Integer>> out = new ArrayList<>();
        for (int c = 0; c < stores.size(); c++) {
            out.add(new TreeMap<>());
        }
        for (Map.Entry<String, Integer> e : new TreeMap<>(wanted).entrySet()) {
            int want = e.getValue();
            // (container, lot) pairs of this id, oldest first, then container order
            List<int[]> refs = new ArrayList<>();
            for (int c = 0; c < stores.size(); c++) {
                List<ProvisionLot> lots = stores.get(c).lots();
                for (int i = 0; i < lots.size(); i++) {
                    if (lots.get(i).type().id().equals(e.getKey())) {
                        refs.add(new int[]{c, i});
                    }
                }
            }
            refs.sort(Comparator.<int[]>comparingLong(r -> -stores.get(r[0]).lots().get(r[1]).ageTicks()).thenComparingInt(r -> r[0]));
            for (int[] r : refs) {
                if (want <= 0) {
                    break;
                }
                int[] units = left.get(r[0]);
                int take = Math.min(want, units[r[1]]);
                if (take > 0) {
                    units[r[1]] -= take;
                    want -= take;
                    out.get(r[0]).merge(e.getKey(), take, Integer::sum);
                }
            }
        }
        return out;
    }

    /**
     * The store a container keeps after its share was removed: oldest lots of each id lose their units first, and
     * every remaining lot ages by {@code agedTicks} (the elapsed time when spoilage is on, else 0).
     */
    public static ProvisionStore remaining(ProvisionStore store, Share share, long agedTicks) {
        Map<String, Integer> toRemove = new TreeMap<>(share.removed());
        List<ProvisionLot> lots = new ArrayList<>(store.lots());
        lots.sort(Comparator.comparingLong(ProvisionLot::ageTicks).reversed());
        List<ProvisionLot> out = new ArrayList<>();
        for (ProvisionLot lot : lots) {
            int remove = Math.min(lot.units(), toRemove.getOrDefault(lot.type().id(), 0));
            if (remove > 0) {
                toRemove.merge(lot.type().id(), -remove, Integer::sum);
            }
            int keep = lot.units() - remove;
            if (keep > 0) {
                out.add(new ProvisionLot(lot.type(), keep, lot.ageTicks() + Math.max(0, agedTicks)));
            }
        }
        return ProvisionStore.of(out);
    }

    /**
     * A store with every lot made {@code ticks} younger (not below 0). Used to line a container's lot ages up with the
     * start of a period it already aged through on its own.
     */
    public static ProvisionStore rewound(ProvisionStore store, long ticks) {
        if (ticks <= 0) {
            return store;
        }
        List<ProvisionLot> out = new ArrayList<>();
        for (ProvisionLot lot : store.lots()) {
            out.add(new ProvisionLot(lot.type(), lot.units(), Math.max(0, lot.ageTicks() - ticks)));
        }
        return ProvisionStore.of(out);
    }

    /** Total units per id over the stores (for checks and reports). */
    public static Map<String, Integer> units(List<ProvisionStore> stores) {
        Map<String, Integer> out = new TreeMap<>();
        for (ProvisionStore s : stores) {
            for (ProvisionLot lot : s.lots()) {
                out.merge(lot.type().id(), lot.units(), Integer::sum);
            }
        }
        return out;
    }
}
