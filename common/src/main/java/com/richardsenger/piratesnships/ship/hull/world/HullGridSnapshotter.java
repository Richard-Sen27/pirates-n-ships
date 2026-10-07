package com.richardsenger.piratesnships.ship.hull.world;

import com.richardsenger.piratesnships.ship.hull.CellKind;
import com.richardsenger.piratesnships.ship.hull.HullGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Snapshots a block region into an immutable {@link HullGrid} (main thread; the analysis can then run elsewhere).
 * The grid's origin is the region's minimum corner, so grid cell (0, 0, 0) is block {@code min}. Solid and opening
 * cells whose block does not fill its cube are marked partial, with their uncovered faces
 * ({@link HullBlockClassifier#uncoveredFaces}).
 */
public final class HullGridSnapshotter {

    private HullGridSnapshotter() {
    }

    /** Snapshots the inclusive box {@code min..max}. */
    public static HullGrid snapshot(BlockGetter level, BlockPos min, BlockPos max) {
        return snapshot(level, min, max, Set.of());
    }

    /**
     * Snapshots the inclusive box {@code min..max}. Positions in {@code breaches} (hull blocks destroyed by damage,
     * tracked by the ship) that are not watertight now become permanently open openings, so water flows in at a
     * limited rate instead of the cells turning into outside air at once.
     */
    public static HullGrid snapshot(BlockGetter level, BlockPos min, BlockPos max, Set<BlockPos> breaches) {
        return snapshot(level, min, max, breaches, state -> false, pos -> { });
    }

    /**
     * {@link #snapshot(BlockGetter, BlockPos, BlockPos, Set)} that also reports, in the same pass, the positions of the
     * blocks matching {@code mark} (e.g. the ship's bilge pumps) to {@code marked}.
     */
    public static HullGrid snapshot(BlockGetter level, BlockPos min, BlockPos max, Set<BlockPos> breaches,
                                    Predicate<BlockState> mark, Consumer<BlockPos> marked) {
        int sx = max.getX() - min.getX() + 1, sy = max.getY() - min.getY() + 1, sz = max.getZ() - min.getZ() + 1;
        HullGrid.Builder b = HullGrid.builder(sx, sy, sz).origin(min.getX(), min.getY(), min.getZ());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    pos.set(min.getX() + x, min.getY() + y, min.getZ() + z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && mark.test(state)) {
                        marked.accept(pos.immutable());
                    }
                    CellKind kind = HullBlockClassifier.classify(state, level, pos);
                    if (kind == CellKind.AIR && !breaches.isEmpty() && breaches.contains(pos)) {
                        b.breach(x, y, z);
                    } else {
                        b.set(x, y, z, kind, kind == CellKind.OPENING && HullBlockClassifier.isOpen(state));
                        if (kind != CellKind.AIR) {
                            int faces = HullBlockClassifier.uncoveredFaces(state, level, pos, kind);
                            if (faces != 0) {
                                b.partial(x, y, z, faces);
                            }
                        }
                    }
                }
            }
        }
        return b.build();
    }
}
