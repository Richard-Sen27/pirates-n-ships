package com.richardsenger.piratesnships.world;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.desk.HarborDeskService;
import com.richardsenger.piratesnships.trade.desk.HarborDesks;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.island.IslandData;
import com.richardsenger.piratesnships.world.island.IslandKeys;
import com.richardsenger.piratesnships.world.island.TreasureChests;
import com.richardsenger.piratesnships.world.port.Berth;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.PortService;
import com.richardsenger.piratesnships.world.port.TreasureSite;
import com.richardsenger.piratesnships.world.structure.PortStructure;
import com.richardsenger.piratesnships.world.structure.ShoreAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The pirate island on a hand-built shore (WG2), on the same 48×48 beach as {@link WorldGameTests} (sand, sea to the
 * north, sea surface at relative y 6). Depth 1: the camp, its jetty, two paths with their ends and one hut on the
 * camp's south side. The hut is drawn from the huts pool, so the test tries world seeds until the layout puts the
 * treasure spot there (deterministic per seed; one in six).
 */
public final class PirateIslandGameTests {

    private static final int MAX_SEED_TRIES = 200;

    private PirateIslandGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(PirateIslandGameTests.class);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48)
    public static void islandCampFacesTheSeaWithJettyTreasureAndFence(GameTestHelper helper) {
        Direction sea = Direction.NORTH;
        WorldGameTests.buildShore(helper, sea);
        ServerLevel level = helper.getLevel();
        BlockPos site = WorldGameTests.site(helper, sea);
        int seaY = WorldGameTests.seaSurface(helper);
        PortStructure structure = structure(level);
        helper.assertValueEqual(structure.portKind(), PortKind.PIRATE_ISLAND, "port kind of the structure");
        helper.assertValueEqual(structure.shoreAnchor(), ShoreAnchor.PIRATE_CAMP, "shore anchor");

        Structure.GenerationContext context = null;
        List<StructurePiece> pieces = null;
        for (int salt = 0; salt < MAX_SEED_TRIES && pieces == null; salt++) {
            Structure.GenerationContext c = context(level, site, level.getSeed() + salt);
            Optional<Structure.GenerationStub> stub = structure.plan(c, site, seaY, WorldGameTests.terrain(helper), 1);
            helper.assertTrue(stub.isPresent(), "the shore gives a generation point");
            List<StructurePiece> p = stub.get().getPiecesBuilder().build().pieces();
            if (p.stream().anyMatch(piece -> isPiece(piece, "treasure_spot"))) {
                context = c;
                pieces = p;
            }
        }
        helper.assertTrue(pieces != null, "a layout with the treasure spot on the camp within " + MAX_SEED_TRIES + " seeds");
        StructureStart start = new StructureStart(structure, context.chunkPos(), 0, new PiecesContainer(pieces));
        WorldGameTests.place(level, start);

        // camp: sand one above the sea surface, beach edge on the last land row, centred on the site's column
        BoundingBox camp = pieces.get(0).getBoundingBox();
        helper.assertTrue(isPiece(pieces.get(0), "camp_start"), "the start piece is the camp");
        helper.assertValueEqual(camp.minY(), seaY + 1, "camp sand one above the sea surface");
        helper.assertValueEqual(WorldGameTests.seaEdge(camp, sea),
                WorldGameTests.canonicalToWorld(helper, sea, WorldGameTests.SITE_X, WorldGameTests.SHORE_Z), "beach edge on the last land row");
        helper.assertValueEqual(camp.getCenter().getX(), site.getX(), "camp centred on the site");

        // jetty: over the water, deck one above the sea surface
        StructurePiece jetty = pieces.stream().filter(p -> isPiece(p, "jetty")).findFirst().orElse(null);
        helper.assertTrue(jetty != null, "the camp has its jetty");
        helper.assertTrue(WorldGameTests.isOverWater(helper, sea, jetty.getBoundingBox()), "the jetty lies over the water");
        helper.assertValueEqual(jetty.getBoundingBox().minY() + 5, seaY + 1, "jetty deck one above the sea surface");

        Port port = PortRegistry.get(level.getServer()).index()
                .byId(PortService.portId(PortKind.PIRATE_ISLAND, PortService.centreOf(new PiecesContainer(pieces)))).orElse(null);
        helper.assertTrue(port != null, "the port is registered");
        helper.assertTrue(port.id().getPath().startsWith("pirate_island_"), "port id " + port.id());
        helper.assertValueEqual(port.kind(), PortKind.PIRATE_ISLAND, "port kind");
        helper.assertValueEqual(port.berths().size(), 2, "two berths");
        for (Berth berth : port.berths()) {
            helper.assertValueEqual(berth.bow(), sea, "bow points out to sea");
            helper.assertValueEqual(berth.pos().getY(), seaY, "berth at the sea surface");
            helper.assertTrue(jetty.getBoundingBox().isInside(berth.pos()), "berth beside the jetty");
            helper.assertTrue(level.getBlockState(berth.pos()).is(Blocks.WATER), "berth " + berth.pos().toShortString() + " is water");
        }

        // treasure: a chest two below the camp's surface, under sand, with the loot table
        helper.assertValueEqual(port.treasures().size(), 1, "one treasure site");
        TreasureSite treasure = port.treasures().get(0);
        helper.assertTrue(!treasure.looted(), "a new treasure is not looted");
        StructurePiece spot = pieces.stream().filter(p -> isPiece(p, "treasure_spot")).findFirst().orElseThrow();
        helper.assertTrue(spot.getBoundingBox().isInside(treasure.pos()), "treasure inside the treasure spot");
        helper.assertValueEqual(treasure.pos().getY(), camp.minY() - 2, "treasure two below the surface");
        helper.assertTrue(level.getBlockState(treasure.pos()).is(Blocks.CHEST), "buried chest at " + treasure.pos().toShortString());
        helper.assertTrue(level.getBlockState(treasure.pos().above()).is(Blocks.SAND), "sand above the chest");
        helper.assertTrue(level.getBlockEntity(treasure.pos()) instanceof ChestBlockEntity chest
                && TreasureChests.LOOT_TABLE.equals(chest.getLootTable()), "the chest has the buried treasure loot table");

        // the fence: market open, desk bound by generation
        helper.assertTrue(TradeService.market(level.getServer(), port.id()).isPresent(), "the fence's market is open");
        BlockPos desk = BlockPos.betweenClosedStream(camp).filter(p -> level.getBlockState(p).is(HarborDesks.HARBOR_DESK.get()))
                .map(BlockPos::immutable).findFirst().orElse(null);
        helper.assertTrue(desk != null, "the camp has the fence's desk");
        helper.assertValueEqual(HarborDeskService.boundPort(level, desk), Optional.of(port.id()), "fence's desk bound to the port");
        helper.succeed();
    }

    /** With the island toggled off, a perfect shore gives no generation point. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, batch = "pirates_n_ships_config_world_pirate_island")
    public static void disabledIslandsHaveNoGenerationPoint(GameTestHelper helper) {
        ConfigOverrides.during(helper, WorldConfig.PIRATE_ISLAND_ENABLED, false);
        WorldGameTests.buildShore(helper, Direction.NORTH);
        ServerLevel level = helper.getLevel();
        BlockPos site = WorldGameTests.site(helper, Direction.NORTH);
        Structure.GenerationContext context = context(level, site, level.getSeed());
        helper.assertTrue(structure(level).plan(context, site, WorldGameTests.seaSurface(helper), WorldGameTests.terrain(helper), 1).isEmpty(),
                "plan while disabled");
        helper.assertTrue(structure(level).findValidGenerationPoint(context).isEmpty(), "generation point while disabled");
        helper.succeed();
    }

    /**
     * Pirates spawn inside the camp's pieces: the structure's {@code monster} overrides list them, their type is a
     * {@code monster} (the natural spawner never spawns {@code misc}), and the spawn rule accepts sand in daylight
     * below the camp cap and refuses at the cap.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_world_pirate_spawns")
    public static void piratesSpawnInTheCampOnSandUpToTheCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureSpawnOverride override = structure(level).spawnOverrides().get(MobCategory.MONSTER);
        helper.assertTrue(override != null, "monster spawn overrides");
        helper.assertValueEqual(override.boundingBox(), StructureSpawnOverride.BoundingBoxType.PIECE, "spawns inside the pieces");
        helper.assertValueEqual(override.spawns().unwrap().size(), 1, "one spawn entry");
        var entry = override.spawns().unwrap().get(0);
        helper.assertTrue(entry.type == MobContent.PIRATE.get(), "pirates spawn");
        helper.assertValueEqual(entry.getWeight().asInt(), IslandData.PIRATE_SPAWN_WEIGHT, "weight");
        helper.assertValueEqual(entry.minCount, 1, "group min");
        helper.assertValueEqual(entry.maxCount, 3, "group max");
        helper.assertValueEqual(MobContent.PIRATE.get().getCategory(), MobCategory.MONSTER, "pirate category");
        helper.assertTrue(SpawnPlacements.getPlacementType(MobContent.PIRATE.get()) == SpawnPlacementTypes.ON_GROUND, "placement type");

        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) helper.setBlock(x, 1, z, Blocks.SAND);
        }
        helper.setBlock(1, 2, 1, Blocks.WATER);
        BlockPos onSand = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos inWater = helper.absolutePos(new BlockPos(1, 2, 1));
        RandomSource random = level.getRandom();

        ConfigOverrides.during(helper, WorldConfig.PIRATE_ISLAND_MAX_PIRATES, 64);
        helper.assertTrue(SpawnPlacements.isSpawnPositionOk(MobContent.PIRATE.get(), level, onSand), "sand is a spawn position");
        helper.assertTrue(SpawnPlacements.checkSpawnRules(MobContent.PIRATE.get(), level, MobSpawnType.NATURAL, onSand, random),
                "spawn rule accepts sand below the cap");
        helper.assertTrue(!SpawnPlacements.isSpawnPositionOk(MobContent.PIRATE.get(), level, inWater), "no spawns in water");

        ConfigOverrides.during(helper, WorldConfig.PIRATE_ISLAND_MAX_PIRATES, 0);
        helper.assertTrue(!SpawnPlacements.checkSpawnRules(MobContent.PIRATE.get(), level, MobSpawnType.NATURAL, onSand, random),
                "no natural spawns at the cap");
        helper.assertTrue(SpawnPlacements.checkSpawnRules(MobContent.PIRATE.get(), level, MobSpawnType.SPAWN_EGG, onSand, random),
                "spawn eggs ignore the cap");
        helper.succeed();
    }

    /** Every element of the island pools names a committed template (a missing one would load as an empty template). */
    @ModGameTest
    public static void everyIslandPoolEntryResolvesToATemplate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        int elements = 0;
        for (ResourceKey<StructureTemplatePool> key : List.of(IslandKeys.START, IslandKeys.PATHS, IslandKeys.HUTS,
                IslandKeys.JETTY, IslandKeys.TERMINATORS)) {
            StructureTemplatePool pool = pools.get(key);
            helper.assertTrue(pool != null, "pool " + key.location() + " is loaded");
            for (StructurePoolElement element : pool.getShuffledTemplates(RandomSource.create(0L))) {
                var size = element.getSize(level.getStructureManager(), Rotation.NONE);
                helper.assertTrue(size.getX() > 0 && size.getY() > 0 && size.getZ() > 0, key.location() + ": " + element + " has no template");
                elements++;
            }
        }
        // camp, path, tent ×3, tavern hut, captain's hut, treasure spot (weights expand), jetty, path end
        helper.assertValueEqual(elements, 1 + 1 + 6 + 1 + 1, "pool elements");
        helper.assertValueEqual(pools.get(IslandKeys.PATHS).getFallback().unwrapKey(), Optional.of(IslandKeys.TERMINATORS),
                "paths fall back to the terminators");
        helper.succeed();
    }

    // --- helpers ----------------------------------------------------------------------------------------------

    private static boolean isPiece(StructurePiece piece, String name) {
        return piece instanceof PoolElementStructurePiece p && p.getElement().toString().contains("pirate_island/" + name + "]");
    }

    private static PortStructure structure(ServerLevel level) {
        Structure s = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(IslandKeys.PIRATE_ISLAND);
        if (!(s instanceof PortStructure island)) throw new IllegalStateException("pirate_island is not loaded: " + s);
        return island;
    }

    private static Structure.GenerationContext context(ServerLevel level, BlockPos site, long seed) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        return new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), seed, new ChunkPos(site), level, b -> true);
    }
}
