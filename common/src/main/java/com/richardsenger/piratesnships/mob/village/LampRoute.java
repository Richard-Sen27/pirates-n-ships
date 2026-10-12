package com.richardsenger.piratesnships.mob.village;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * The lamplighter's round (LIFE1), pure: the village's lamp posts in walking order. Vanilla's lanterns have no lit
 * state, so the round is a walk with a lantern in hand from post to post, a pause at each with the {@code work} pose
 * (trimming the wick), at dusk and again at dawn ({@link VillageRoutine}).
 */
public final class LampRoute {

    private LampRoute() {
    }

    /**
     * Up to {@code max} of {@code lamps} in walking order from {@code start}: the nearest first, then always the nearest
     * of the rest (a greedy round, short enough for a dozen posts). Ties keep the input order.
     */
    public static List<BlockPos> order(BlockPos start, List<BlockPos> lamps, int max) {
        List<BlockPos> left = new ArrayList<>(lamps);
        List<BlockPos> out = new ArrayList<>();
        BlockPos at = start;
        while (!left.isEmpty() && out.size() < max) {
            int best = 0;
            for (int i = 1; i < left.size(); i++) {
                if (left.get(i).distSqr(at) < left.get(best).distSqr(at)) best = i;
            }
            at = left.remove(best);
            out.add(at);
        }
        return out;
    }
}
