package com.richardsenger.piratesnships.world;

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
import com.richardsenger.piratesnships.world.port.Berth;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.PortService;
import com.richardsenger.piratesnships.world.structure.PortStructure;
import com.richardsenger.piratesnships.world.village.ShoreFacing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.phys.AABB;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The navy outpost on the hand-built shore of {@link WorldGameTests} (WG3): 48×48 sand beach, sea surface at relative
 * y 6. Depth 1: the fort gate, its quay, one wall each way ending in a wall tower, and one building on the apron
 * (15 + 2 × 14 = 43 blocks along the shore, 15 + 9 inland, 18 out over the water). The structure is placed through
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
        for (ResourceKey<StructureTemplatePool> key : List.of(OutpostKeys.START, OutpostKeys.WALLS, OutpostKeys.BUILDINGS,
                OutpostKeys.QUAY, OutpostKeys.TERMINATORS)) {
            StructureTemplatePool pool = pools.get(key);
            helper.assertTrue(pool != null, "pool " + key.location() + " is loaded");
            for (StructurePoolElement element : pool.getShuffledTemplates(RandomSource.create(0L))) {
                var size = element.getSize(level.getStructureManager(), Rotation.NONE);
                helper.assertTrue(size.getX() > 0 && size.getY() > 0 && size.getZ() > 0, key.location() + ": " + element + " has no template");
                elements++;
            }
        }
        // fort gate, wall, barracks ×2, brig, watchtower (weights expand), quay, wall tower
        helper.assertValueEqual(elements, 1 + 1 + 4 + 1 + 1, "pool elements");
        helper.assertValueEqual(pools.get(OutpostKeys.WALLS).getFallback().unwrapKey(), Optional.of(OutpostKeys.TERMINATORS),
                "walls fall back to the wall tower");
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

        Structure.GenerationContext context = WorldGameTests.context(level, site);
        Optional<Structure.GenerationStub> stub = structure.plan(context, site, seaY, WorldGameTests.terrain(helper), 1);
        helper.assertTrue(stub.isPresent(), "the shore gives a generation point");
        StructureStart start = new StructureStart(structure, context.chunkPos(), 0, stub.get().getPiecesBuilder().build());
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
        helper.assertValueEqual(count(pieces, "wall"), 2L, "one wall each way");
        helper.assertValueEqual(count(pieces, "wall_tower"), 2L, "each wall run ends in a tower");

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

    private static long count(List<StructurePiece> pieces, String name) {
        return pieces.stream().filter(p -> isPiece(p, name)).count();
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
