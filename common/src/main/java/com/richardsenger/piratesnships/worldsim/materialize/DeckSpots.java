package com.richardsenger.piratesnships.worldsim.materialize;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Where people stand on a ship's deck (WS3b): pure, in the ship's own (plot) coordinates. A spot is a floor block (a
 * block with a sturdy top, as the caller decides) between {@code minY} and {@code maxY} with {@value #HEADROOM} empty
 * cells above it; per column only the highest such floor counts, so the main deck wins over the hold below it and a
 * mast top above {@code maxY} never counts. The returned positions are the floor blocks (the feet stand on top).
 */
public final class DeckSpots {

    /** Empty cells needed above a floor. */
    public static final int HEADROOM = 2;
    /** How far above the waterline a deck may lie (the sloop's deck is 5 above, a cabin roof 8 or 9). */
    public static final int MAX_DECK_HEIGHT = 10;

    private DeckSpots() {
    }

    /**
     * Every spot of the block set {@code blocks} (the ship's blocks), sorted along z then x. {@code floor} says whether
     * a block of the set can be stood on.
     */
    public static List<BlockPos> candidates(Collection<BlockPos> blocks, Predicate<BlockPos> floor, int minY, int maxY) {
        Set<BlockPos> occupied = new HashSet<>();
        for (BlockPos b : blocks) occupied.add(b.immutable());
        Map<Long, BlockPos> best = new HashMap<>();
        for (BlockPos b : occupied) {
            if (b.getY() < minY || b.getY() > maxY || !floor.test(b)) continue;
            boolean clear = true;
            for (int dy = 1; dy <= HEADROOM; dy++) {
                if (occupied.contains(b.above(dy))) {
                    clear = false;
                    break;
                }
            }
            if (!clear) continue;
            long column = ((long) b.getX() << 32) ^ (b.getZ() & 0xFFFFFFFFL);
            BlockPos prev = best.get(column);
            if (prev == null || prev.getY() < b.getY()) best.put(column, b);
        }
        List<BlockPos> out = new ArrayList<>(best.values());
        out.sort(Comparator.comparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY));
        return out;
    }

    /**
     * {@code count} spots spread evenly over {@code candidates} (in their order); when there are fewer candidates than
     * people, spots are used again in turn. Empty when there are no candidates.
     */
    public static List<BlockPos> pick(List<BlockPos> candidates, int count) {
        List<BlockPos> out = new ArrayList<>();
        int n = candidates.size();
        if (n == 0 || count <= 0) return out;
        if (count >= n) {
            for (int i = 0; i < count; i++) out.add(candidates.get(i % n));
            return out;
        }
        for (int i = 0; i < count; i++) {
            int idx = (int) Math.floor((i + 0.5) * n / count);
            out.add(candidates.get(Math.min(n - 1, idx)));
        }
        return out;
    }
}
