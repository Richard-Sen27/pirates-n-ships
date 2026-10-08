package com.richardsenger.piratesnships.world;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.desk.HarborDeskService;
import com.richardsenger.piratesnships.trade.desk.HarborDesks;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.port.Berth;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.PortService;
import com.richardsenger.piratesnships.world.structure.PortStructure;
import com.richardsenger.piratesnships.world.village.VillageKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The seafarer village on a hand-built shore (WG1): a flat sand beach with the sea on one side inside a 48×48 test
 * area (water rows y 3..6, so the sea surface is relative y 6). The tests run the structure's own planning at a chosen
 * site with the terrain read from the real blocks, then place the pieces through {@link StructureStart#placeInChunk},
 * which also runs {@code afterPlace} (port registry and desk binding). Depth 1: dock head, pier, one street and its
 * street end (the terminator once the depth runs out); 20 + 11 + 7 + 3 blocks across the shore.
 */
public final class WorldGameTests {

    static final int SIZE = 48;
    static final int SEA_Y = 6;
    /** Canonical layout (sea to the north): water for z below this row, land from it on. */
    static final int SHORE_Z = 22;
    /** Canonical site: 9 blocks inland from the first water row. */
    static final int SITE_X = 24;
    static final int SITE_Z = 30;

    private WorldGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(WorldGameTests.class);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48)
    public static void villageFacesTheSeaToTheNorth(GameTestHelper helper) {
        villageOnShore(helper, Direction.NORTH);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48)
    public static void villageTurnsToTheSeaToTheEast(GameTestHelper helper) {
        villageOnShore(helper, Direction.EAST);
    }

    /** With the structure toggled off, a perfect shore gives no generation point. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, batch = "pirates_n_ships_config_world_village")
    public static void disabledVillagesHaveNoGenerationPoint(GameTestHelper helper) {
        ConfigOverrides.during(helper, WorldConfig.SEAFARER_VILLAGE_ENABLED, false);
        buildShore(helper, Direction.NORTH);
        ServerLevel level = helper.getLevel();
        BlockPos site = site(helper, Direction.NORTH);
        Structure.GenerationContext context = context(level, site);
        helper.assertTrue(structure(level).plan(context, site, seaSurface(helper), terrain(helper), 1).isEmpty(), "plan while disabled");
        helper.assertTrue(structure(level).findValidGenerationPoint(context).isEmpty(), "generation point while disabled");
        helper.succeed();
    }

    /** Every element of the village pools names a committed template (a missing one would load as an empty template). */
    @ModGameTest
    public static void everyPoolEntryResolvesToATemplate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        int elements = 0;
        for (ResourceKey<StructureTemplatePool> key : List.of(VillageKeys.START, VillageKeys.STREETS,
                VillageKeys.BUILDINGS, VillageKeys.PIER, VillageKeys.TERMINATORS)) {
            StructureTemplatePool pool = pools.get(key);
            helper.assertTrue(pool != null, "pool " + key.location() + " is loaded");
            for (StructurePoolElement element : pool.getShuffledTemplates(RandomSource.create(0L))) {
                var size = element.getSize(level.getStructureManager(), Rotation.NONE);
                helper.assertTrue(size.getX() > 0 && size.getY() > 0 && size.getZ() > 0, key.location() + ": " + element + " has no template");
                elements++;
            }
        }
        // dock head, street, house ×3, tavern, shipwright (weights expand), pier, street end
        helper.assertValueEqual(elements, 1 + 1 + 5 + 1 + 1, "pool elements");
        helper.assertValueEqual(pools.get(VillageKeys.STREETS).getFallback().unwrapKey(), Optional.of(VillageKeys.TERMINATORS),
                "streets fall back to the terminators");
        helper.succeed();
    }

    private static void villageOnShore(GameTestHelper helper, Direction sea) {
        buildShore(helper, sea);
        ServerLevel level = helper.getLevel();
        BlockPos site = site(helper, sea);
        int seaY = seaSurface(helper);
        PortStructure structure = structure(level);
        Structure.GenerationContext context = context(level, site);
        Optional<Structure.GenerationStub> stub = structure.plan(context, site, seaY, terrain(helper), 1);
        helper.assertTrue(stub.isPresent(), "the shore gives a generation point");
        StructureStart start = new StructureStart(structure, context.chunkPos(), 0, stub.get().getPiecesBuilder().build());
        place(level, start);

        List<StructurePiece> pieces = start.getPieces();
        helper.assertValueEqual(pieces.size(), 4, "dock head, pier, street and street end");
        BoundingBox dock = pieces.get(0).getBoundingBox();
        helper.assertValueEqual(dock.minY(), seaY + 1, "dock head paving one above the sea surface");
        // The quay's sea edge is the shore row; the dock head lies on the land side of it
        helper.assertValueEqual(seaEdge(dock, sea), canonicalToWorld(helper, sea, SITE_X, SHORE_Z), "quay edge on the last land row");

        StructurePiece pier = pieces.stream().filter(p -> p != pieces.get(0) && isOverWater(helper, sea, p.getBoundingBox()))
                .findFirst().orElse(null);
        helper.assertTrue(pier != null, "a piece (the pier) lies over the water on the " + sea + " side");
        helper.assertValueEqual(pier.getBoundingBox().minY() + 5, seaY + 1, "pier deck one above the sea surface");

        Port port = PortRegistry.get(level.getServer()).index().byId(PortService.villageId(PortService.centreOf(new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(pieces))))
                .orElse(null);
        helper.assertTrue(port != null, "the port is registered");
        helper.assertValueEqual(port.kind(), PortKind.SEAFARER_VILLAGE, "port kind");
        helper.assertValueEqual(port.berths().size(), 2, "two berths");
        for (Berth berth : port.berths()) {
            helper.assertValueEqual(berth.bow(), sea, "bow points out to sea");
            helper.assertValueEqual(berth.pos().getY(), seaY, "berth at the sea surface");
            helper.assertTrue(pier.getBoundingBox().isInside(berth.pos()), "berth beside the pier");
            helper.assertTrue(level.getBlockState(berth.pos()).is(Blocks.WATER), "berth " + berth.pos().toShortString() + " is water");
            helper.assertTrue(!level.getBlockState(berth.pos().above()).isFaceSturdy(level, berth.pos().above(), Direction.UP),
                    "berth " + berth.pos().toShortString() + " is open above");
        }
        helper.assertTrue(TradeService.market(level.getServer(), port.id()).isPresent(), "the port's market is open");

        // The dock head's desk is bound to the port by generation
        BlockPos desk = BlockPos.betweenClosedStream(dock).filter(p -> level.getBlockState(p).is(HarborDesks.HARBOR_DESK.get()))
                .map(BlockPos::immutable).findFirst().orElse(null);
        helper.assertTrue(desk != null, "the dock head has a harbor desk");
        helper.assertValueEqual(HarborDeskService.boundPort(level, desk), Optional.of(port.id()), "generated desk bound to the port");
        // PRT1a: the harbor master stands behind it
        com.richardsenger.piratesnships.mob.harbor.HarborMasterGameTests.assertPlacedAtDesk(helper, level, start.getBoundingBox(), desk, port.id());

        // A desk placed later inside the port's box binds through the locator (installed on server start; reinstalled
        // here because another module's GameTest swaps the locator)
        PortService.installDeskLocator();
        BlockPos later = BlockPos.betweenClosedStream(dock.minX(), dock.minY() + 1, dock.minZ(), dock.maxX(), dock.minY() + 1, dock.maxZ())
                .filter(p -> level.getBlockState(p).isAir()).map(BlockPos::immutable).findFirst().orElseThrow();
        level.setBlockAndUpdate(later, HarborDesks.HARBOR_DESK.get().defaultBlockState());
        helper.assertValueEqual(HarborDeskService.autoBind(level, later), Optional.of(port.id()), "placed desk finds the port");
        helper.assertValueEqual(HarborDeskService.boundPort(level, later), Optional.of(port.id()), "placed desk bound");
        level.setBlockAndUpdate(later, Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    // --- Shore ------------------------------------------------------------------------------------------------

    /** Maps the canonical layout (sea to the north) to the test area turned so the sea lies toward {@code sea}. */
    static BlockPos canonical(Direction sea, int x, int y, int z) {
        return switch (sea) {
            case EAST -> new BlockPos(SIZE - 1 - z, y, x);
            case SOUTH -> new BlockPos(SIZE - 1 - x, y, SIZE - 1 - z);
            case WEST -> new BlockPos(z, y, SIZE - 1 - x);
            default -> new BlockPos(x, y, z);
        };
    }

    /** The world coordinate across the shore (z for north/south, x for east/west) of canonical row {@code z}. */
    static int canonicalToWorld(GameTestHelper helper, Direction sea, int x, int z) {
        BlockPos p = helper.absolutePos(canonical(sea, x, 0, z));
        return sea.getAxis() == Direction.Axis.Z ? p.getZ() : p.getX();
    }

    static int seaEdge(BoundingBox box, Direction sea) {
        return switch (sea) {
            case NORTH -> box.minZ();
            case SOUTH -> box.maxZ();
            case WEST -> box.minX();
            default -> box.maxX();
        };
    }

    static boolean isOverWater(GameTestHelper helper, Direction sea, BoundingBox box) {
        int shore = canonicalToWorld(helper, sea, SITE_X, SHORE_Z);
        return switch (sea) {
            case NORTH -> box.maxZ() < shore;
            case SOUTH -> box.minZ() > shore;
            case WEST -> box.maxX() < shore;
            default -> box.minX() > shore;
        };
    }

    static void buildShore(GameTestHelper helper, Direction sea) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                boolean water = z < SHORE_Z;
                for (int y = 1; y <= 15; y++) {
                    BlockState state;
                    if (water) state = y <= 2 ? Blocks.STONE.defaultBlockState() : y <= SEA_Y ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    else state = y <= SEA_Y ? Blocks.SAND.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    helper.setBlock(canonical(sea, x, y, z), state);
                }
            }
        }
    }

    static BlockPos site(GameTestHelper helper, Direction sea) {
        return helper.absolutePos(canonical(sea, SITE_X, SEA_Y, SITE_Z));
    }

    static int seaSurface(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(0, SEA_Y, 0)).getY();
    }

    /** First free height (fluids ignored) of the test area's columns; columns outside count as high land. */
    static PortStructure.Terrain terrain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(BlockPos.ZERO);
        BlockPos b = helper.absolutePos(new BlockPos(SIZE - 1, 15, SIZE - 1));
        int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
        int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());
        int top = b.getY();
        int bottom = a.getY();
        return (x, z) -> {
            if (x < minX || x > maxX || z < minZ || z > maxZ) return top + 100;
            for (int y = top; y >= bottom; y--) {
                BlockState s = level.getBlockState(new BlockPos(x, y, z));
                if (!s.isAir() && s.getFluidState().isEmpty()) return y + 1;
            }
            return bottom;
        };
    }

    // --- Structure --------------------------------------------------------------------------------------------

    private static PortStructure structure(ServerLevel level) {
        Structure s = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(VillageKeys.SEAFARER_VILLAGE);
        if (!(s instanceof PortStructure village)) throw new IllegalStateException("seafarer_village is not loaded: " + s);
        return village;
    }

    static Structure.GenerationContext context(ServerLevel level, BlockPos site) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        return new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), new ChunkPos(site), level, b -> true);
    }

    /** Places every chunk of {@code start}, as {@code /place structure} does. */
    static void place(ServerLevel level, StructureStart start) {
        BoundingBox box = start.getBoundingBox();
        ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ()));
        ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()));
        ChunkPos.rangeClosed(min, max).forEach(chunk -> start.placeInChunk(level, level.structureManager(),
                level.getChunkSource().getGenerator(), level.getRandom(),
                new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                        chunk.getMaxBlockX(), level.getMaxBuildHeight(), chunk.getMaxBlockZ()), chunk));
    }
}
