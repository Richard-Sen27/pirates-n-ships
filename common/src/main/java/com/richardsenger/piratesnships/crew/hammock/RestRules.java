package com.richardsenger.piratesnships.crew.hammock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * When crew turn in and which hammock each takes (HM1, docs/design.md §7.1). Pure.
 */
public final class RestRules {

    /** Ticks per day. */
    public static final long DAY = 24000L;
    /** Time of day from which vanilla lets players sleep in clear weather: the crew turns in. */
    public static final long NIGHTFALL = 12542L;
    /** Time of day at which vanilla's sleeping window ends: the crew gets up. */
    public static final long DAWN = 23460L;

    /** A free hammock: key {@code key} (its foot) at a world position. */
    public record Bed<K>(K key, double x, double y, double z) {
        public Bed {
            Objects.requireNonNull(key, "key");
        }
    }

    /** A free crew member on the ship at a world position. */
    public record Sleeper(UUID id, double x, double y, double z) {
        public Sleeper {
            Objects.requireNonNull(id, "id");
        }
    }

    private RestRules() {
    }

    /**
     * Whether it is night at day time {@code dayTime} (any day): vanilla's sleeping window. A fixed clock, not
     * {@code Level#isNight}, which also turns true under thunderclouds and would wake and bed the crew with the weather.
     */
    public static boolean isNight(long dayTime) {
        long t = Math.floorMod(dayTime, DAY);
        return t >= NIGHTFALL && t < DAWN;
    }

    /**
     * Pairs sleepers with beds, closest pair first, one bed each; ties keep list order (beds first, then sleepers), so
     * the result is deterministic. Sleepers without a bed are missing from the result.
     */
    public static <K> Map<UUID, K> assign(List<Bed<K>> beds, List<Sleeper> sleepers) {
        record Pair(int bed, int sleeper, double d2) { }
        List<Pair> pairs = new ArrayList<>();
        for (int b = 0; b < beds.size(); b++) {
            for (int s = 0; s < sleepers.size(); s++) {
                Bed<K> bed = beds.get(b);
                Sleeper sl = sleepers.get(s);
                double dx = bed.x() - sl.x(), dy = bed.y() - sl.y(), dz = bed.z() - sl.z();
                pairs.add(new Pair(b, s, dx * dx + dy * dy + dz * dz));
            }
        }
        pairs.sort((a, c) -> {
            int r = Double.compare(a.d2(), c.d2());
            if (r != 0) return r;
            r = Integer.compare(a.bed(), c.bed());
            return r != 0 ? r : Integer.compare(a.sleeper(), c.sleeper());
        });
        Set<Integer> usedBeds = new HashSet<>();
        Map<UUID, K> out = new LinkedHashMap<>();
        for (Pair p : pairs) {
            UUID id = sleepers.get(p.sleeper()).id();
            if (usedBeds.contains(p.bed()) || out.containsKey(id)) continue;
            usedBeds.add(p.bed());
            out.put(id, beds.get(p.bed()).key());
        }
        return out;
    }
}
