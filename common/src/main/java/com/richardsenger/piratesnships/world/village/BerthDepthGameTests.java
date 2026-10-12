package com.richardsenger.piratesnships.world.village;

import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.template.ShipOrders;
import com.richardsenger.piratesnships.ship.template.ShipTemplates;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.WorldConfig;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.PortService;
import com.richardsenger.piratesnships.world.port.ShipSpot;
import com.richardsenger.piratesnships.world.port.ShipSpots;
import com.richardsenger.piratesnships.world.structure.BerthBasins;
import com.richardsenger.piratesnships.world.structure.PierExtensions;
import com.richardsenger.piratesnships.world.structure.PortStructure;
import com.richardsenger.piratesnships.world.structure.StructureLayout;
import com.richardsenger.piratesnships.world.structure.StructureLayoutGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * STR7's deep berths (design.md §10.1) on a sloped beach, in the pattern of STR3's placed villages: the sea floor
 * falls one block every {@link #SLOPE} blocks from the shore, so the pier's berths (11 out) stand in three blocks of
 * water and the pier's end (20 out) in five, one short of the rule (draught 2 + berth depth 3 under the surface). The
 * village is planned on that terrain through the real jigsaw ({@link PortStructure#plan}) and placed chunk by chunk.
 *
 * <ul>
 *     <li>{@link #shallowBeachGetsADeepBerth}: one {@code pier_extension} hangs from the pier (the water beyond it is
 *     deep enough, so no second one), the port registers four berths, every berth's own mooring meets the depth rule
 *     for the largest shipped template (the floor was carved), no sand within the clearance, the piles reach the carved
 *     floor, and a ship of the largest template ordered or granted at the port lies at berth 1, assembled.</li>
 * </ul>
 */
public final class BerthDepthGameTests {

    static final int SEA_Y = 10;
    /** The first land row (canonical z) and the dock head's site, as in {@link VillageLayoutGameTests} but 10 farther in. */
    static final int SHORE_Z = 56;
    static final int SITE_X = 80;
    static final int SITE_Z = SHORE_Z + 9;
    static final int SIZE = 160;
    static final int HEIGHT = 40;
    /** One block deeper every SLOPE blocks out from the shore. */
    static final int SLOPE = 5;
    static final int PLAN_X = 12200;
    static final int PLAN_SHORE_Z = 12256;
    static final long SEED = 7L;

    private BerthDepthGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(BerthDepthGameTests.class);
    }

    /** Water blocks over the floor {@code out} blocks out from the shore (1 at the shore, at most the arena's 9). */
    static int water(int out) {
        return Math.min(SEA_Y - 1, out / SLOPE + 1);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = 400, batch = "pirates_n_ships_berth_depth")
    public static void shallowBeachGetsADeepBerth(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PortStructure village = VillageLayoutGameTests.village(level);
        helper.assertTrue(WorldConfig.PORTS_PIER_EXTENSION.get() && WorldConfig.PORTS_CARVE_BERTHS.get(), "STR7 defaults on");
        helper.assertTrue(village.extensionPool().isPresent(), "the village names its extension pool");
        buildBeach(helper);
        clearCeiling(helper);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int seaY = origin.getY() + SEA_Y;
        int depth = BerthBasins.depth();
        helper.assertValueEqual(depth, 5, "draught 2 + berth depth 3");
        // the pier's end (20 out) is shallow, the first extension's end (32 out) is not
        helper.assertTrue(PierExtensions.shallow(seaY - water(20) + 1, seaY, depth), "the pier's end is shallow");
        helper.assertFalse(PierExtensions.shallow(seaY - water(32) + 1, seaY, depth), "the first section's end is deep enough");

        List<StructurePiece> pieces = plan(helper, level, seaY);
        BlockPos site = new BlockPos(origin.getX() + SITE_X, seaY, origin.getZ() + SITE_Z);
        int dx = site.getX() - PLAN_X;
        int dz = site.getZ() - (PLAN_SHORE_Z + SITE_Z - SHORE_Z);
        pieces.forEach(piece -> piece.move(dx, 0, dz));
        StructureLayout layout = StructureLayout.of(pieces);
        helper.assertValueEqual(layout.named("pier").size(), 1, "one pier");
        helper.assertValueEqual(layout.named("pier_extension").size(), 1, "one extension section");
        BoundingBox pier = layout.named("pier").get(0).box();
        BoundingBox section = layout.named("pier_extension").get(0).box();
        helper.assertValueEqual(section.maxZ(), pier.minZ() - 1, "the section hangs from the pier's seaward end");
        helper.assertValueEqual(section.maxY(), pier.maxY(), "deck rows level (the section reaches two rows deeper)");
        StructureStart start = new StructureStart(village, new ChunkPos(site), 0, new PiecesContainer(pieces));
        place(level, start);

        Port port = PortRegistry.get(level.getServer()).index()
                .byId(PortService.portId(PortKind.SEAFARER_VILLAGE, PortService.centreOf(new PiecesContainer(pieces)))).orElse(null);
        helper.assertTrue(port != null, "the port is registered");
        helper.assertValueEqual(port.berths().size(), 4, "berths of the pier and the section");

        // every berth's own mooring meets the depth rule for the largest shipped template, with the ground cleared
        ShipSpots.Shape largest = ShipSpots.shape(level, ShipTemplates.NAVY_SLOOP_ARMED_ID).orElseThrow();
        helper.assertValueEqual(largest.hull(), ShipSpots.LARGEST_SHIPPED, "the armed sloop is the largest shipped hull");
        List<ShipSpot.Anchor> anchors = ShipSpots.anchors(level, port);
        int clearance = WorldConfig.PORTS_SHIP_CLEARANCE.get();
        for (int i = 0; i < anchors.size(); i++) {
            ShipSpot.Anchor a = anchors.get(i);
            helper.assertTrue(a.pierSide() != null, "berth " + (i + 1) + " knows its pier side");
            ShipSpot.Search s = ShipSpot.find((x, z) -> ShipSpots.flags(level, x, z, seaY, largest.draught(), WorldConfig.PORTS_BERTH_DEPTH.get(),
                    largest.above()), List.of(a), largest.hull(), new ShipSpot.Rules(clearance, 0), r -> false);
            helper.assertTrue(s.spot().isPresent(), "berth " + (i + 1) + " at " + a.x() + " " + a.z() + ": a deep mooring (" + s.failure() + ")");
            helper.assertValueEqual(s.spot().get().distance(), 0, "berth " + (i + 1) + ": at the berth itself");
        }
        // the pier's berths stood in three blocks of water before the carve
        BlockPos first = port.berths().get(0).pos();
        int out = SHORE_Z - (first.getZ() - origin.getZ());
        helper.assertTrue(water(out) < depth + 1, "berth 1 was shallow (" + water(out) + " water blocks " + out + " out)");
        // the piles of the pier reach the carved floor: under a footing beside the berth there is no water any more
        int pileX = first.getX() + (anchors.get(0).pierSide().getStepX());
        int pileZ = pier.minZ() + 1;   // the pier's seaward pile row (z 1 of the pier)
        BlockState under = level.getBlockState(new BlockPos(pileX, seaY - depth, pileZ));
        helper.assertFalse(under.is(Blocks.WATER), "the pile at " + pileX + " " + pileZ + " reaches the carved floor: " + under);

        // a ship of the largest template ordered or granted here lies at berth 1, assembled
        ServerPlayer player = serverPlayer(helper, port.centre());
        ShipOrders.Delivery delivery = ShipOrders.deliver(level, port, ShipTemplates.NAVY_SLOOP_ARMED_ID, player, "STR7 test");
        delivery.berthed().map(b -> b.placed().assembly()).filter(a -> a != null && a.shipId() != null)
                .ifPresent(a -> ShipTestCleanup.track(helper, a.shipId()));
        helper.assertTrue(delivery.berthed().isPresent(), "delivered: " + delivery.failure());
        helper.assertValueEqual(delivery.berthed().get().berth(), 1, "at berth 1");
        UUID ship = delivery.berthed().get().placed().assembly().shipId();
        helper.assertTrue(ship != null && SableShips.byId(level, ship) != null, "assembled");
        PortRegistry.get(level.getServer()).remove(port.id());
        helper.succeed();
    }

    /** The village planned on the sloped beach at the fixed site (the sea to the north). */
    static List<StructurePiece> plan(GameTestHelper helper, ServerLevel level, int seaY) {
        PortStructure village = VillageLayoutGameTests.village(level);
        BlockPos site = new BlockPos(PLAN_X, seaY, PLAN_SHORE_Z + SITE_Z - SHORE_Z);
        PortStructure.Terrain terrain = (x, z) -> z < PLAN_SHORE_Z ? seaY - water(PLAN_SHORE_Z - z) + 1 : seaY + 2;
        ChunkGenerator beach = StructureLayoutGameTests.beachGenerator(helper, level, seaY + 2);
        Structure.GenerationContext context = new Structure.GenerationContext(level.registryAccess(), beach, beach.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), SEED, new ChunkPos(site), level, b -> true);
        Optional<Structure.GenerationStub> stub = village.plan(context, site, seaY, terrain, village.maxDepth());
        helper.assertTrue(stub.isPresent(), "the shore gives a generation point");
        return new ArrayList<>(stub.get().getPiecesBuilder().build().pieces());
    }

    /**
     * The sloped beach: sand floor under {@link #water} blocks of water north of {@link #SHORE_Z} (stone below the sand),
     * sand up to one above the sea on the land side, a stone rim round the sea.
     */
    static void buildBeach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState sand = Blocks.SAND.defaultBlockState();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                boolean sea = z < SHORE_Z;
                boolean rim = x == 0 || x == SIZE - 1 || z == 0;
                int floor = sea ? SEA_Y - water(SHORE_Z - z) : SEA_Y + 1;   // the top ground row
                for (int y = 1; y <= SEA_Y + 1; y++) {
                    BlockState state;
                    if (sea && rim) state = y <= SEA_Y ? stone : null;
                    else if (y < floor) state = stone;
                    else if (y == floor) state = sand;
                    else state = sea && y <= SEA_Y ? water : null;
                    if (state != null) level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static void clearCeiling(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -1; x <= SIZE; x++) {
            for (int z = -1; z <= SIZE; z++) {
                for (int y = HEIGHT - 1; y <= HEIGHT + 2; y++) {
                    BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                    if (level.getBlockState(pos).is(Blocks.BARRIER)) level.setBlock(pos, air, Block.UPDATE_CLIENTS);
                }
            }
        }
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

    /** A real server player with a mock connection, not added to the level, standing at {@code at}. */
    static ServerPlayer serverPlayer(GameTestHelper h, BlockPos at) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "berth_test");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atCenterOf(at));
        return p;
    }
}
