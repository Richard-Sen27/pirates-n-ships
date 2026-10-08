package com.richardsenger.piratesnships.world;

import com.richardsenger.piratesnships.mob.MobFaction;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.world.outpost.OutpostKeys;
import com.richardsenger.piratesnships.world.structure.PortStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * The navy outpost of {@link NavyOutpostGameTests} for other modules' GameTests (MOB2's squads): the hand-built shore of
 * {@link WorldGameTests} on {@code EMPTY_48}, the outpost planned at depth 1 (fort gate, quay, one wall each way ending
 * in a wall tower, one building) and placed with its garrison, as world generation places it.
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
        Structure s = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(OutpostKeys.NAVY_OUTPOST);
        if (!(s instanceof PortStructure structure)) throw new IllegalStateException("navy_outpost is not loaded: " + s);
        Structure.GenerationContext context = WorldGameTests.context(level, site);
        Structure.GenerationStub stub = structure.plan(context, site, WorldGameTests.seaSurface(helper), WorldGameTests.terrain(helper), 1)
                .orElseThrow(() -> new IllegalStateException("the shore gives no generation point"));
        StructureStart start = new StructureStart(structure, context.chunkPos(), 0, stub.getPiecesBuilder().build());
        WorldGameTests.place(level, start);
        PiecesContainer pieces = new PiecesContainer(start.getPieces());
        return new Placed(start, pieces, pieces.calculateBoundingBox());
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
