package com.richardsenger.piratesnships.world.structure;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.mob.MobFaction;
import com.richardsenger.piratesnships.mob.entity.NavyOfficer;
import com.richardsenger.piratesnships.mob.entity.NavySoldier;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.outpost.GarrisonPosts;
import com.richardsenger.piratesnships.world.outpost.OutpostData;
import com.richardsenger.piratesnships.world.outpost.OutpostKeys;
import com.richardsenger.piratesnships.world.outpost.OutpostPieces;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.PortService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The navy outpost's natural layout (STR4), with the tools and the pattern of {@link StructureLayoutGameTests}: the
 * outpost is planned headless through the real jigsaw assembler ({@link PortStructure#plan}) under six seeds on a
 * straight shore (sea to the north) and checked per instance, the beard is fed the planned pieces, and three seeds are
 * placed for real and dumped as schematics for the renders.
 *
 * <ul>
 *     <li>{@link #navyOutpostLayoutsAreNatural}: at least {@link #MIN_PIECES} pieces, a footprint aspect of at least
 *     {@link StructureLayoutGameTests#MIN_ASPECT}, at least {@link #MIN_VARIANTS} distinct buildings, both wall runs
 *     from the gate ending in a tower (every wall piece leads on to a wall or a tower), the quay's far end over the
 *     water; no two instances with the same piece sequence. Dumps {@code navy_outpost-layout-<seed>.txt}
 *     ({@code tools/structure_check.py --prefix navy_outpost-layout-}).</li>
 *     <li>{@link #navyOutpostBeardSparesTheQuayAndTheWalls}: the rigid chain from the gate (quay, walls, towers) is out
 *     of the beard's reach, so the beard adds nothing at the berths and the quay's far end; a building of the court is
 *     bearded.</li>
 *     <li>{@code navyOutpostPlaced<n>}: three seeds placed on a 160 by 160 beach: the quay's far end and both berths in
 *     water, the port registered, the walls level with the gate, the garrison at its posts; the region is written as
 *     {@code navy_outpost_<seed>.schem}.</li>
 * </ul>
 * The files go to {@value StructureLayoutGameTests#DIR_PROPERTY} (default {@code structure-layouts} in the run directory).
 */
public final class OutpostLayoutGameTests {

    /** Six seeds far apart ({@link StructureLayoutGameTests#ISLAND_SEEDS} explains why). */
    public static final long[] SEEDS = {11L, 2024L, 4242L, 31337L, 65536L, 99991L};
    public static final long[] RENDER_SEEDS = {11L, 2024L, 4242L};
    public static final int MIN_PIECES = 12;
    public static final int MIN_VARIANTS = 4;
    static final String DUMP_PREFIX = "navy_outpost-layout-";

    private OutpostLayoutGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(OutpostLayoutGameTests.class);
    }

    @ModGameTest(timeoutTicks = 300)
    public static void navyOutpostLayoutsAreNatural(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int seaY = helper.absolutePos(BlockPos.ZERO).getY() + StructureLayoutGameTests.SEA_Y;
        List<String> buildings = OutpostPieces.BUILDINGS;
        Map<String, Long> sequences = new HashMap<>();
        clearDumps(DUMP_PREFIX);
        for (long seed : SEEDS) {
            List<StructurePiece> pieces = plan(helper, level, seaY, seed);
            StructureLayout layout = StructureLayout.of(pieces);
            BoundingBox quay = single(helper, layout, "quay", seed);
            boolean waterEnd = quay.minZ() < StructureLayoutGameTests.PLAN_SHORE_Z;
            write(layout.lines(OutpostKeys.NAVY_OUTPOST.location().toString(), seed, buildings, waterEnd),
                    DUMP_PREFIX + seed + ".txt");
            Runs runs = runs(pieces);
            Constants.LOG.info("STR4 layout seed {}: {} pieces, {} by {}, aspect {}, variants {}, runs {} / {}", seed,
                    layout.pieces().size(), layout.bounds().getXSpan(), layout.bounds().getZSpan(),
                    String.format(Locale.ROOT, "%.2f", layout.aspect()), layout.variants(buildings), runs.names(0), runs.names(1));
            helper.assertTrue(layout.pieces().size() >= MIN_PIECES,
                    "seed " + seed + ": " + layout.pieces().size() + " pieces, at least " + MIN_PIECES);
            helper.assertTrue(layout.aspect() >= StructureLayoutGameTests.MIN_ASPECT, String.format(Locale.ROOT,
                    "seed %d: aspect %.2f (%d by %d), at least %.2f", seed, layout.aspect(), layout.bounds().getXSpan(),
                    layout.bounds().getZSpan(), StructureLayoutGameTests.MIN_ASPECT));
            helper.assertTrue(layout.variants(buildings).size() >= MIN_VARIANTS,
                    "seed " + seed + ": buildings " + layout.variants(buildings) + ", at least " + MIN_VARIANTS + " distinct");
            helper.assertValueEqual(runs.runs().size(), 2, "seed " + seed + ": wall runs from the gate");
            for (int r = 0; r < 2; r++) {
                List<String> names = runs.names(r);
                helper.assertTrue(OutpostPieces.isTower(names.get(names.size() - 1)),
                        "seed " + seed + ": the run " + names + " ends in a tower");
            }
            helper.assertTrue(waterEnd, "seed " + seed + ": the quay's far end (z " + quay.minZ() + ") is over the water");
            Long same = sequences.put(layout.sequence(), seed);
            helper.assertTrue(same == null, "seeds " + same + " and " + seed + " give the same piece sequence");
        }
        helper.succeed();
    }

    /**
     * Vanilla's beardifier, fed the outpost's pieces the way {@code Beardifier.forStructuresInChunk} feeds them: the
     * quay, every wall and every tower carry the unbearded ground level, so the beard adds nothing at the berths and
     * the quay's far end; beside a building of the court it fills the ground.
     */
    @ModGameTest(timeoutTicks = 100)
    public static void navyOutpostBeardSparesTheQuayAndTheWalls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int seaY = helper.absolutePos(BlockPos.ZERO).getY() + StructureLayoutGameTests.SEA_Y;
        PortStructure outpost = outpost(level);
        helper.assertValueEqual(outpost.terrainAdaptation(), TerrainAdjustment.BEARD_THIN, "terrain adaptation");
        helper.assertTrue(outpost.landPiecesOnLand(), "land pieces stay on land");
        for (long seed : RENDER_SEEDS) {
            List<StructurePiece> pieces = plan(helper, level, seaY, seed);
            var rigids = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Beardifier.Rigid>();
            var junctions = new it.unimi.dsi.fastutil.objects.ObjectArrayList<JigsawJunction>();
            int chain = 0;
            for (StructurePiece piece : pieces) {
                if (!(piece instanceof PoolElementStructurePiece p)) continue;
                String name = GarrisonPosts.pieceName(p.getElement().toString());
                if (p.getElement().getProjection() == StructureTemplatePool.Projection.RIGID) {
                    rigids.add(new Beardifier.Rigid(p.getBoundingBox(), outpost.terrainAdaptation(), p.getGroundLevelDelta()));
                }
                junctions.addAll(p.getJunctions());
                if (name.equals("quay") || OutpostPieces.isWall(name) || OutpostPieces.isTower(name)) {
                    helper.assertValueEqual(p.getGroundLevelDelta(), PortStructure.UNBEARDED_GROUND_LEVEL_DELTA,
                            "seed " + seed + ": " + name + " is out of the beard's reach");
                    chain++;
                }
            }
            helper.assertTrue(chain >= 5, "seed " + seed + ": the quay, the walls and the towers (" + chain + ")");
            Beardifier beard = new Beardifier(rigids.iterator(), junctions.iterator());
            PoolElementStructurePiece quay = pieces.stream().filter(p -> p instanceof PoolElementStructurePiece e
                    && GarrisonPosts.pieceName(e.getElement().toString()).equals("quay")).map(p -> (PoolElementStructurePiece) p)
                    .findFirst().orElseThrow();
            BoundingBox q = quay.getBoundingBox();
            // the berths (the quay's outer columns beside the deck, nine rows from the far end), the far end itself
            List<BlockPos> water = List.of(new BlockPos(q.minX(), seaY, q.minZ() + 9), new BlockPos(q.maxX(), seaY, q.minZ() + 9),
                    new BlockPos(q.minX(), seaY, q.minZ()), new BlockPos(q.maxX(), seaY, q.minZ()), new BlockPos(q.minX(), seaY - 2, q.minZ() + 9));
            for (BlockPos p : water) {
                double d = beard.compute(new DensityFunction.SinglePointContext(p.getX(), p.getY(), p.getZ()));
                helper.assertTrue(d < 0.05, "seed " + seed + ": the beard leaves the water at " + p.toShortString() + " (" + d + ")");
            }
            StructureLayout layout = StructureLayout.of(pieces);
            StructureLayout.Piece building = layout.pieces().stream().filter(p -> OutpostPieces.BUILDINGS.contains(p.name()))
                    .findFirst().orElse(null);
            helper.assertTrue(building != null, "seed " + seed + ": a building in the court");
            double under = beard.compute(new DensityFunction.SinglePointContext(building.box().minX() - 1, building.box().minY() - 1,
                    building.box().minZ() + 1));
            helper.assertTrue(under > 0.05, "seed " + seed + ": the beard fills the ground beside " + building.name() + ": " + under);
        }
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = 400, batch = "pirates_n_ships_outpost_layout")
    public static void navyOutpostPlaced1(GameTestHelper helper) {
        place(helper, RENDER_SEEDS[0]);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = 400, batch = "pirates_n_ships_outpost_layout")
    public static void navyOutpostPlaced2(GameTestHelper helper) {
        place(helper, RENDER_SEEDS[1]);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = 400, batch = "pirates_n_ships_outpost_layout")
    public static void navyOutpostPlaced3(GameTestHelper helper) {
        place(helper, RENDER_SEEDS[2]);
    }

    /** Builds the beach, places the outpost of {@code seed} on it, checks the quay, port, walls and garrison, dumps it. */
    private static void place(GameTestHelper helper, long seed) {
        ServerLevel level = helper.getLevel();
        PortStructure outpost = outpost(level);
        buildBeach(helper);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int seaY = origin.getY() + StructureLayoutGameTests.SEA_Y;
        BlockPos site = new BlockPos(origin.getX() + StructureLayoutGameTests.SITE_X, seaY,
                origin.getZ() + StructureLayoutGameTests.SITE_Z);
        List<StructurePiece> pieces = plan(helper, level, seaY, seed);
        int dx = site.getX() - StructureLayoutGameTests.PLAN_X;
        int dz = site.getZ() - (StructureLayoutGameTests.PLAN_SHORE_Z + StructureLayoutGameTests.SITE_Z - StructureLayoutGameTests.SHORE_Z);
        pieces.forEach(piece -> piece.move(dx, 0, dz));
        StructureStart start = new StructureStart(outpost, new ChunkPos(site), 0, new PiecesContainer(pieces));
        StructureLayout layout = StructureLayout.of(pieces);
        clearCeiling(helper);
        placeChunks(level, start);

        // the quay's far end and both berths stand in water
        BoundingBox quay = single(helper, layout, "quay", seed);
        for (BlockPos p : List.of(new BlockPos(quay.minX(), seaY, quay.minZ() + 9), new BlockPos(quay.maxX(), seaY, quay.minZ() + 9),
                new BlockPos((quay.minX() + quay.maxX()) / 2, seaY - 2, quay.minZ() - 1))) {
            helper.assertTrue(level.getBlockState(p).is(Blocks.WATER), "seed " + seed + ": water at " + p.toShortString());
        }
        // the walls are level with the gate (the bastions stand three rows deeper on their footings)
        BoundingBox gate = layout.pieces().get(0).box();
        for (StructureLayout.Piece p : layout.pieces()) {
            if (!OutpostPieces.isWall(p.name()) && !OutpostPieces.isTower(p.name())) continue;
            int expected = gate.minY() - (p.name().endsWith("bastion") ? 3 : 0);
            helper.assertValueEqual(p.box().minY(), expected, "seed " + seed + ": " + p.name() + " at " + p.box() + " is level with the gate");
        }
        Port port = PortRegistry.get(level.getServer()).index()
                .byId(PortService.portId(PortKind.NAVY_OUTPOST, PortService.centreOf(new PiecesContainer(pieces)))).orElse(null);
        helper.assertTrue(port != null, "seed " + seed + ": the port is registered");
        helper.assertValueEqual(port.berths().size(), 2, "seed " + seed + ": berths");
        for (var berth : port.berths()) {
            helper.assertTrue(level.getBlockState(berth.pos()).is(Blocks.WATER), "seed " + seed + ": berth " + berth.pos().toShortString() + " is water");
        }
        // the garrison: the default 6 soldiers and 1 officer at their posts
        BoundingBox b = layout.bounds();
        List<SeafarerMob> navy = level.getEntitiesOfClass(SeafarerMob.class, AABB.of(b).inflate(1),
                m -> m.faction() == MobFaction.NAVY && m.isAlive());
        helper.assertValueEqual(navy.stream().filter(m -> m instanceof NavySoldier).count(), 6L, "seed " + seed + ": soldiers");
        helper.assertValueEqual(navy.stream().filter(m -> m instanceof NavyOfficer).count(), 1L, "seed " + seed + ": officers");
        for (SeafarerMob m : navy) {
            helper.assertTrue(level.noCollision(m), "seed " + seed + ": " + m + " is not stuck in a block");
        }

        BoundingBox region = new BoundingBox(b.minX() - StructureLayoutGameTests.MARGIN,
                Math.max(level.getMinBuildHeight(), b.minY() - StructureLayoutGameTests.MARGIN), b.minZ() - StructureLayoutGameTests.MARGIN,
                b.maxX() + StructureLayoutGameTests.MARGIN, Math.min(level.getMaxBuildHeight() - 1, b.maxY() + StructureLayoutGameTests.MARGIN),
                b.maxZ() + StructureLayoutGameTests.MARGIN);
        try {
            RegionSchematic.write(level, region, helper.absolutePos(BlockPos.ZERO), StructureLayoutGameTests.dir().resolve("navy_outpost_" + seed + ".schem"));
        } catch (IOException e) {
            throw new IllegalStateException("could not write the region of seed " + seed, e);
        }
        // STR6: every door can be walked through from both sides (after the dump, so a failure leaves the region to look at)
        List<String> blockedDoors = DoorAccess.blockedDoors(level, start.getBoundingBox());
        helper.assertTrue(blockedDoors.isEmpty(), "seed " + seed + ": " + blockedDoors.size() + " blocked doors: " + blockedDoors);
        navy.forEach(SeafarerMob::discard);
        helper.succeed();
    }

    // --- the wall runs ------------------------------------------------------------------------------------------

    /** The two wall runs from the gate, each the list of its pieces' indices from the gate outward. */
    record Runs(List<StructurePiece> pieces, List<List<Integer>> runs) {
        List<String> names(int run) {
            if (run >= runs.size()) return List.of();
            return runs.get(run).stream().map(i -> name(pieces.get(i))).toList();
        }
    }

    /**
     * The wall runs of a planned outpost: from the gate, through every wall piece, to its end. A run's piece leads on
     * to the wall or tower whose first junction (the one to its parent) starts in it ({@link PortStructure#landNodes}).
     */
    static Runs runs(List<StructurePiece> pieces) {
        List<LandFilter.Node> nodes = PortStructure.landNodes(pieces);
        List<List<Integer>> runs = new ArrayList<>();
        for (int i = 1; i < pieces.size(); i++) {
            if (nodes.get(i).parent() != 0 || !isTrace(pieces.get(i))) continue;
            List<Integer> run = new ArrayList<>();
            int at = i;
            while (at >= 0) {
                run.add(at);
                int next = -1;
                for (int j = at + 1; j < pieces.size(); j++) {
                    if (nodes.get(j).parent() == at && isTrace(pieces.get(j))) {
                        next = j;
                        break;
                    }
                }
                at = next;
            }
            runs.add(run);
        }
        return new Runs(pieces, runs);
    }

    private static boolean isTrace(StructurePiece piece) {
        String name = name(piece);
        return OutpostPieces.isWall(name) || OutpostPieces.isTower(name);
    }

    private static String name(StructurePiece piece) {
        return piece instanceof PoolElementStructurePiece p ? GarrisonPosts.pieceName(p.getElement().toString()) : "";
    }

    // --- helpers (the island test's, for the outpost) ----------------------------------------------------------

    /** The outpost of {@code seed} planned at {@link StructureLayoutGameTests}' fixed site on its straight shore. */
    static List<StructurePiece> plan(GameTestHelper helper, ServerLevel level, int seaY, long seed) {
        PortStructure outpost = outpost(level);
        int shore = StructureLayoutGameTests.PLAN_SHORE_Z;
        BlockPos site = new BlockPos(StructureLayoutGameTests.PLAN_X, seaY,
                shore + StructureLayoutGameTests.SITE_Z - StructureLayoutGameTests.SHORE_Z);
        PortStructure.Terrain terrain = (x, z) -> z < shore ? seaY - 4 : seaY + 2;
        Structure.GenerationContext context = StructureLayoutGameTests.context(level,
                StructureLayoutGameTests.beachGenerator(helper, level, seaY + 2), site, seed);
        Optional<Structure.GenerationStub> stub = outpost.plan(context, site, seaY, terrain, outpost.maxDepth());
        helper.assertTrue(stub.isPresent(), "seed " + seed + ": the shore gives a generation point");
        return new ArrayList<>(stub.get().getPiecesBuilder().build().pieces());
    }

    static PortStructure outpost(ServerLevel level) {
        Structure s = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(OutpostKeys.NAVY_OUTPOST);
        if (!(s instanceof PortStructure outpost)) throw new IllegalStateException("navy_outpost is not loaded: " + s);
        checkSize(outpost);
        return outpost;
    }

    private static void checkSize(PortStructure outpost) {
        if (outpost.maxDepth() != OutpostData.SIZE) {
            throw new IllegalStateException("navy_outpost has size " + outpost.maxDepth() + ", OutpostData says " + OutpostData.SIZE);
        }
    }

    private static BoundingBox single(GameTestHelper helper, StructureLayout layout, String name, long seed) {
        List<StructureLayout.Piece> found = layout.named(name);
        helper.assertValueEqual(found.size(), 1, "seed " + seed + ": " + name + " pieces");
        return found.get(0).box();
    }

    /** The island test's beach: stone under the sea, water to the sea surface, sand one above it, a rim round the sea. */
    private static void buildBeach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState sand = Blocks.SAND.defaultBlockState();
        int size = StructureLayoutGameTests.SIZE;
        int seaY = StructureLayoutGameTests.SEA_Y;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                boolean sea = z < StructureLayoutGameTests.SHORE_Z;
                boolean rim = x == 0 || x == size - 1 || z == 0;
                for (int y = 1; y <= seaY + 1; y++) {
                    BlockState state = sea ? (y <= 4 || (rim && y <= seaY) ? stone : y <= seaY ? water : null) : sand;
                    if (state != null) level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Takes away the framework's barrier ceiling (vanilla's gravity processor would lay the lanes on it). */
    private static void clearCeiling(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState air = Blocks.AIR.defaultBlockState();
        int size = StructureLayoutGameTests.SIZE;
        int height = StructureLayoutGameTests.HEIGHT;
        for (int x = -1; x <= size; x++) {
            for (int z = -1; z <= size; z++) {
                for (int y = height - 1; y <= height + 2; y++) {
                    BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                    if (level.getBlockState(pos).is(Blocks.BARRIER)) level.setBlock(pos, air, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Places every chunk of {@code start}, as {@code /place structure} does. */
    private static void placeChunks(ServerLevel level, StructureStart start) {
        BoundingBox box = start.getBoundingBox();
        ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ()));
        ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()));
        ChunkPos.rangeClosed(min, max).forEach(chunk -> start.placeInChunk(level, level.structureManager(),
                level.getChunkSource().getGenerator(), level.getRandom(),
                new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                        chunk.getMaxBlockX(), level.getMaxBuildHeight(), chunk.getMaxBlockZ()), chunk));
    }

    private static void clearDumps(String prefix) {
        Path dir = StructureLayoutGameTests.dir();
        if (!Files.isDirectory(dir)) return;
        try (var files = Files.list(dir)) {
            for (Path f : files.filter(f -> f.getFileName().toString().startsWith(prefix)).toList()) Files.delete(f);
        } catch (IOException e) {
            throw new IllegalStateException("could not clear " + dir, e);
        }
    }

    private static void write(List<String> lines, String name) {
        try {
            Files.createDirectories(StructureLayoutGameTests.dir());
            Files.write(StructureLayoutGameTests.dir().resolve(name), lines);
        } catch (IOException e) {
            throw new IllegalStateException("could not write " + name, e);
        }
    }
}
