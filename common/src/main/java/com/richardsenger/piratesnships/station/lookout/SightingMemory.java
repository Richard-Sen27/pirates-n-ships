package com.richardsenger.piratesnships.station.lookout;

import java.util.HashMap;
import java.util.Map;

/**
 * What one ship's lookouts have seen (CN1, LKO2): per object (a ship, a monster, a stretch of coast, by key) the tick it
 * was last seen, and the tick and distance it was last cued at. An object is cued when it is new (never seen, or not
 * seen for more than {@code memoryTicks}), and again while it stays in sight once it came {@code recallBlocks} closer
 * than at its last cue or {@code recallTicks} passed since that cue (LKO2's repeat rule; 0 switches either off). Every
 * sighting refreshes the memory. Pure, not thread safe.
 */
public final class SightingMemory {

    private static final class Entry {
        long seen;
        long cued;
        int cuedDistance;
    }

    private final Map<String, Entry> entries = new HashMap<>();

    /** Records a sighting of {@code key} at {@code now} without a repeat rule; true when it is new or forgotten. */
    public boolean sight(String key, long now, long memoryTicks) {
        return sight(key, now, memoryTicks, 0, 0, 0);
    }

    /**
     * Records a sighting of {@code key} at {@code now}, {@code distance} blocks off; true when it is to be cued: new,
     * forgotten, {@code recallBlocks} (> 0) closer than at its last cue, or {@code recallTicks} (> 0) after it.
     */
    public boolean sight(String key, long now, long memoryTicks, int distance, int recallBlocks, long recallTicks) {
        Entry e = entries.get(key);
        boolean cue;
        if (e == null || now - e.seen > memoryTicks) {
            if (e == null) {
                e = new Entry();
                entries.put(key, e);
            }
            cue = true;
        } else {
            cue = (recallBlocks > 0 && e.cuedDistance - distance >= recallBlocks)
                    || (recallTicks > 0 && now - e.cued >= recallTicks);
        }
        e.seen = now;
        if (cue) {
            e.cued = now;
            e.cuedDistance = distance;
        }
        return cue;
    }

    /** Whether {@code key} is remembered at {@code now}. */
    public boolean remembers(String key, long now, long memoryTicks) {
        Entry e = entries.get(key);
        return e != null && now - e.seen <= memoryTicks;
    }

    /** Drops everything not seen for more than {@code memoryTicks}. */
    public void forget(long now, long memoryTicks) {
        entries.values().removeIf(e -> now - e.seen > memoryTicks);
    }

    public int size() {
        return entries.size();
    }
}
