package com.richardsenger.piratesnships.crew.lean;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Pure choice of a spot at the rail for an idle crew member (RAIL5, docs/design.md §4.8 "Railing extras"). A spot is a
 * railing block's own cell on the deck with one of its railed edges as the outboard side: the leaner stands in it with
 * its front at the rail. A candidate is usable when its floor carries the leaner, there is headroom above it, the deck
 * cell inboard of it can be walked to (the pathfinder never enters a railing's cell, so the last step is straight in),
 * and no station block (helm, winch, gun, …) stands within one block of it. Ports and gates never offer a spot (they are
 * openings, not railings). Leaners keep a cell between each other.
 */
public final class RailSpots {

    /** A spot: the railing's cell (plot frame on a ship) and the side the leaner faces. */
    public record Spot(BlockPos cell, Direction outboard) {
    }

    /** What the level says about one railed edge of one railing block (see the class comment). */
    public record Candidate(BlockPos cell, Direction outboard, boolean floor, boolean headroom, boolean approach,
                            boolean nearStation) {
        public boolean usable() {
            return floor && headroom && approach && !nearStation;
        }

        public Spot spot() {
            return new Spot(cell, outboard);
        }
    }

    private RailSpots() {
    }

    /** Whether two leaners' cells keep at least one cell between them (Chebyshev distance two or more). */
    public static boolean apart(BlockPos a, BlockPos b) {
        int d = Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
        return d >= 2;
    }

    /**
     * The nearest usable candidate to {@code from} (the crew member's feet, same frame) within {@code radius} blocks
     * (horizontal, at most two blocks up or down) that keeps a cell from every spot in {@code taken}; null when there is
     * none. Ties go to the earlier candidate.
     */
    public static @Nullable Spot choose(List<Candidate> candidates, Vec3 from, double radius, Collection<BlockPos> taken) {
        Spot best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (Candidate c : candidates) {
            if (!c.usable()) {
                continue;
            }
            Vec3 at = leanPoint(c.spot());
            double d = Math.hypot(at.x - from.x, at.z - from.z);
            if (d > radius + 1.0e-9 || Math.abs(c.cell().getY() - from.y) > 2.0) {
                continue;
            }
            boolean free = true;
            for (BlockPos t : taken) {
                if (!apart(t, c.cell())) {
                    free = false;
                    break;
                }
            }
            if (free && d < bestD - 1.0e-9) {
                best = c.spot();
                bestD = d;
            }
        }
        return best;
    }

    /** Where the leaner's feet go: the cell's floor, {@link LeanRules#LEAN_OFFSET} outboard of its centre. */
    public static Vec3 leanPoint(Spot s) {
        return new Vec3(s.cell().getX() + 0.5 + s.outboard().getStepX() * LeanRules.LEAN_OFFSET, s.cell().getY(),
                s.cell().getZ() + 0.5 + s.outboard().getStepZ() * LeanRules.LEAN_OFFSET);
    }

    /** The deck cell inboard of the spot, where the path ends before the last step in. */
    public static BlockPos approachCell(Spot s) {
        return s.cell().relative(s.outboard().getOpposite());
    }
}
