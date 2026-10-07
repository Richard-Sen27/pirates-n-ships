package com.richardsenger.piratesnships.ship.hull.net;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The hull strip of the ship HUD (HUD1), pure: the compartments ordered bow to stern, capped at {@link #MAX_CELLS}.
 *
 * <p><b>Order:</b> by the centroid's position along the bow axis, bow first; compartments at the same position (decks
 * above each other) lowest first, then by id, so the order is stable.
 *
 * <p><b>Cap:</b> while there are more than {@link #MAX_CELLS} cells, the smallest one (by volume; on a tie the one
 * nearest the stern) is merged into its smaller neighbour in strip order (on a tie the one toward the bow). A merged
 * cell adds up volume, water and breaches, pumps when either part pumps, keeps the larger part's id and sits at the
 * volume-weighted mean position. Neighbours only, so the strip still reads like the ship.
 */
public final class CompartmentStrip {

    /** Most cells in the strip (and in a {@link ShipStatusPayload}). */
    public static final int MAX_CELLS = 16;

    /**
     * One compartment as the server sees it.
     *
     * @param forward  centroid along the bow axis (larger = nearer the bow) [blocks]
     * @param height   centroid height (only orders compartments at the same {@code forward})
     */
    public record Entry(int id, double forward, double height, int volume, double water, int breaches, boolean pumping) {

        ShipStatusPayload.Cell cell() {
            return new ShipStatusPayload.Cell(id, volume, (float) water, breaches, pumping);
        }
    }

    private static final Comparator<Entry> BOW_FIRST = Comparator.comparingDouble((Entry e) -> -e.forward())
            .thenComparingDouble(Entry::height).thenComparingInt(Entry::id);

    private CompartmentStrip() {
    }

    /** The entries ordered bow to stern and merged down to at most {@code max} cells. */
    public static List<Entry> order(List<Entry> entries, int max) {
        List<Entry> strip = new ArrayList<>(entries);
        strip.sort(BOW_FIRST);
        while (strip.size() > Math.max(1, max)) {
            int smallest = 0;
            for (int i = 1; i < strip.size(); i++) {
                if (strip.get(i).volume() <= strip.get(smallest).volume()) {
                    smallest = i;
                }
            }
            int other;
            if (smallest == 0) {
                other = 1;
            } else if (smallest == strip.size() - 1) {
                other = smallest - 1;
            } else {
                other = strip.get(smallest - 1).volume() <= strip.get(smallest + 1).volume() ? smallest - 1 : smallest + 1;
            }
            int lo = Math.min(smallest, other);
            Entry merged = merge(strip.get(lo), strip.get(lo + 1));
            strip.set(lo, merged);
            strip.remove(lo + 1);
        }
        return strip;
    }

    /** The payload cells of {@link #order(List, int)} with {@link #MAX_CELLS}. */
    public static List<ShipStatusPayload.Cell> cells(List<Entry> entries) {
        return order(entries, MAX_CELLS).stream().map(Entry::cell).toList();
    }

    static Entry merge(Entry a, Entry b) {
        int volume = a.volume() + b.volume();
        double wa = volume == 0 ? 0.5 : a.volume() / (double) volume;
        Entry big = b.volume() > a.volume() ? b : a;
        return new Entry(big.id(), a.forward() * wa + b.forward() * (1 - wa), a.height() * wa + b.height() * (1 - wa),
                volume, a.water() + b.water(), a.breaches() + b.breaches(), a.pumping() || b.pumping());
    }
}
