package com.richardsenger.piratesnships.law.world;

import com.richardsenger.piratesnships.Constants;

import java.util.List;

/**
 * Noticed plunder (LAW3, docs/design.md §13.4), the pure rule: how many plunder-marked units a ship carries and when a
 * navy observer suspects piracy. The world side is {@link PlunderAboard} (it turns a ship's containers into
 * {@link Holding}s) and {@link FlagCrimes} (the observation that calls it).
 */
public final class PlunderNotice {

    /** Message to the owner when a navy observer records {@code suspected_piracy}. */
    public static final String SPOTTED_KEY = "message." + Constants.MOD_ID + ".law.plunder_spotted";

    /**
     * @param enabled {@code law.plunder_notice}: refusals are reported and observers look for plunder
     * @param units   {@code law.plunder_notice_units}: marked units a ship may carry unnoticed (more is noticed)
     */
    public record Params(boolean enabled, int units) {
        public static final Params DEFAULTS = new Params(true, 16);
    }

    /**
     * One stack (or a bulk container's load) of {@code count} units, plunder-marked or not, each unit carrying
     * {@code inner} (a shulker box's contents, a filled crate's cargo). The adapter stops nesting where the cargo
     * weighing does (two levels below the container).
     */
    public record Holding(int count, boolean marked, List<Holding> inner) {
        public Holding {
            count = Math.max(0, count);
            inner = List.copyOf(inner);
        }

        public static Holding of(int count, boolean marked) {
            return new Holding(count, marked, List.of());
        }
    }

    private PlunderNotice() {
    }

    /** Marked units in {@code holdings}: a marked stack counts its units, nested content counts once per carrying unit. */
    public static long markedUnits(Iterable<Holding> holdings) {
        long n = 0;
        for (Holding h : holdings) n += markedUnits(h);
        return n;
    }

    public static long markedUnits(Holding h) {
        long n = h.marked() ? h.count() : 0;
        if (!h.inner().isEmpty() && h.count() > 0) n += (long) h.count() * markedUnits(h.inner());
        return n;
    }

    /** Whether an observer notices {@code markedUnits} aboard: more than {@code units}, while the notice is on. */
    public static boolean noticed(long markedUnits, Params p) {
        return p.enabled() && markedUnits > Math.max(0, p.units());
    }
}
