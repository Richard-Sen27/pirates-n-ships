package com.richardsenger.piratesnships.ship.hull.runtime;

import com.richardsenger.piratesnships.ship.hull.CellKind;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;

/**
 * Breach bookkeeping of one ship (docs/design.md §4.5), pure. A breach is a plot position where the last hull analysis
 * had a watertight cell and the block was removed. The snapshotter turns breaches into permanently open openings, so
 * water enters at a rate instead of the room becoming outside at once. Placing a watertight block there again patches it.
 */
public final class BreachSet {

    private final LongOpenHashSet cells = new LongOpenHashSet();

    /**
     * Updates the set for a block change.
     *
     * @param analysedKind what the last analysis had at {@code pos}, or null if outside its grid
     * @param newKind      what the position is now
     * @return whether the set changed
     */
    public boolean onBlockChanged(BlockPos pos, CellKind analysedKind, CellKind newKind) {
        long key = pos.asLong();
        if (newKind == CellKind.SOLID) {
            return cells.remove(key);
        }
        if (analysedKind == CellKind.SOLID && newKind == CellKind.AIR) {
            return cells.add(key);
        }
        return false;
    }

    public boolean contains(BlockPos pos) {
        return cells.contains(pos.asLong());
    }

    public int size() {
        return cells.size();
    }

    public boolean isEmpty() {
        return cells.isEmpty();
    }

    /** Positions, for the snapshotter. */
    public Set<BlockPos> positions() {
        Set<BlockPos> out = new HashSet<>();
        cells.forEach(k -> out.add(BlockPos.of(k)));
        return out;
    }

    public long[] toArray() {
        long[] a = cells.toLongArray();
        java.util.Arrays.sort(a);
        return a;
    }

    public void load(long[] packed) {
        cells.clear();
        for (long k : packed) {
            cells.add(k);
        }
    }
}
