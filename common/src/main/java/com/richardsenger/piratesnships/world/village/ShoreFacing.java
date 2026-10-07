package com.richardsenger.piratesnships.world.village;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

import java.util.List;
import java.util.Optional;

/**
 * Which way the sea lies from a village candidate (design.md §10.1, WG1). Pure: the caller supplies a {@link Probe}
 * that answers "is this column sea water at sea level" relative to the candidate, backed by the chunk generator's
 * noise during world generation and by real blocks in GameTests.
 *
 * <p>Rule: for each horizontal direction, walk 1..{@code probeBlocks} columns out and note the first water column and
 * how many of the columns are water. A direction qualifies when its first water column is at most
 * {@code maxDistance} away. Among qualifying directions the one with the most water wins (open sea beats a pond on
 * the other side), then the nearest shore, then the order north, east, south, west. The candidate's own column must be
 * land; a candidate standing in water has no shore to build a quay on.
 */
public final class ShoreFacing {

    /** Horizontal directions in tie-break order. */
    public static final List<Direction> ORDER = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    /** Answers whether the column at (dx, dz) from the candidate is sea water at sea level. */
    @FunctionalInterface
    public interface Probe {
        boolean water(int dx, int dz);
    }

    /**
     * The chosen shore: the sea lies toward {@code sea}; the first water column is {@code distance} blocks away, so the
     * last land column (where the quay edge goes) is {@code distance - 1} away.
     */
    public record Shore(Direction sea, int distance, int waterColumns) {
    }

    private ShoreFacing() {
    }

    public static Optional<Shore> choose(Probe probe, int probeBlocks, int maxDistance) {
        if (probe.water(0, 0)) return Optional.empty();
        Shore best = null;
        for (Direction d : ORDER) {
            int first = -1;
            int count = 0;
            for (int i = 1; i <= probeBlocks; i++) {
                if (probe.water(d.getStepX() * i, d.getStepZ() * i)) {
                    if (first < 0) first = i;
                    count++;
                }
            }
            if (first < 0 || first > maxDistance) continue;
            Shore s = new Shore(d, first, count);
            if (best == null || s.waterColumns() > best.waterColumns()
                    || s.waterColumns() == best.waterColumns() && s.distance() < best.distance()) {
                best = s;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The rotation that turns a piece's north (−z) side toward {@code sea}: the dock head faces −z with its pier
     * connector there, so this rotation points the pier at the water.
     */
    public static Rotation rotationFacing(Direction sea) {
        return switch (sea) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
}
