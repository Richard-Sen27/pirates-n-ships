package com.richardsenger.piratesnships.world;

import com.richardsenger.piratesnships.combat.cannon.CannonBlock;
import com.richardsenger.piratesnships.combat.cannon.CannonContent;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.MobFaction;
import com.richardsenger.piratesnships.mob.entity.NavyOfficer;
import com.richardsenger.piratesnships.mob.entity.NavySoldier;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.desk.HarborDeskService;
import com.richardsenger.piratesnships.trade.desk.HarborDesks;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.outpost.Garrison;
import com.richardsenger.piratesnships.world.outpost.GarrisonPosts;
import com.richardsenger.piratesnships.world.outpost.OutpostData;
import com.richardsenger.piratesnships.world.outpost.OutpostKeys;
import com.richardsenger.piratesnships.world.outpost.OutpostPieces;
import com.richardsenger.piratesnships.world.port.Berth;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.PortService;
import com.richardsenger.piratesnships.world.structure.PortStructure;
import com.richardsenger.piratesnships.world.village.ShoreFacing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The navy outpost on the hand-built shore of {@link WorldGameTests} (WG3): 48×48 sand beach, sea surface at relative
 * y 6. Depth 1: the fort gate, its quay, one piece of the wall trace each way (STR4: any piece of the shore state, a
 * straight, a bastion, a sally port, a stair or an angle) ending in a tower, 18 blocks out over the water; the court
 * (lanes and what hangs from them) is left out, the arena has no room for it ({@link OutpostFixture#withoutTheCourt};
 * {@code world/structure/OutpostLayoutGameTests} plans and places whole outposts). The structure is placed through
 * {@link StructureStart#placeInChunk}, which runs {@code afterPlace}: port, desk binding and garrison.
 */
public final class NavyOutpostGameTests {

    private NavyOutpostGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(NavyOutpostGameTests.class);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48)
    public static void outpostFacesTheSeaToTheNorthWithItsGarrison(GameTestHelper helper) {
        outpostOnShore(helper, Direction.NORTH);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48)
    public static void outpostTurnsToTheSeaToTheEastWithItsGarrison(GameTestHelper helper) {
        outpostOnShore(helper, Direction.EAST);
    }

    /**
     * FIX3: the outpost as worldgen leaves it. On a proto chunk every entity block's position holds a {@code DUMMY}
     * block entity tag ({@code WorldGenRegion#setBlock}), which the level chunk promotes through {@code newBlockEntity}
     * and logs "Tried to load a block entity for block ... but failed" when that is null (both cannon halves and both
     * brig door halves stood behind the warning). Here the placed outpost's block entities are replaced by such tags and
     * promoted by {@code LevelChunk#getBlockEntity} as on a real load: every one must come back, the cannon rears too.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48)
    public static void outpostBlockEntitiesSurviveTheWorldgenPromotion(GameTestHelper helper) {
        WorldGameTests.buildShore(helper, Direction.NORTH);
        openSky(helper);
        ServerLevel level = helper.getLevel();
        BlockPos site = WorldGameTests.site(helper, Direction.NORTH);
        PortStructure structure = structure(level);
        List<StructurePiece> planned = OutpostFixture.plan(helper, Direction.NORTH, 1);
        helper.assertTrue(!planned.isEmpty(), "the shore gives a generation point");
        StructureStart start = new StructureStart(structure, new net.minecraft.world.level.ChunkPos(site), 0,
                new PiecesContainer(OutpostFixture.withoutTheCourt(planned)));
        BoundingBox box = new PiecesContainer(start.getPieces()).calculateBoundingBox();
        WorldGameTests.place(level, start);

        int promoted = 0;
        int cannonRears = 0;
        List<String> failed = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState state = level.getBlockState(p);
            if (!state.hasBlockEntity()) continue;
            BlockPos pos = p.immutable();
            LevelChunk chunk = level.getChunkAt(pos);
            chunk.removeBlockEntity(pos);
            CompoundTag dummy = new CompoundTag();
            dummy.putInt("x", pos.getX());
            dummy.putInt("y", pos.getY());
            dummy.putInt("z", pos.getZ());
            dummy.putString("id", "DUMMY");
            chunk.setBlockEntityNbt(dummy);
            BlockEntity be = chunk.getBlockEntity(pos);
            if (be == null) failed.add(state + " at " + pos.toShortString());
            promoted++;
            if (state.is(CannonContent.CANNON.get()) && !CannonBlock.isMaster(state)) cannonRears++;
        }
        helper.assertTrue(cannonRears >= 2, "the two wall pieces' cannons stand in the outpost (" + cannonRears + " rears)");
        helper.assertTrue(failed.isEmpty(), "block entities worldgen fails to load (" + failed.size() + " of " + promoted
                + "): " + failed);
        navy(level, box).forEach(SeafarerMob::discard);
        helper.succeed();
    }

    /** With the outpost toggled off, a perfect shore gives no generation point. */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, batch = "pirates_n_ships_config_world_navy_outpost")
    public static void disabledOutpostsHaveNoGenerationPoint(GameTestHelper helper) {
        ConfigOverrides.during(helper, WorldConfig.NAVY_OUTPOST_ENABLED, false);
        WorldGameTests.buildShore(helper, Direction.NORTH);
        ServerLevel level = helper.getLevel();
        BlockPos site = WorldGameTests.site(helper, Direction.NORTH);
        Structure.GenerationContext context = WorldGameTests.context(level, site);
        helper.assertTrue(structure(level).plan(context, site, WorldGameTests.seaSurface(helper), WorldGameTests.terrain(helper), 1).isEmpty(),
                "plan while disabled");
        helper.assertTrue(structure(level).findValidGenerationPoint(context).isEmpty(), "generation point while disabled");
        helper.succeed();
    }

    /** Every element of the outpost pools names a committed template (a missing one would load as an empty template). */
    @ModGameTest
    public static void everyOutpostPoolEntryResolvesToATemplate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        int elements = 0;
        for (ResourceKey<StructureTemplatePool> key : List.of(OutpostKeys.START, OutpostKeys.WALLS_GATE, OutpostKeys.WALLS, OutpostKeys.WALLS_FLANK_HEAD,
                OutpostKeys.WALLS_FLANK, OutpostKeys.BUILDINGS, OutpostKeys.QUAY, OutpostKeys.TERMINATORS, OutpostKeys.LANEHEADS,
                OutpostKeys.LANES, OutpostKeys.LANE_ENDS, OutpostKeys.DECORATIONS)) {
            StructureTemplatePool pool = pools.get(key);
            helper.assertTrue(pool != null, "pool " + key.location() + " is loaded");
            for (StructurePoolElement element : pool.getShuffledTemplates(RandomSource.create(0L))) {
                var size = element.getSize(level.getStructureManager(), Rotation.NONE);
                helper.assertTrue(size.getX() > 0 && size.getY() > 0 && size.getZ() > 0, key.location() + ": " + element + " has no template");
                elements++;
            }
        }
        // weights expand in the pools: fort gate and quay one each, the rest as OutpostData weighs them
        int weights = 2;
        for (var pool : List.of(OutpostData.WALLS_GATE, OutpostData.WALLS, OutpostData.WALLS_FLANK_HEAD, OutpostData.WALLS_FLANK, OutpostData.BUILDINGS,
                OutpostData.TERMINATORS, OutpostData.LANEHEADS, OutpostData.LANES, OutpostData.LANE_ENDS, OutpostData.DECORATIONS)) {
            weights += pool.values().stream().mapToInt(Integer::intValue).sum();
        }
        helper.assertValueEqual(elements, weights, "pool elements");
        for (ResourceKey<StructureTemplatePool> walls : List.of(OutpostKeys.WALLS_GATE, OutpostKeys.WALLS, OutpostKeys.WALLS_FLANK_HEAD,
                OutpostKeys.WALLS_FLANK)) {
            helper.assertValueEqual(pools.get(walls).getFallback().unwrapKey(), Optional.of(OutpostKeys.TERMINATORS),
                    walls.location() + " falls back to the towers");
        }
        helper.assertValueEqual(pools.get(OutpostKeys.LANES).getFallback().unwrapKey(), Optional.of(OutpostKeys.LANE_ENDS),
                "lanes end in a dead end");
        helper.assertValueEqual(MobContent.NAVY_SOLDIER.get().getCategory(), MobCategory.MISC, "navy soldiers never spawn naturally");
        helper.assertValueEqual(MobContent.NAVY_OFFICER.get().getCategory(), MobCategory.MISC, "navy officers never spawn naturally");
        helper.assertTrue(structure(level).spawnOverrides().isEmpty(), "the outpost has no spawn overrides");
        helper.succeed();
    }

    private static void outpostOnShore(GameTestHelper helper, Direction sea) {
        WorldGameTests.buildShore(helper, sea);
        openSky(helper);
        ServerLevel level = helper.getLevel();
        BlockPos site = WorldGameTests.site(helper, sea);
        int seaY = WorldGameTests.seaSurface(helper);
        PortStructure structure = structure(level);
        helper.assertValueEqual(structure.portKind(), PortKind.NAVY_OUTPOST, "port kind of the structure");
        helper.assertValueEqual(structure.shoreAnchor(), OutpostData.FORT_GATE, "shore anchor");
        helper.assertValueEqual(structure.maxDepth(), OutpostData.SIZE, "size");

        List<StructurePiece> planned = OutpostFixture.plan(helper, sea, 1);
        helper.assertTrue(!planned.isEmpty(), "the shore gives a generation point");
        // STR4: the fixture's layout is fixed (OutpostFixture.FIXTURE_SEED), wherever the framework puts the arena
        helper.assertValueEqual(OutpostFixture.names(OutpostFixture.withoutTheCourt(planned)), sea == Direction.NORTH
                ? List.of("fort_gate", "wall_bastion", "wall_stair", "quay", "battery", "battery")
                : List.of("fort_gate", "quay", "wall", "wall_sally_port", "round_tower", "round_tower"), "the fixture's pieces");
        StructureStart start = new StructureStart(structure, new net.minecraft.world.level.ChunkPos(site), 0,
                new PiecesContainer(OutpostFixture.withoutTheCourt(planned)));
        List<StructurePiece> pieces = start.getPieces();
        PiecesContainer container = new PiecesContainer(pieces);
        BoundingBox box = container.calculateBoundingBox();
        WorldGameTests.place(level, start);

        // the gate: paving one above the sea surface, its sea edge on the last land row, centred on the site
        StructurePiece gate = pieces.get(0);
        helper.assertTrue(isPiece(gate, "fort_gate"), "the start piece is the fort gate");
        BoundingBox gateBox = gate.getBoundingBox();
        helper.assertValueEqual(gateBox.minY(), seaY + 1, "gate paving one above the sea surface");
        helper.assertValueEqual(WorldGameTests.seaEdge(gateBox, sea),
                WorldGameTests.canonicalToWorld(helper, sea, WorldGameTests.SITE_X, WorldGameTests.SHORE_Z), "sea wall on the last land row");
        helper.assertValueEqual(((PoolElementStructurePiece) gate).getRotation(), ShoreFacing.rotationFacing(sea), "the gate's sea side faces the sea");
        long walls = pieces.stream().filter(p -> OutpostPieces.isWall(name(p))).count();
        long towers = pieces.stream().filter(p -> OutpostPieces.isTower(name(p))).count();
        helper.assertValueEqual(walls, 2L, "one piece of the wall trace each way");
        helper.assertValueEqual(towers, 2L, "each wall run ends in a tower");
        for (StructurePiece p : pieces) {
            if (!OutpostPieces.isWall(name(p)) && !OutpostPieces.isTower(name(p))) continue;
            helper.assertValueEqual(p.getBoundingBox().minY(), gateBox.minY() - (name(p).endsWith("bastion") ? 3 : 0),
                    name(p) + " is level with the gate");
        }

        // the quay: over the water, deck one above the sea surface, two berths in water
        StructurePiece quay = pieces.stream().filter(p -> isPiece(p, "quay")).findFirst().orElse(null);
        helper.assertTrue(quay != null, "the gate has its quay");
        helper.assertTrue(WorldGameTests.isOverWater(helper, sea, quay.getBoundingBox()), "the quay lies over the water");
        helper.assertValueEqual(quay.getBoundingBox().minY() + 5, seaY + 1, "quay deck one above the sea surface");

        Port port = PortRegistry.get(level.getServer()).index()
                .byId(PortService.portId(PortKind.NAVY_OUTPOST, PortService.centreOf(container))).orElse(null);
        helper.assertTrue(port != null, "the port is registered");
        helper.assertTrue(port.id().getPath().startsWith("navy_outpost_"), "port id " + port.id());
        helper.assertValueEqual(port.kind(), PortKind.NAVY_OUTPOST, "port kind");
        helper.assertValueEqual(port.berths().size(), 2, "two berths");
        for (Berth berth : port.berths()) {
            helper.assertValueEqual(berth.bow(), sea, "bow points out to sea");
            helper.assertValueEqual(berth.pos().getY(), seaY, "berth at the sea surface");
            helper.assertTrue(quay.getBoundingBox().isInside(berth.pos()), "berth beside the quay");
            helper.assertTrue(level.getBlockState(berth.pos()).is(Blocks.WATER), "berth " + berth.pos().toShortString() + " is water");
        }
        helper.assertTrue(TradeService.market(level.getServer(), port.id()).isPresent(), "the outpost's market is open");
        BlockPos desk = BlockPos.betweenClosedStream(gateBox).filter(p -> level.getBlockState(p).is(HarborDesks.HARBOR_DESK.get()))
                .map(BlockPos::immutable).findFirst().orElse(null);
        helper.assertTrue(desk != null, "the gate has the harbor master's desk");
        helper.assertValueEqual(HarborDeskService.boundPort(level, desk), Optional.of(port.id()), "desk bound to the outpost");

        // the garrison: 6 soldiers and 1 officer at their posts, stationary, persistent, inside the box
        List<SeafarerMob> garrison = navy(level, box);
        assertGarrison(helper, garrison, box);
        List<GarrisonPosts.Assignment> plan = Garrison.plan(container);
        for (GarrisonPosts.Assignment a : plan) {
            SeafarerMob mob = garrison.stream().filter(m -> m.blockPosition().equals(a.pos())).findFirst().orElse(null);
            helper.assertTrue(mob != null, "a mob stands at the post " + a.pos().toShortString());
            helper.assertTrue(a.role() == GarrisonPosts.Role.OFFICER ? mob instanceof NavyOfficer : mob instanceof NavySoldier,
                    "the post " + a.pos().toShortString() + " has a " + a.role());
            helper.assertTrue(Mth.degreesDifferenceAbs(mob.getYRot(), a.facing().toYRot()) < 1f,
                    "the mob at " + a.pos().toShortString() + " faces " + a.facing());
            helper.assertTrue(level.getBlockState(a.pos().below()).isFaceSturdy(level, a.pos().below(), Direction.UP),
                    "the post " + a.pos().toShortString() + " stands on a sturdy block");
            helper.assertTrue(level.noCollision(mob), "the mob at " + a.pos().toShortString() + " is not stuck in a block");
        }
        // the officer stands in the gate's court
        SeafarerMob officer = garrison.stream().filter(m -> m instanceof NavyOfficer).findFirst().orElseThrow();
        helper.assertTrue(gateBox.isInside(officer.blockPosition()), "the officer stands in the fort gate");
        // ... inside the port's box, so law.ransom_needs_port accepts his ransoms (OfficerTurnIns, LA2)
        helper.assertTrue(port.contains(level.dimension(), officer.blockPosition()), "the officer stands inside the navy port");

        // placing the same structure again does not double the garrison
        WorldGameTests.place(level, start);
        assertGarrison(helper, navy(level, box), box);

        // PRT1a: one harbor master behind the desk, not doubled by the second placement
        com.richardsenger.piratesnships.mob.harbor.HarborMasterGameTests.assertPlacedAtDesk(helper, level, box, desk, port.id());
        // leave no armed garrison behind for neighbouring tests
        navy(level, box).forEach(SeafarerMob::discard);
        helper.succeed();
    }

    /**
     * Removes the framework's barrier ceiling over the test area: the towers' roofs reach above the 16-high template,
     * and the templates carry no air, so a barrier would stay where a tower guard stands.
     */
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

    private static void assertGarrison(GameTestHelper helper, List<SeafarerMob> garrison, BoundingBox box) {
        long soldiers = garrison.stream().filter(m -> m instanceof NavySoldier).count();
        long officers = garrison.stream().filter(m -> m instanceof NavyOfficer).count();
        helper.assertValueEqual(soldiers, 6L, "garrison soldiers");
        helper.assertValueEqual(officers, 1L, "garrison officers");
        for (SeafarerMob m : garrison) {
            helper.assertTrue(m.isStationary(), m + " is stationary");
            helper.assertTrue(m.isPersistenceRequired(), m + " is persistent");
            helper.assertTrue(box.isInside(m.blockPosition()), m + " inside the outpost's box");
        }
    }

    private static List<SeafarerMob> navy(ServerLevel level, BoundingBox box) {
        return level.getEntitiesOfClass(SeafarerMob.class, AABB.of(box).inflate(1), m -> m.faction() == MobFaction.NAVY && m.isAlive());
    }

    private static String name(StructurePiece piece) {
        return piece instanceof PoolElementStructurePiece p ? GarrisonPosts.pieceName(p.getElement().toString()) : "";
    }

    private static boolean isPiece(StructurePiece piece, String name) {
        return piece instanceof PoolElementStructurePiece p && GarrisonPosts.pieceName(p.getElement().toString()).equals(name);
    }

    private static PortStructure structure(ServerLevel level) {
        Structure s = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(OutpostKeys.NAVY_OUTPOST);
        if (!(s instanceof PortStructure outpost)) throw new IllegalStateException("navy_outpost is not loaded: " + s);
        return outpost;
    }
}
