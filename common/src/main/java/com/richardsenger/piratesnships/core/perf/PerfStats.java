package com.richardsenger.piratesnships.core.perf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The arithmetic of the performance test (PERF1, {@link PerformanceGameTests}), without a world: percentiles of tick
 * times, the per-ship cost between two runs, and the attribution of stack samples of the server thread to the mod's
 * methods and packages.
 */
public final class PerfStats {

    /** The mod's root package, as it appears in a stack frame's class name. */
    public static final String MOD_PACKAGE = "com.richardsenger.piratesnships.";
    /** Sable's root package. */
    public static final String SABLE_PACKAGE = "dev.ryanhcode.sable.";
    /** Frames of this class are the sampler's own and the test's; they never count as work. */
    private static final String PERF_PACKAGE = MOD_PACKAGE + "core.perf.";

    private PerfStats() {
    }

    // ------------------------------------------------------------------ tick times

    /** Minimum, median, 95th percentile, maximum and mean of a series [ms], and its length. */
    public record Summary(double min, double median, double p95, double max, double mean, int count) {

        public String row() {
            return String.format(Locale.ROOT, "min %.2f, median %.2f, p95 %.2f, max %.2f, mean %.2f ms (n=%d)",
                    min, median, p95, max, mean, count);
        }
    }

    /**
     * The summary of {@code values}, percentiles by the nearest-rank method (p95 of 20 values is the 19th smallest).
     * An empty series gives zeros.
     */
    public static Summary summarize(double[] values) {
        if (values.length == 0) return new Summary(0, 0, 0, 0, 0, 0);
        double[] s = values.clone();
        Arrays.sort(s);
        double sum = 0;
        for (double v : s) sum += v;
        return new Summary(s[0], percentile(s, 50), percentile(s, 95), s[s.length - 1], sum / s.length, s.length);
    }

    /** The nearest-rank percentile {@code p} (0..100) of the sorted, non-empty {@code sorted}. */
    public static double percentile(double[] sorted, double p) {
        int rank = (int) Math.ceil(p / 100.0 * sorted.length);
        return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))];
    }

    /**
     * The tick times [ms] of the ticks after {@code lastTick} up to {@code tick}, read from the server's ring of the last
     * 100 tick durations [ns] (entry {@code t % 100} holds tick {@code t}). At most the last 100 ticks can be read.
     */
    public static double[] ticksSince(long[] ringNanos, int lastTick, int tick) {
        int n = Math.max(0, Math.min(ringNanos.length, tick - lastTick));
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            int t = tick - n + 1 + i;
            out[i] = ringNanos[Math.floorMod(t, ringNanos.length)] / 1.0e6;
        }
        return out;
    }

    /** What one more ship costs [ms per tick]: the difference of two runs' MSPT over their difference in ships. */
    public static double perShip(double msptMany, int shipsMany, double msptFew, int shipsFew) {
        if (shipsMany == shipsFew) return 0;
        return (msptMany - msptFew) / (shipsMany - shipsFew);
    }

    // ------------------------------------------------------------------ stack samples

    /** One frame: class and method, as {@code StackTraceElement} has them. */
    public record Frame(String className, String method) {

        public String id() {
            return className + "." + method;
        }

        public boolean mod() {
            return className.startsWith(MOD_PACKAGE) && !className.startsWith(PERF_PACKAGE);
        }

        public boolean sable() {
            return className.startsWith(SABLE_PACKAGE);
        }

        /** The mod package below the root, two levels deep ({@code ship.hull}, {@code crew.lean}); null outside the mod. */
        public String modArea() {
            if (!mod()) return null;
            String rest = className.substring(MOD_PACKAGE.length());
            String[] parts = rest.split("\\.");
            if (parts.length <= 1) return "(root)";
            if (parts.length == 2) return parts[0];
            return parts[0] + "." + parts[1];
        }
    }

    /**
     * Stack samples of the server thread, top frame first, and what they say. A sample is <em>busy</em> when the thread
     * runs a tick ({@code tickServer} on the stack, outside the wait for the next tick); only busy samples count toward
     * the shares, which are fractions of the busy samples.
     */
    public static final class Samples {
        private int total;
        private int busy;
        private final Map<String, Integer> inclusive = new HashMap<>();
        private final Map<String, Integer> self = new HashMap<>();
        private final Map<String, Integer> areas = new HashMap<>();
        private int mod;
        private int sable;

        /** Adds one sample, {@code frames[0]} the top of the stack. */
        public void add(List<Frame> frames) {
            total++;
            if (!PerfStats.busy(frames)) return;
            busy++;
            Set<String> seen = new HashSet<>();
            Set<String> seenAreas = new HashSet<>();
            boolean anyMod = false;
            boolean anySable = false;
            Frame deepest = null;
            for (Frame f : frames) {
                if (f.sable()) anySable = true;
                if (!f.mod()) continue;
                anyMod = true;
                if (deepest == null) deepest = f;
                if (seen.add(f.id())) inclusive.merge(f.id(), 1, Integer::sum);
                if (seenAreas.add(f.modArea())) areas.merge(f.modArea(), 1, Integer::sum);
            }
            if (deepest != null) self.merge(deepest.id(), 1, Integer::sum);
            if (anyMod) mod++;
            if (anySable) sable++;
        }

        public int total() {
            return total;
        }

        public int busy() {
            return busy;
        }

        /** Share of busy samples with a mod frame anywhere on the stack. */
        public double modShare() {
            return busy == 0 ? 0 : (double) mod / busy;
        }

        /** Share of busy samples with a Sable frame anywhere on the stack. */
        public double sableShare() {
            return busy == 0 ? 0 : (double) sable / busy;
        }

        /** The {@code n} mod methods on the most busy stacks (inclusive of what they call), largest first. */
        public List<Entry> topInclusive(int n) {
            return top(inclusive, n);
        }

        /** The {@code n} mod methods that are most often the deepest mod frame (their own work plus vanilla/JDK calls). */
        public List<Entry> topSelf(int n) {
            return top(self, n);
        }

        /** The {@code n} mod package areas ({@code ship.hull}, …) on the most busy stacks. */
        public List<Entry> topAreas(int n) {
            return top(areas, n);
        }

        private List<Entry> top(Map<String, Integer> counts, int n) {
            List<Entry> out = new ArrayList<>();
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                out.add(new Entry(e.getKey(), e.getValue(), busy == 0 ? 0 : (double) e.getValue() / busy));
            }
            out.sort(Comparator.comparingInt(Entry::samples).reversed().thenComparing(Entry::name));
            return out.size() > n ? out.subList(0, n) : out;
        }
    }

    /** A named count and its share of the busy samples. */
    public record Entry(String name, int samples, double share) {

        public String row() {
            return String.format(Locale.ROOT, "%5.1f%%  %6d  %s", share * 100.0, samples, name);
        }
    }

    /**
     * Whether a stack is tick work: {@code tickServer} is on it and the thread is not in the wait for the next tick
     * ({@code waitUntilNextTick}; tasks run there do not count toward vanilla's tick time either).
     */
    public static boolean busy(List<Frame> frames) {
        boolean tick = false;
        for (Frame f : frames) {
            if (f.method().equals("waitUntilNextTick")) return false;
            if (f.method().equals("tickServer")) tick = true;
        }
        return tick;
    }

    /** Frames from {@code class.method} strings, for tests. */
    public static List<Frame> frames(Collection<String> ids) {
        List<Frame> out = new ArrayList<>();
        for (String id : ids) {
            int dot = id.lastIndexOf('.');
            out.add(new Frame(id.substring(0, dot), id.substring(dot + 1)));
        }
        return out;
    }

    /** Merges entries of the same name (two profiler windows), averaging their shares weighted equally. */
    public static List<Entry> mergeWindows(List<List<Entry>> windows, int n) {
        Map<String, double[]> sums = new LinkedHashMap<>();
        for (List<Entry> w : windows) {
            for (Entry e : w) {
                double[] s = sums.computeIfAbsent(e.name(), k -> new double[2]);
                s[0] += e.samples();
                s[1] += e.share();
            }
        }
        List<Entry> out = new ArrayList<>();
        int k = Math.max(1, windows.size());
        sums.forEach((name, s) -> out.add(new Entry(name, (int) s[0], s[1] / k)));
        out.sort(Comparator.comparingDouble(Entry::share).reversed().thenComparing(Entry::name));
        return out.size() > n ? out.subList(0, n) : out;
    }
}
