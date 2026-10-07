package com.richardsenger.piratesnships.combat.cannon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Which blocks one cannonball hit reaches (docs/design.md §4.6). Pure: the world is asked through a predicate. */
public final class CannonImpact {

    /** Step of the march along the flight path, in blocks. Small enough not to skip a block corner. */
    private static final double STEP = 0.1;

    private CannonImpact() {
    }

    /**
     * Up to {@code limit} blocks the ball reaches: the hit block first, then the next solid blocks
     * ({@code solid}) along {@code direction} from the hit point, within {@code limit + 1} blocks of it, each once.
     * All positions and the direction are in one frame (the plot frame for a ship). Empty for a limit below 1.
     */
    public static List<BlockPos> blocksAlong(BlockPos hitBlock, Vec3 hitPoint, Vec3 direction, int limit, Predicate<BlockPos> solid) {
        List<BlockPos> out = new ArrayList<>();
        if (limit < 1) return out;
        out.add(hitBlock);
        if (limit == 1 || direction.lengthSqr() < 1.0e-12) return out;
        Vec3 dir = direction.normalize();
        double reach = limit + 1;
        for (double d = STEP; d <= reach && out.size() < limit; d += STEP) {
            BlockPos p = BlockPos.containing(hitPoint.add(dir.scale(d)));
            if (!out.contains(p) && solid.test(p)) {
                out.add(p);
            }
        }
        return out;
    }
}
