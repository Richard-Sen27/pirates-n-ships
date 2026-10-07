package com.richardsenger.piratesnships.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GameTest support for the survival tests: mock players that are in the level (ticked by it, so vanilla's freezing,
 * item use and our tick hooks all run for them), and a biome change of the test area that is undone when the test
 * ends.
 */
final class SurvivalTestSupport {

    private static volatile Field testInfoField;

    private SurvivalTestSupport() {
    }

    /** A mock player of {@code mode} at the test-relative position, added to the level and discarded when the test ends. */
    static Player playerInLevel(GameTestHelper helper, Vec3 relative, GameType mode) {
        Player player = helper.makeMockPlayer(mode);
        Vec3 at = helper.absoluteVec(relative);
        player.moveTo(at.x, at.y, at.z, 0f, 0f);
        helper.getLevel().addFreshEntity(player);
        discardAtEnd(helper, player);
        return player;
    }

    /** Discards {@code entity} when the test passes, fails or is rerun (the framework never clears players). */
    static void discardAtEnd(GameTestHelper helper, Entity entity) {
        onEnd(helper, entity::discard);
    }

    /**
     * Sets the biome of the test area to {@code biome} (as {@code /fillbiome} does) and restores the old biomes when the
     * test ends. Changed are the biome cells (4×4×4 quarts) whose centres lie inside the test's bounds horizontally,
     * and 8 blocks below and above it, so neighbouring test areas keep theirs. Vanilla's {@code getBiome} blends
     * neighbouring cells, so only positions at least a cell (4 blocks) inside the area are reliably in the new biome:
     * use the 24×24 template and keep the subject near its middle.
     */
    static void setBiome(GameTestHelper helper, ResourceKey<Biome> biome) {
        ServerLevel level = helper.getLevel();
        Holder<Biome> holder = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biome);
        AABB b = helper.getBounds();
        int qx0 = quartWithCentreFrom(b.minX), qx1 = quartWithCentreBefore(b.maxX);
        int qz0 = quartWithCentreFrom(b.minZ), qz1 = quartWithCentreBefore(b.maxZ);
        int qy0 = quartWithCentreFrom(b.minY - 8), qy1 = quartWithCentreBefore(b.maxY + 8);
        List<ChunkAccess> chunks = new ArrayList<>();
        Map<Long, Holder<Biome>> original = new HashMap<>();
        for (int cx = SectionPos.blockToSectionCoord(QuartPos.toBlock(qx0)); cx <= SectionPos.blockToSectionCoord(QuartPos.toBlock(qx1)); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(QuartPos.toBlock(qz0)); cz <= SectionPos.blockToSectionCoord(QuartPos.toBlock(qz1)); cz++) {
                chunks.add(level.getChunk(cx, cz, ChunkStatus.FULL, true));
            }
        }
        for (ChunkAccess chunk : chunks) {
            for (int qx = qx0; qx <= qx1; qx++) {
                for (int qz = qz0; qz <= qz1; qz++) {
                    if (!inChunk(chunk, qx, qz)) continue;
                    for (int qy = qy0; qy <= qy1; qy++) original.put(BlockPos.asLong(qx, qy, qz), chunk.getNoiseBiome(qx, qy, qz));
                }
            }
        }
        fill(level, chunks, (qx, qy, qz) -> original.containsKey(BlockPos.asLong(qx, qy, qz)) ? holder : null);
        onEnd(helper, () -> fill(level, chunks, (qx, qy, qz) -> original.get(BlockPos.asLong(qx, qy, qz))));
    }

    @FunctionalInterface
    private interface CellBiome {
        /** The biome for a cell, or {@code null} to keep the current one. */
        Holder<Biome> at(int qx, int qy, int qz);
    }

    private static void fill(ServerLevel level, List<ChunkAccess> chunks, CellBiome biomes) {
        for (ChunkAccess chunk : chunks) {
            chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> {
                Holder<Biome> b = biomes.at(qx, qy, qz);
                return b != null ? b : chunk.getNoiseBiome(qx, qy, qz);
            }, level.getChunkSource().randomState().sampler());
            chunk.setUnsaved(true);
        }
        level.getChunkSource().chunkMap.resendBiomesForChunks(chunks);
    }

    private static boolean inChunk(ChunkAccess chunk, int qx, int qz) {
        int bx = QuartPos.toBlock(qx), bz = QuartPos.toBlock(qz);
        return SectionPos.blockToSectionCoord(bx) == chunk.getPos().x && SectionPos.blockToSectionCoord(bz) == chunk.getPos().z;
    }

    /** The first cell whose centre (4q + 2) is at or after {@code block}. */
    private static int quartWithCentreFrom(double block) {
        return (int) Math.ceil((block - 2) / 4.0);
    }

    /** The last cell whose centre (4q + 2) is before {@code block}. */
    private static int quartWithCentreBefore(double block) {
        return (int) Math.ceil((block - 2) / 4.0) - 1;
    }

    private static void onEnd(GameTestHelper helper, Runnable action) {
        testInfo(helper).addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo testInfo) { }
            @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { action.run(); }
            @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { action.run(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { action.run(); }
        });
    }

    /** {@code GameTestHelper#testInfo} is private and has no getter in 1.21.1 (same accessor as {@code ConfigOverrides}). */
    private static GameTestInfo testInfo(GameTestHelper helper) {
        try {
            Field f = testInfoField;
            if (f == null) {
                for (Field candidate : GameTestHelper.class.getDeclaredFields()) {
                    if (candidate.getType() == GameTestInfo.class) {
                        candidate.setAccessible(true);
                        testInfoField = f = candidate;
                        break;
                    }
                }
            }
            if (f == null) throw new IllegalStateException("GameTestHelper has no GameTestInfo field");
            return (GameTestInfo) f.get(helper);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
