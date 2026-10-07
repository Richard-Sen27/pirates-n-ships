package com.richardsenger.piratesnships.ship.assembly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Pure rules for rejoining a split-off piece to its ship with the Shipwright's Toolkit (RS2). No world access.
 *
 * <p>Checks, in this order, refused on the first failure: same origin, size of the absorbed piece, alignment, contact,
 * no overlap, nails.
 */
public final class RejoinRules {

    /** The first failed check, or {@link #OK}. */
    public enum Check { OK, DIFFERENT_SHIP, TOO_BIG, NOT_ALIGNED, ADD_PLANKS, TOO_FAR, OVERLAP, NO_NAILS }

    /**
     * How the snapped piece meets the keeper.
     *
     * @param distance smallest Manhattan distance between a snapped cell and a keeper cell, capped at 3
     *                 (0 = a snapped block lands on a keeper block, 1 = face to face, 2 = one block would bridge)
     * @param touching at least one snapped block is face-adjacent to a keeper block
     * @param overlap  at least one snapped block lands on an occupied keeper cell
     */
    public record Contact(int distance, boolean touching, boolean overlap) { }

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private RejoinRules() {
    }

    /**
     * The verdict, in the fixed order. {@code fit} and {@code contact} are only computed when the earlier checks pass.
     *
     * @param keeperOrigin   origin of the keeper's line ({@code ShipSplits.lineage(...).origin()})
     * @param absorbedOrigin origin of the absorbed piece's line
     * @param absorbedBlocks number of blocks of the absorbed piece
     * @param nailsHeld      nails the player holds ({@link Integer#MAX_VALUE} for a creative player)
     */
    public static Check check(@Nullable UUID keeperOrigin, @Nullable UUID absorbedOrigin, int absorbedBlocks, int maxPieceBlocks,
                              Supplier<RejoinTransform.Fit> fit, double maxAngle, double maxGap, Supplier<Contact> contact,
                              int nailsHeld, int nailsPerRejoin) {
        if (keeperOrigin == null || absorbedOrigin == null || !keeperOrigin.equals(absorbedOrigin)) {
            return Check.DIFFERENT_SHIP;
        }
        if (absorbedBlocks > maxPieceBlocks) {
            return Check.TOO_BIG;
        }
        if (!fit.get().aligned(maxAngle, maxGap)) {
            return Check.NOT_ALIGNED;
        }
        Contact c = contact.get();
        if (c.distance() >= 3) {
            return Check.TOO_FAR;
        }
        if (c.distance() == 2) {
            return Check.ADD_PLANKS;
        }
        if (c.overlap()) {
            return Check.OVERLAP;
        }
        if (!hasNails(nailsHeld, nailsPerRejoin)) {
            return Check.NO_NAILS;
        }
        return Check.OK;
    }

    /** True if {@code held} nails pay for one rejoin. */
    public static boolean hasNails(int held, int perRejoin) {
        return held >= Math.max(0, perRejoin);
    }

    /** How many nails to take: none from a creative player, else the cost. */
    public static int nailsToTake(int perRejoin, boolean creative) {
        return creative ? 0 : Math.max(0, perRejoin);
    }

    /** How the snapped cells meet the keeper's cells (both in keeper plot coordinates). */
    public static Contact contact(Set<BlockPos> keeper, Collection<BlockPos> snapped) {
        boolean touching = false;
        boolean overlap = false;
        boolean bridge = false;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (BlockPos s : snapped) {
            if (keeper.contains(s)) {
                overlap = true;
            }
            if (!touching) {
                for (int[] f : FACES) {
                    if (keeper.contains(m.set(s.getX() + f[0], s.getY() + f[1], s.getZ() + f[2]))) {
                        touching = true;
                        break;
                    }
                }
            }
            if (!touching && !bridge) {
                bridge = withinTwo(keeper, s, m);
            }
        }
        return new Contact(overlap ? 0 : touching ? 1 : bridge ? 2 : 3, touching, overlap);
    }

    /** A keeper cell at Manhattan distance exactly 2 (one placed block, straight or in the corner, would join them). */
    private static boolean withinTwo(Set<BlockPos> keeper, BlockPos s, BlockPos.MutableBlockPos m) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 2
                            && keeper.contains(m.set(s.getX() + dx, s.getY() + dy, s.getZ() + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * The keeper's seam: indices of its blocks within {@code radius} of a block of another piece (world centers), the
     * nearest first, at most {@code max}.
     */
    public static List<Integer> seam(List<Vec3> keeper, List<Vec3> others, double radius, int max) {
        List<double[]> hits = new ArrayList<>(); // {index, distance²}
        double r2 = radius * radius;
        for (int i = 0; i < keeper.size(); i++) {
            Vec3 k = keeper.get(i);
            double best = Double.MAX_VALUE;
            for (Vec3 o : others) {
                double d = k.distanceToSqr(o);
                if (d < best) {
                    best = d;
                }
            }
            if (best <= r2) {
                hits.add(new double[] {i, best});
            }
        }
        hits.sort(Comparator.comparingDouble(h -> h[1]));
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < hits.size() && out.size() < max; i++) {
            out.add((int) hits.get(i)[0]);
        }
        return out;
    }
}
