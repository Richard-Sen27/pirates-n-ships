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

    // ---- glancing hits (Q2) ----------------------------------------------------------------------------------------

    /**
     * How squarely a ball flying along {@code direction} hits a face with the normal {@code normal}: {@code |cos θ|},
     * θ the angle between the flight and the normal; 1 straight on, 0 along the face. 1 when either vector is zero
     * (no angle to judge, so the hit counts as straight).
     */
    public static double squareness(Vec3 direction, Vec3 normal) {
        double l = direction.length() * normal.length();
        if (l < 1.0e-12) return 1.0;
        return Math.min(1.0, Math.abs(direction.dot(normal)) / l);
    }

    /** Whether a hit with the squareness {@code cos} grazes the face: its angle to the normal is over {@code bounceDegrees}. */
    public static boolean bounces(double cos, double bounceDegrees) {
        if (bounceDegrees >= 90.0) return false;
        return cos < Math.cos(Math.toRadians(Math.max(0.0, bounceDegrees)));
    }

    /**
     * Blocks a hit with the squareness {@code cos} breaks out of {@code limit}: {@code max(1, round(limit × cos))}, and
     * 0 when the limit is 0 (no block damage stays no block damage).
     */
    public static int glancingBlocks(int limit, double cos) {
        if (limit <= 0) return 0;
        return (int) Math.max(1, Math.min(limit, Math.round(limit * Math.max(0.0, Math.min(1.0, cos)))));
    }

    /**
     * The velocity of a ball bouncing off a face with the normal {@code normal}: the part along the face is kept, the
     * part towards the face is turned around and scaled by {@code factor}.
     */
    public static Vec3 deflect(Vec3 velocity, Vec3 normal, double factor) {
        if (normal.lengthSqr() < 1.0e-12) return velocity;
        Vec3 n = normal.normalize();
        Vec3 along = n.scale(velocity.dot(n));
        return velocity.subtract(along).subtract(along.scale(factor));
    }
}
