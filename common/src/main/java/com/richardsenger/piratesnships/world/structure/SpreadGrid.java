package com.richardsenger.piratesnships.world.structure;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import org.jetbrains.annotations.Nullable;

/**
 * The arithmetic of a random-spread structure grid (WG5), without world access: the same chunk choice as vanilla's
 * {@code RandomSpreadStructurePlacement#getPotentialStructureChunk} for given numbers, and the rule that picks the
 * numbers of a {@link ConfiguredSpreadPlacement} (the server config when it is loaded, else the structure set's JSON).
 *
 * @param spacing    the side of one grid cell in chunks (at least 2)
 * @param separation the minimum gap in chunks between the starts of two neighbouring cells (1 to {@code spacing - 1})
 */
public record SpreadGrid(int spacing, int separation) {

    /** Clamps the numbers into what vanilla's random spread accepts: spacing at least 2, separation in [1, spacing). */
    public SpreadGrid {
        spacing = Math.max(2, spacing);
        separation = Math.max(1, Math.min(separation, spacing - 1));
    }

    /**
     * The grid a configured placement uses: the config's spacing and separation where a value is given (it is loaded
     * or overridden), the JSON's where it is {@code null}. A separation that is not below the spacing (e.g. spacing
     * lowered in the config while separation kept an old value) is clamped to {@code spacing - 1}.
     */
    public static SpreadGrid resolve(int jsonSpacing, int jsonSeparation, @Nullable Integer configSpacing,
                                     @Nullable Integer configSeparation) {
        return new SpreadGrid(configSpacing != null ? configSpacing : jsonSpacing,
                configSeparation != null ? configSeparation : jsonSeparation);
    }

    /**
     * The one chunk in the grid cell of chunk ({@code chunkX}, {@code chunkZ}) where the structure may start, as
     * vanilla computes it: the cell's corner plus two offsets in [0, spacing - separation) drawn from a random seeded
     * with the level seed, the cell coordinates and the salt.
     */
    public ChunkPos potentialChunk(long seed, int chunkX, int chunkZ, int salt, RandomSpreadType spreadType) {
        int cellX = Math.floorDiv(chunkX, spacing);
        int cellZ = Math.floorDiv(chunkZ, spacing);
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(seed, cellX, cellZ, salt);
        int range = spacing - separation;
        int dx = spreadType.evaluate(random, range);
        int dz = spreadType.evaluate(random, range);
        return new ChunkPos(cellX * spacing + dx, cellZ * spacing + dz);
    }

    /** Whether the structure may start in chunk ({@code chunkX}, {@code chunkZ}) (its cell's potential chunk). */
    public boolean isPlacementChunk(long seed, int chunkX, int chunkZ, int salt, RandomSpreadType spreadType) {
        ChunkPos pos = potentialChunk(seed, chunkX, chunkZ, salt, spreadType);
        return pos.x == chunkX && pos.z == chunkZ;
    }
}
