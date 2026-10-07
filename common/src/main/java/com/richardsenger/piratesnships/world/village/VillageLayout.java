package com.richardsenger.piratesnships.world.village;

import com.richardsenger.piratesnships.world.structure.ShoreAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;

/**
 * Where and how the dock head goes (WG1), pure arithmetic shared by world generation and the GameTests.
 *
 * <p>The dock head ({@code village/dock_head}, 11×8×11) faces −z: its sea edge is row z 0, its pier connector sits at
 * x {@link #DOCK_PIER_X} on that row. Rotated by {@link ShoreFacing#rotationFacing}, the sea edge faces the water.
 * {@link #startOrigin} puts the template origin so that the sea edge's centre lands on the last land column of the
 * shore.
 */
public final class VillageLayout {

    /** The dock head's pier connector column (local x) on its sea edge row (local z 0). */
    public static final int DOCK_PIER_X = 5;

    private VillageLayout() {
    }

    /**
     * The template origin (y unchanged) for the dock head rotated by {@code rotation} so that its local
     * ({@link #DOCK_PIER_X}, y, 0) lands on {@code seaEdge}. Templates rotate about their origin (pivot 0).
     */
    public static BlockPos startOrigin(BlockPos seaEdge, Rotation rotation) {
        return ShoreAnchor.VILLAGE.startOrigin(seaEdge, rotation);
    }

    /** The last land column toward {@code sea}: {@code distance - 1} blocks from {@code candidate}. */
    public static BlockPos seaEdge(BlockPos candidate, Direction sea, int distance) {
        return candidate.relative(sea, distance - 1);
    }

    /**
     * {@code JigsawPlacement.addPieces} takes the start piece's rotation from the first {@code nextInt(4)} of the
     * context's random and offers no parameter for it. This returns a large-feature random for {@code chunk}, from
     * the first seed at or after {@code seed} whose first draw is {@code wanted}, so the rotation is chosen and the
     * rest of the layout stays deterministic per world seed and chunk. About four tries on average.
     */
    public static WorldgenRandom randomForRotation(long seed, ChunkPos chunk, Rotation wanted) {
        for (long salt = 0; salt < 1024; salt++) {
            if (Rotation.getRandom(random(seed + salt, chunk)) == wanted) return random(seed + salt, chunk);
        }
        throw new IllegalStateException("No seed gives rotation " + wanted);
    }

    private static WorldgenRandom random(long seed, ChunkPos chunk) {
        WorldgenRandom r = new WorldgenRandom(new LegacyRandomSource(0L));
        r.setLargeFeatureSeed(seed, chunk.x, chunk.z);
        return r;
    }
}
