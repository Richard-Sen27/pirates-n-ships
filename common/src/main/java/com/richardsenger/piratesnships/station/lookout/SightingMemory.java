package com.richardsenger.piratesnships.station.lookout;

import java.util.HashMap;
import java.util.Map;

/**
 * What one ship's lookouts have seen (CN1): the tick each object (a ship, a monster, a stretch of coast, by key) was
 * last seen. An object is called when it is new: never seen, or not seen for more than {@code memoryTicks}. Every
 * sighting refreshes the memory, so an object that stays in sight is called once. Pure, not thread safe.
 */
public final class SightingMemory {

    private final Map<String, Long> lastSeen = new HashMap<>();

    /** Records a sighting of {@code key} at {@code now}; true when it is to be called (new or forgotten). */
    public boolean sight(String key, long now, long memoryTicks) {
        Long last = lastSeen.put(key, now);
        return last == null || now - last > memoryTicks;
    }

    /** Whether {@code key} is remembered at {@code now}. */
    public boolean remembers(String key, long now, long memoryTicks) {
        Long last = lastSeen.get(key);
        return last != null && now - last <= memoryTicks;
    }

    /** Drops everything not seen for more than {@code memoryTicks}. */
    public void forget(long now, long memoryTicks) {
        lastSeen.values().removeIf(t -> now - t > memoryTicks);
    }

    public int size() {
        return lastSeen.size();
    }
}
