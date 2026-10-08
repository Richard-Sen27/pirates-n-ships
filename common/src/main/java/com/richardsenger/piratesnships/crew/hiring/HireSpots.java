package com.richardsenger.piratesnships.crew.hiring;

import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where a recruit goes aboard (CRW1): a free spot on the ship's deck. A local stand-in for WS3b's {@code DeckSpots},
 * which has not landed: every plot block with a sturdy top and two free blocks above is a spot; the deck's height is the
 * most common height of each column's topmost spot, and only spots within one block of it count (so a mast top, a cabin
 * roof or the hold are skipped); spots with someone standing on them are taken. Of the rest, the one nearest a world
 * point.
 */
final class HireSpots {

    private HireSpots() {
    }

    /** A free deck spot of {@code ship} nearest {@code near}, as the world position to stand at. */
    static Optional<Vec3> nearest(ServerLevel level, ShipBody ship, Vec3 near) {
        BlockPos[] b = ship.plotBounds();
        List<BlockPos> spots = new ArrayList<>();
        List<Integer> topHeights = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = b[0].getX(); x <= b[1].getX(); x++) {
            for (int z = b[0].getZ(); z <= b[1].getZ(); z++) {
                boolean top = true;
                for (int y = b[1].getY(); y >= b[0].getY(); y--) {
                    p.set(x, y, z);
                    if (standable(level, p)) {
                        spots.add(p.immutable());
                        if (top) topHeights.add(y);
                        top = false;
                    }
                }
            }
        }
        if (spots.isEmpty()) return Optional.empty();
        // the deck is the most common topmost height; spots under a yard or an awning at that height count too
        int deck = deckHeight(topHeights);
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos top : spots) {
            if (Math.abs(top.getY() - deck) > 1) continue;
            Vec3 world = ship.toWorld(Vec3.atBottomCenterOf(top.above()));
            if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(world, world).inflate(0.4, 0.0, 0.4).expandTowards(0, 1.8, 0),
                    LivingEntity::isAlive).isEmpty()) {
                continue;
            }
            double d = world.distanceToSqr(near);
            if (d < bestDist) {
                bestDist = d;
                best = world;
            }
        }
        return Optional.ofNullable(best);
    }

    /** A sturdy top with two blocks free of collision and fluid above. */
    private static boolean standable(ServerLevel level, BlockPos pos) {
        BlockState floor = level.getBlockState(pos);
        if (floor.isAir() || !floor.isFaceSturdy(level, pos, Direction.UP)) return false;
        for (int i = 1; i <= 2; i++) {
            BlockPos up = pos.above(i);
            BlockState s = level.getBlockState(up);
            if (!s.getCollisionShape(level, up).isEmpty() || !s.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    /** The deck's height: the most common of the column heights, the lower one on a tie. Pure. */
    static int deckHeight(List<Integer> heights) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int h : heights) counts.merge(h, 1, Integer::sum);
        int best = Integer.MIN_VALUE;
        int bestCount = -1;
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount || (e.getValue() == bestCount && e.getKey() < best)) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        return best;
    }
}
