package com.richardsenger.piratesnships.worldsim.materialize;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure rule of WS3c "health carries back" (design.md §10.4): a voyage record keeps its ship's health as
 * {@code 1 − flooded fraction}; when the ship appears again its compartments are flooded back to that fraction, the
 * lowest compartments first (water settles in the bilge before it rises into the cabins). A record at full health
 * floods nothing.
 */
public final class HealthFlooding {

    /** Health at or above this counts as a dry hull (rounding in the saved fraction). */
    static final double FULL = 1.0 - 1e-6;

    /** One compartment: its id, the grid y of its lowest cells and its capacity in blocks of water. */
    public record Tank(int id, int bottom, double capacity) { }

    private HealthFlooding() {
    }

    /**
     * The water per compartment id for {@code health} (0..1, clamped): {@code (1 − health)} of the total capacity,
     * poured into the compartments by their lowest cells (ties by id), each filled up before the next one. Only
     * compartments that get water are in the map; empty for full health or no capacity.
     */
    public static Map<Integer, Double> volumes(List<Tank> tanks, double health) {
        Map<Integer, Double> out = new LinkedHashMap<>();
        double h = Math.max(0.0, Math.min(1.0, health));
        if (h >= FULL || tanks.isEmpty()) return out;
        double total = 0;
        for (Tank t : tanks) total += Math.max(0.0, t.capacity());
        double left = (1.0 - h) * total;
        List<Tank> order = new ArrayList<>(tanks);
        order.sort(Comparator.comparingInt(Tank::bottom).thenComparingInt(Tank::id));
        for (Tank t : order) {
            if (left <= 1e-9) break;
            double v = Math.min(Math.max(0.0, t.capacity()), left);
            if (v <= 0) continue;
            out.put(t.id(), v);
            left -= v;
        }
        return out;
    }
}
