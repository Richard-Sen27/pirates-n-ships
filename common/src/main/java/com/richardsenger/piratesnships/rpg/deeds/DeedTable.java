package com.richardsenger.piratesnships.rpg.deeds;

import com.richardsenger.piratesnships.rpg.reputation.Faction;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * The reputation delta of every {@link Deed} for every {@link Faction}. Pure: {@link #read} copies it from any source
 * (the config values {@code reputation.deeds.<deed>.<faction>}, see {@code ReputationConfig#deedTable()}), and
 * missing or out-of-range entries fall back to the deed's default or are clamped to −100..100.
 */
public record DeedTable(Map<Deed, Map<Faction, Integer>> deltas) {

    public static final int LIMIT = 100;

    public DeedTable {
        Map<Deed, Map<Faction, Integer>> copy = new EnumMap<>(Deed.class);
        for (Deed d : Deed.values()) {
            Map<Faction, Integer> row = new EnumMap<>(Faction.class);
            Map<Faction, Integer> given = deltas == null ? null : deltas.get(d);
            for (Faction f : Faction.values()) {
                Integer v = given == null ? null : given.get(f);
                row.put(f, clamp(v == null ? d.defaultDelta(f) : v));
            }
            copy.put(d, Collections.unmodifiableMap(row));
        }
        deltas = Collections.unmodifiableMap(copy);
    }

    /** The table of the deeds' default deltas. */
    public static DeedTable defaults() {
        return new DeedTable(Map.of());
    }

    /** Reads every entry from {@code source}; a {@code null} answer keeps the default. */
    public static DeedTable read(BiFunction<Deed, Faction, Integer> source) {
        Map<Deed, Map<Faction, Integer>> m = new EnumMap<>(Deed.class);
        for (Deed d : Deed.values()) {
            Map<Faction, Integer> row = new EnumMap<>(Faction.class);
            for (Faction f : Faction.values()) {
                Integer v = source.apply(d, f);
                if (v != null) row.put(f, v);
            }
            m.put(d, row);
        }
        return new DeedTable(m);
    }

    public int delta(Deed deed, Faction faction) {
        return deltas.get(deed).get(faction);
    }

    /** The non-zero deltas of {@code deed}. */
    public Map<Faction, Integer> row(Deed deed) {
        Map<Faction, Integer> out = new EnumMap<>(Faction.class);
        deltas.get(deed).forEach((f, v) -> {
            if (v != 0) out.put(f, v);
        });
        return out;
    }

    private static int clamp(int v) {
        return Math.max(-LIMIT, Math.min(LIMIT, v));
    }
}
