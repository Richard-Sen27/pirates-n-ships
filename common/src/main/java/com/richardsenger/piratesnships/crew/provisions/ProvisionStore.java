package com.richardsenger.piratesnships.crew.provisions;

import com.mojang.serialization.Codec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a pantry holds: an immutable list of {@link ProvisionLot}s. Cheap to copy (one list), every change returns a
 * new store. Lots with the same type and age are merged, empty lots are dropped, and lots are kept in a canonical order.
 *
 * <p>Persist it with {@link #CODEC} (block entity or ship data). The store is the authority on <em>ages</em>; the
 * pantry's item stacks are the authority on <em>amounts</em>. {@link #reconcile} brings the two together after the
 * container changed.
 */
public final class ProvisionStore {

    public static final ProvisionStore EMPTY = new ProvisionStore(List.of());

    public static final Codec<ProvisionStore> CODEC = ProvisionLot.CODEC.listOf().xmap(ProvisionStore::of, ProvisionStore::lots);

    /** Lots are kept in this order (kind, id, oldest first), so equal contents give equal stores. */
    private static final Comparator<ProvisionLot> CANONICAL_ORDER = Comparator
            .comparing((ProvisionLot l) -> l.type().kind())
            .thenComparing(l -> l.type().id())
            .thenComparing(Comparator.comparingLong(ProvisionLot::ageTicks).reversed());

    private final List<ProvisionLot> lots;

    private ProvisionStore(List<ProvisionLot> lots) {
        this.lots = lots;
    }

    /** A store of the given lots (merged and with empty lots dropped). */
    public static ProvisionStore of(List<ProvisionLot> lots) {
        Map<String, ProvisionLot> merged = new LinkedHashMap<>();
        for (ProvisionLot lot : lots) {
            if (lot.units() == 0) {
                continue;
            }
            String key = lot.type().id() + "@" + lot.ageTicks();
            ProvisionLot old = merged.get(key);
            if (old == null) {
                merged.put(key, lot);
            } else {
                // Same id and age: keep the newest type data (it may come from a fresher classification).
                merged.put(key, new ProvisionLot(lot.type(), old.units() + lot.units(), lot.ageTicks()));
            }
        }
        if (merged.isEmpty()) {
            return EMPTY;
        }
        List<ProvisionLot> sorted = new ArrayList<>(merged.values());
        sorted.sort(CANONICAL_ORDER);
        return new ProvisionStore(List.copyOf(sorted));
    }

    public static ProvisionStore of(ProvisionLot... lots) {
        return of(List.of(lots));
    }

    public List<ProvisionLot> lots() {
        return lots;
    }

    public boolean isEmpty() {
        return lots.isEmpty();
    }

    /** A copy with {@code units} new (age 0) units of {@code type} added. */
    public ProvisionStore add(ProvisionType type, int units) {
        List<ProvisionLot> l = new ArrayList<>(lots);
        l.add(new ProvisionLot(type, units, 0));
        return of(l);
    }

    /** Units of the type with this id, over all lots. */
    public int units(String id) {
        int n = 0;
        for (ProvisionLot lot : lots) {
            if (lot.type().id().equals(id)) {
                n += lot.units();
            }
        }
        return n;
    }

    /** Total value (nutrition or rations) of one kind. */
    public double totalValue(ProvisionKind kind) {
        double v = 0;
        for (ProvisionLot lot : lots) {
            if (lot.type().kind() == kind) {
                v += lot.totalValue();
            }
        }
        return v;
    }

    /** Total cargo weight of everything in the store (input for design.md §4.9). */
    public double totalWeight() {
        double w = 0;
        for (ProvisionLot lot : lots) {
            w += lot.totalWeight();
        }
        return w;
    }

    /**
     * Brings the store in line with the current container contents. {@code current} maps each type id to its
     * (freshly classified) type and the unit count actually present.
     *
     * <ul>
     *   <li>More units than the store knows: the extra units are a new lot of age 0.</li>
     *   <li>Fewer units: units are removed from the oldest lots first (someone took food out, or it was eaten).</li>
     *   <li>Types not present any more are dropped; every kept lot takes the fresh type data (so config changes to
     *       weights or shelf lives apply).</li>
     * </ul>
     */
    public ProvisionStore reconcile(Map<String, Counted> current) {
        List<ProvisionLot> out = new ArrayList<>();
        for (Map.Entry<String, Counted> e : current.entrySet()) {
            ProvisionType type = e.getValue().type();
            int wanted = e.getValue().units();
            List<ProvisionLot> known = new ArrayList<>();
            for (ProvisionLot lot : lots) {
                if (lot.type().id().equals(e.getKey())) {
                    known.add(new ProvisionLot(type, lot.units(), lot.ageTicks()));
                }
            }
            known.sort(Comparator.comparingLong(ProvisionLot::ageTicks)); // youngest first
            int have = known.stream().mapToInt(ProvisionLot::units).sum();
            if (wanted > have) {
                known.add(new ProvisionLot(type, wanted - have, 0));
            } else {
                int excess = have - wanted;
                for (int i = known.size() - 1; i >= 0 && excess > 0; i--) { // oldest first
                    ProvisionLot lot = known.get(i);
                    int take = Math.min(excess, lot.units());
                    known.set(i, lot.withUnits(lot.units() - take));
                    excess -= take;
                }
            }
            out.addAll(known);
        }
        return of(out);
    }

    /** A provision type with a unit count, the input of {@link #reconcile}. */
    public record Counted(ProvisionType type, int units) {
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ProvisionStore s && s.lots.equals(lots);
    }

    @Override
    public int hashCode() {
        return lots.hashCode();
    }

    @Override
    public String toString() {
        return "ProvisionStore" + lots;
    }
}
