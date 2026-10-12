package com.richardsenger.piratesnships.world;

import com.richardsenger.piratesnships.mob.MobFaction;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.world.outpost.GarrisonPosts;
import com.richardsenger.piratesnships.world.outpost.OutpostKeys;
import com.richardsenger.piratesnships.world.outpost.OutpostPieces;
import com.richardsenger.piratesnships.world.structure.PortStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * The navy outpost of {@link NavyOutpostGameTests} for other modules' GameTests (MOB2's squads): the hand-built shore of
 * {@link WorldGameTests} on {@code EMPTY_48}, the outpost planned at depth 1 (fort gate, quay, one piece of the wall
 * trace each way ending in a tower) and placed with its garrison, as world generation places it. STR4: the court (the
 * lanes from the land gate and what hangs from them) is left out ({@link #withoutTheCourt}): the 48 block arena has no
 * room for it, and it holds no posts at depth 1; {@code world/structure/OutpostLayoutGameTests} places whole outposts.
 */
public final class OutpostFixture {

    /** A placed outpost. */
    public record Placed(StructureStart start, PiecesContainer pieces, BoundingBox box) {
    }

    private OutpostFixture() {
    }

    /** Builds the shore with the sea to {@code sea} and places the outpost on it (garrison included). */
    public static Placed place(GameTestHelper helper, Direction sea) {
        WorldGameTests.buildShore(helper, sea);
        openSky(helper);
        ServerLevel level = helper.getLevel();
        BlockPos site = WorldGameTests.site(helper, sea);
        PortStructure structure = structure(level);
        List<StructurePiece> planned = plan(helper, sea, 1);
        if (planned.isEmpty()) throw new IllegalStateException("the shore gives no generation point");
        StructureStart start = new StructureStart(structure, new ChunkPos(site), 0, new PiecesContainer(withoutTheCourt(planned)));
        WorldGameTests.place(level, start);
        PiecesContainer pieces = new PiecesContainer(start.getPieces());
        return new Placed(start, pieces, pieces.calculateBoundingBox());
    }

    /** The level seed every fixture outpost is planned with (STR4: the layout must not depend on the test run). */
    public static final long FIXTURE_SEED = 20_261_010L;
    /** Where the fixture arena's north-west corner is planned: a fixed chunk-aligned site far from any test. */
    static final int PLAN_X = 8192;
    static final int PLAN_Z = 8192;

    /**
     * The outpost on the hand-built shore with the sea to {@code sea}, planned at {@code depth} deterministically
     * (STR4): planned with {@link #FIXTURE_SEED} as if the arena lay at ({@value #PLAN_X}, {@value #PLAN_Z}), the
     * terrain read from the real arena, then moved onto it. {@link PortStructure#plan} draws its jigsaw random from
     * the seed and the site's chunk, so planning at the real site gave each run its own wall pieces, wherever the
     * framework put the arena. Empty when the shore gives no generation point.
     */
    public static List<StructurePiece> plan(GameTestHelper helper, Direction sea, int depth) {
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(BlockPos.ZERO);
        BlockPos b = helper.absolutePos(new BlockPos(WorldGameTests.SIZE - 1, 0, WorldGameTests.SIZE - 1));
        int dx = Math.min(a.getX(), b.getX()) - PLAN_X;
        int dz = Math.min(a.getZ(), b.getZ()) - PLAN_Z;
        BlockPos site = WorldGameTests.site(helper, sea).offset(-dx, 0, -dz);
        PortStructure.Terrain arena = WorldGameTests.terrain(helper);
        PortStructure.Terrain terrain = (x, z) -> arena.firstFree(x + dx, z + dz);
        // STR8: the lanes' levels are read from the generator, so it stands at the shore's sand
        var generator = com.richardsenger.piratesnships.world.structure.StructureLayoutGameTests.beachGenerator(level,
                WorldGameTests.seaSurface(helper) + 1);
        Structure.GenerationContext context = new Structure.GenerationContext(level.registryAccess(), generator,
                generator.getBiomeSource(), level.getChunkSource().randomState(), level.getStructureManager(), FIXTURE_SEED,
                new ChunkPos(site), level, biome -> true);
        var stub = structure(level).plan(context, site, WorldGameTests.seaSurface(helper), terrain, depth);
        if (stub.isEmpty()) return List.of();
        List<StructurePiece> pieces = new java.util.ArrayList<>(stub.get().getPiecesBuilder().build().pieces());
        pieces.forEach(p -> p.move(dx, 0, dz));
        return pieces;
    }

    static PortStructure structure(ServerLevel level) {
        Structure s = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(OutpostKeys.NAVY_OUTPOST);
        if (!(s instanceof PortStructure structure)) throw new IllegalStateException("navy_outpost is not loaded: " + s);
        return structure;
    }

    /** The piece names of a planned outpost, in the jigsaw's order. */
    public static List<String> names(List<StructurePiece> pieces) {
        return pieces.stream().map(p -> p instanceof PoolElementStructurePiece e ? GarrisonPosts.pieceName(e.getElement().toString()) : "")
                .toList();
    }

    /** The fort gate, the quay and the wall trace with its towers: the planned pieces without the court (STR4). */
    public static List<StructurePiece> withoutTheCourt(List<StructurePiece> pieces) {
        return pieces.stream().filter(p -> {
            String name = p instanceof PoolElementStructurePiece e ? GarrisonPosts.pieceName(e.getElement().toString()) : "";
            return name.equals(GarrisonPosts.FORT_GATE) || name.equals("quay") || OutpostPieces.isWall(name) || OutpostPieces.isTower(name);
        }).toList();
    }

    /** The living navy mobs in and around {@code box}. */
    public static List<SeafarerMob> navy(ServerLevel level, BoundingBox box) {
        return level.getEntitiesOfClass(SeafarerMob.class, AABB.of(box).inflate(1), m -> m.faction() == MobFaction.NAVY && m.isAlive());
    }

    /** Removes the framework's barrier ceiling: the towers reach above the 16-high template (see NavyOutpostGameTests). */
    private static void openSky(GameTestHelper helper) {
        for (int y = 16; y <= 18; y++) {
            for (int x = 0; x < WorldGameTests.SIZE; x++) {
                for (int z = 0; z < WorldGameTests.SIZE; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (helper.getBlockState(p).is(Blocks.BARRIER)) helper.setBlock(p, Blocks.AIR);
                }
            }
        }
    }
}
