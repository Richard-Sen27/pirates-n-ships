package com.richardsenger.piratesnships.world.wreck;

import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Optional;

/**
 * Pure placement rules of a wreck (design.md §10.1, WK1), without world access.
 * <ul>
 *     <li><b>Height.</b> The floor height is the first free block of the {@code OCEAN_FLOOR_WG} heightmap (fluids
 *     ignored); the piece's y 0, its seabed row, goes {@code depthBelowFloor} (1) below it, so it replaces the floor's
 *     top block.</li>
 *     <li><b>Water.</b> The water above the seabed row is the number of blocks from the row above it up to the sea
 *     surface (the topmost water block, {@code getSeaLevel() - 1}). A piece fits if that is at least its
 *     {@code water_above}.</li>
 *     <li><b>Choice.</b> Weighted among the pieces that fit; none fits: no wreck.</li>
 * </ul>
 */
public final class WreckPlan {

    private WreckPlan() {
    }

    /** The y of the piece's seabed row (its y 0) for a column whose first free height is {@code floorHeight}. */
    public static int seabedY(int floorHeight, int depthBelowFloor) {
        return floorHeight - depthBelowFloor;
    }

    /** Blocks of water above the seabed row at {@code seabedY}, up to and including the sea surface. */
    public static int waterAbove(int seaSurface, int seabedY) {
        return seaSurface - seabedY;
    }

    /** The pieces whose water cover fits {@code waterAbove}, in their order. */
    public static List<WreckPieceEntry> fitting(List<WreckPieceEntry> pieces, int waterAbove) {
        return pieces.stream().filter(p -> p.waterAbove() <= waterAbove).toList();
    }

    /** A piece picked by weight among those that fit, or empty if none does. Draws one int from {@code random} if any fits. */
    public static Optional<WreckPieceEntry> choose(List<WreckPieceEntry> pieces, int waterAbove, RandomSource random) {
        List<WreckPieceEntry> fitting = fitting(pieces, waterAbove);
        if (fitting.isEmpty()) return Optional.empty();
        int total = fitting.stream().mapToInt(WreckPieceEntry::weight).sum();
        int roll = random.nextInt(total);
        for (WreckPieceEntry piece : fitting) {
            roll -= piece.weight();
            if (roll < 0) return Optional.of(piece);
        }
        throw new IllegalStateException("weighted roll ran past the pieces");
    }
}
