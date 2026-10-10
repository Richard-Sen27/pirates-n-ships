package com.richardsenger.piratesnships.world.village;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.mob.harbor.HarborMasterGameTests;
import com.richardsenger.piratesnships.trade.desk.HarborDesks;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.port.Berth;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.PortService;
import com.richardsenger.piratesnships.world.structure.PortStructure;
import com.richardsenger.piratesnships.world.structure.RegionSchematic;
import com.richardsenger.piratesnships.world.structure.StructureLayout;
import com.richardsenger.piratesnships.world.structure.StructureLayoutGameTests;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
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
import net.minecraft.world.level.chunk.ChunkGenerator;
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
import java.util.Set;

/**
 * The seafarer village's natural layout (STR3, design.md §10.1 "Natural layouts"), with STR2's tools
 * ({@link StructureLayout}, {@link RegionSchematic}, {@link StructureLayoutGameTests#beachGenerator}): the village is
 * generated headless under several seeds through the real jigsaw assembler ({@link PortStructure#plan}) and every
 * instance must not be a line and must be varied.
 *
 * <ul>
 *     <li>{@link #villageLayoutsAreNatural}: six seeds on a straight shore (sea to the north): per instance at least
 *     {@link #MIN_PIECES} pieces, a footprint aspect of at least {@link #MIN_ASPECT}, at least {@link #MIN_VARIANTS}
 *     distinct buildings of the buildings pool, the pier's far end over the water; no two instances with the same piece
 *     sequence. Each instance goes to {@code village-layout-<seed>.txt} ({@code tools/structure_check.py} reads them:
 *     {@code python3 tools/structure_check.py <dir>/village-layout-*.txt}).</li>
 *     <li>{@link #villageBeardSparesTheBerths}: vanilla's beardifier, fed the village's pieces, adds nothing over the
 *     pier's seaward half (the dock head and the pier are out of the beard's reach) and fills the ground beside a
 *     building.</li>
 *     <li>{@code villagePlaced<n>}: three seeds placed for real on a 160 by 160 beach: the pier's far end stands in
 *     water, the streets lie on the sand, the port registers with its two berths (water, open above), the harbor
 *     master stands at the dock head's desk, and the region is written as {@code seafarer_village_<seed>.schem} for
 *     {@code tools/render_generated_structure.py}.</li>
 * </ul>
 * The files go to {@value StructureLayoutGameTests#DIR_PROPERTY} (default {@code structure-layouts} in the run
 * directory).
 */
public final class VillageLayoutGameTests {

    /**
     * The village's layout seeds; {@link #RENDER_SEEDS} of them are placed and dumped. They lie far apart: the layout
     * random comes from the first of {@code seed}, {@code seed + 1}, ... that gives the shore's rotation.
     */
    public static final long[] VILLAGE_SEEDS = {7L, 1234L, 5150L, 27182L, 77777L, 314159L};
    public static final long[] RENDER_SEEDS = {7L, 1234L, 5150L};
    public static final int MIN_PIECES = 10;
    public static final double MIN_ASPECT = 0.5;
    public static final int MIN_VARIANTS = 4;
    /**
     * The most beard allowed over the pier's seaward half: a hundredth of density lifts the noise's ground by a fraction
     * of a block, and the seabed there lies four or more blocks under the sea surface.
     */
    static final double MAX_BEARD_AT_THE_BERTHS = 0.01;
    /** The pier's width and its berth row counted from the far end (art/README.md, "Structures (ST1)": berths at z 9). */
    static final int PIER_WIDTH = 7;
    static final int BERTH_ROW = 9;
    /** The beach the placed tests build: sea surface row, first land row (canonical z), the dock head's site. */
    static final int SEA_Y = 10;
    static final int SHORE_Z = 46;
    static final int SITE_X = 80;
    static final int SITE_Z = SHORE_Z + 9;
    static final int SIZE = 160;
    static final int HEIGHT = 40;
    static final int MARGIN = 8;
    /** Where every village is planned (x, first land row z), so a seed's layout is the same wherever the arena lies. */
    static final int PLAN_X = 8200;
    static final int PLAN_SHORE_Z = 8256;

    private VillageLayoutGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(VillageLayoutGameTests.class);
    }

    @ModGameTest(timeoutTicks = 200)
    public static void villageLayoutsAreNatural(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int seaY = helper.absolutePos(BlockPos.ZERO).getY() + SEA_Y;
        Set<String> buildings = VillageData.BUILDINGS.keySet();
        Map<String, Long> sequences = new HashMap<>();
        clearDumps("village-layout-");
        for (long seed : VILLAGE_SEEDS) {
            StructureLayout layout = StructureLayout.of(planVillage(helper, level, seaY, seed));
            BoundingBox pier = single(helper, layout, "pier", seed);
            boolean waterEnd = pier.minZ() < PLAN_SHORE_Z;
            write(layout.lines(VillageKeys.SEAFARER_VILLAGE.location().toString(), seed, buildings, waterEnd),
                    "village-layout-" + seed + ".txt");
            Constants.LOG.info("STR3 layout seed {}: {} pieces, {} by {}, aspect {}, variants {}", seed, layout.pieces().size(),
                    layout.bounds().getXSpan(), layout.bounds().getZSpan(), String.format(Locale.ROOT, "%.2f", layout.aspect()),
                    layout.variants(buildings));
            helper.assertTrue(layout.pieces().size() >= MIN_PIECES,
                    "seed " + seed + ": " + layout.pieces().size() + " pieces, at least " + MIN_PIECES);
            helper.assertTrue(layout.aspect() >= MIN_ASPECT, String.format(Locale.ROOT,
                    "seed %d: aspect %.2f (%d by %d), at least %.2f", seed, layout.aspect(), layout.bounds().getXSpan(),
                    layout.bounds().getZSpan(), MIN_ASPECT));
            helper.assertTrue(layout.variants(buildings).size() >= MIN_VARIANTS,
                    "seed " + seed + ": buildings " + layout.variants(buildings) + ", at least " + MIN_VARIANTS + " distinct");
            helper.assertTrue(waterEnd, "seed " + seed + ": the pier's far end (z " + pier.minZ() + ") is over the water (z < "
                    + PLAN_SHORE_Z + ")");
            helper.assertValueEqual(layout.named("dock_head").size(), 1, "seed " + seed + ": one dock head");
            helper.assertTrue(layout.pieces().get(0).name().equals("dock_head"), "seed " + seed + ": the dock head is the start");
            Long same = sequences.put(layout.sequence(), seed);
            helper.assertTrue(same == null, "seeds " + same + " and " + seed + " give the same piece sequence");
        }
        helper.succeed();
    }

    /**
     * The beard ({@code beard_thin}) levels the ground round the buildings but leaves the berths' water alone: vanilla's
     * beardifier, fed the village's pieces the way {@code Beardifier.forStructuresInChunk} feeds them (every rigid pool
     * piece with its ground level, every junction), adds less than {@link #MAX_BEARD_AT_THE_BERTHS} anywhere over the
     * pier's seaward half (its far end and the berth row, from four below the sea surface up to it), because the dock
     * head and the pier are unbearded and keep no rigid junctions; and it fills under the ground row beside a building.
     * Nearer the quay a building on the beach beside the dock head may still reach the pier's landward end (up to about
     * 0.15 one below the sea surface in a scan of 100 seeds, art/README.md "Natural layout of the village (STR3)"), so
     * that part is only logged.
     */
    @ModGameTest(timeoutTicks = 100)
    public static void villageBeardSparesTheBerths(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int seaY = helper.absolutePos(BlockPos.ZERO).getY() + SEA_Y;
        PortStructure village = village(level);
        helper.assertValueEqual(village.terrainAdaptation(), TerrainAdjustment.BEARD_THIN, "terrain adaptation");
        helper.assertTrue(village.unbeardedStart(), "the dock head is unbearded");
        helper.assertTrue(village.landPiecesOnLand(), "land pieces stay on land");
        for (long seed : VILLAGE_SEEDS) {
            List<StructurePiece> pieces = planVillage(helper, level, seaY, seed);
            Beardifier beard = beardOf(pieces, village.terrainAdaptation());
            StructureLayout layout = StructureLayout.of(pieces);
            BoundingBox pier = single(helper, layout, "pier", seed);
            // planned with the sea to the north: the pier runs north from the quay, its far end is its minZ row
            helper.assertValueEqual(pier.getXSpan(), PIER_WIDTH, "seed " + seed + ": the pier runs north");
            double seaward = 0;
            double landward = 0;
            for (int x = pier.minX(); x <= pier.maxX(); x++) {
                for (int z = pier.minZ(); z <= pier.maxZ() && z < PLAN_SHORE_Z; z++) {
                    for (int y = seaY - 4; y <= seaY; y++) {
                        double d = beard.compute(new DensityFunction.SinglePointContext(x, y, z));
                        if (z <= pier.minZ() + BERTH_ROW) seaward = Math.max(seaward, d);
                        else landward = Math.max(landward, d);
                    }
                }
            }
            Constants.LOG.info("STR3 beard seed {}: seaward half {}, landward half {}", seed,
                    String.format(Locale.ROOT, "%.5f", seaward), String.format(Locale.ROOT, "%.5f", landward));
            helper.assertTrue(seaward < MAX_BEARD_AT_THE_BERTHS, "seed " + seed + ": no beard at the berths: " + seaward);
            StructureLayout.Piece house = layout.pieces().stream().filter(p -> VillageData.BUILDINGS.containsKey(p.name()))
                    .findFirst().orElse(null);
            helper.assertTrue(house != null, "seed " + seed + ": a building in the village");
            double under = beard.compute(new DensityFunction.SinglePointContext(house.box().minX() - 1, house.box().minY() - 1,
                    house.box().minZ() + 1));
            helper.assertTrue(under > 0.05, "seed " + seed + ": the beard fills the ground beside " + house.name() + ": " + under);
        }
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = 400, batch = "pirates_n_ships_village_layout")
    public static void villagePlaced1(GameTestHelper helper) {
        placeVillage(helper, RENDER_SEEDS[0]);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = 400, batch = "pirates_n_ships_village_layout")
    public static void villagePlaced2(GameTestHelper helper) {
        placeVillage(helper, RENDER_SEEDS[1]);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = 400, batch = "pirates_n_ships_village_layout")
    public static void villagePlaced3(GameTestHelper helper) {
        placeVillage(helper, RENDER_SEEDS[2]);
    }

    /** Builds the beach, places the village of {@code seed} on it, checks the pier, streets, port and harbor master. */
    private static void placeVillage(GameTestHelper helper, long seed) {
        ServerLevel level = helper.getLevel();
        PortStructure village = village(level);
        buildBeach(helper);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int seaY = origin.getY() + SEA_Y;
        BlockPos site = new BlockPos(origin.getX() + SITE_X, seaY, origin.getZ() + SITE_Z);
        List<StructurePiece> pieces = planVillage(helper, level, seaY, seed);
        int dx = site.getX() - PLAN_X;
        int dz = site.getZ() - (PLAN_SHORE_Z + SITE_Z - SHORE_Z);
        pieces.forEach(piece -> piece.move(dx, 0, dz));
        BoundingBox arena = new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + SIZE - 1,
                origin.getY() + HEIGHT - 1, origin.getZ() + SIZE - 1);
        for (StructurePiece piece : pieces) {
            BoundingBox b = piece.getBoundingBox();
            helper.assertTrue(b.minX() >= arena.minX() && b.maxX() <= arena.maxX() && b.minZ() >= arena.minZ()
                    && b.maxZ() <= arena.maxZ(), "seed " + seed + ": " + piece + " stays in the arena");
        }
        StructureStart start = new StructureStart(village, new ChunkPos(site), 0, new PiecesContainer(pieces));
        StructureLayout layout = StructureLayout.of(pieces);
        clearCeiling(helper);
        place(level, start);

        // the pier's far end stands in water
        BoundingBox pier = single(helper, layout, "pier", seed);
        int water = 0;
        for (int x = pier.minX(); x <= pier.maxX(); x++) {
            if (level.getBlockState(new BlockPos(x, seaY, pier.minZ())).getFluidState().isSource()) water++;
        }
        helper.assertTrue(water >= 2, "seed " + seed + ": the pier's far end stands in water (" + water + " water columns)");
        helper.assertTrue(level.getBlockState(new BlockPos(pier.minX(), seaY - 2, pier.minZ())).is(Blocks.WATER),
                "seed " + seed + ": water under the pier's far end");

        // the streets lie on the beach: their crown, kerbs and verges replace the sand row
        for (StructureLayout.Piece street : layout.pieces()) {
            if (!street.name().startsWith("street")) continue;
            int bed = 0;
            for (int x = street.box().minX(); x <= street.box().maxX(); x++) {
                for (int z = street.box().minZ(); z <= street.box().maxZ(); z++) {
                    BlockState s = level.getBlockState(new BlockPos(x, seaY + 1, z));
                    if (s.is(Blocks.COBBLESTONE) || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.ANDESITE)
                            || s.is(Blocks.MOSSY_COBBLESTONE)) bed++;
                }
            }
            helper.assertTrue(bed >= 6, "seed " + seed + ": " + street.name() + " at " + street.box() + " lies on the beach ("
                    + bed + " street blocks)");
        }

        // the port with its two berths, water and open above
        Port port = PortRegistry.get(level.getServer()).index()
                .byId(PortService.portId(PortKind.SEAFARER_VILLAGE, PortService.centreOf(new PiecesContainer(pieces)))).orElse(null);
        helper.assertTrue(port != null, "seed " + seed + ": the port is registered");
        helper.assertValueEqual(port.berths().size(), 2, "seed " + seed + ": berths");
        for (Berth berth : port.berths()) {
            helper.assertTrue(level.getBlockState(berth.pos()).is(Blocks.WATER), "seed " + seed + ": berth " + berth.pos().toShortString()
                    + " is water");
            helper.assertTrue(level.getBlockState(berth.pos().above()).isAir(), "seed " + seed + ": berth " + berth.pos().toShortString()
                    + " is open above");
        }
        // the harbor master at the dock head's desk
        BoundingBox dock = single(helper, layout, "dock_head", seed);
        BlockPos desk = BlockPos.betweenClosedStream(dock).filter(p -> level.getBlockState(p).is(HarborDesks.HARBOR_DESK.get()))
                .map(BlockPos::immutable).findFirst().orElse(null);
        helper.assertTrue(desk != null, "seed " + seed + ": the dock head has a harbor desk");
        HarborMasterGameTests.assertPlacedAtDesk(helper, level, start.getBoundingBox(), desk, port.id());

        BoundingBox b = layout.bounds();
        BoundingBox region = new BoundingBox(b.minX() - MARGIN, Math.max(level.getMinBuildHeight(), b.minY() - MARGIN),
                b.minZ() - MARGIN, b.maxX() + MARGIN, Math.min(level.getMaxBuildHeight() - 1, b.maxY() + MARGIN), b.maxZ() + MARGIN);
        try {
            RegionSchematic.write(level, region, helper.absolutePos(BlockPos.ZERO), dir().resolve("seafarer_village_" + seed + ".schem"));
        } catch (IOException e) {
            throw new IllegalStateException("could not write the region of seed " + seed, e);
        }
        helper.succeed();
    }

    // --- helpers ----------------------------------------------------------------------------------------------

    /**
     * The village of {@code seed} planned through the real jigsaw assembler at the fixed site ({@link #PLAN_X}, the
     * shore at {@link #PLAN_SHORE_Z}, the sea to the north, the sea surface at {@code seaY}, sand one above it), with
     * the streets' (and the buildings') height from a flat generator at the sand's surface, as on a real beach.
     */
    static List<StructurePiece> planVillage(GameTestHelper helper, ServerLevel level, int seaY, long seed) {
        PortStructure village = village(level);
        BlockPos site = new BlockPos(PLAN_X, seaY, PLAN_SHORE_Z + SITE_Z - SHORE_Z);
        PortStructure.Terrain terrain = (x, z) -> z < PLAN_SHORE_Z ? seaY - 4 : seaY + 2;
        ChunkGenerator beach = StructureLayoutGameTests.beachGenerator(helper, level, seaY + 2);
        Structure.GenerationContext context = new Structure.GenerationContext(level.registryAccess(), beach, beach.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), seed, new ChunkPos(site), level, b -> true);
        Optional<Structure.GenerationStub> stub = village.plan(context, site, seaY, terrain, village.maxDepth());
        helper.assertTrue(stub.isPresent(), "seed " + seed + ": the shore gives a generation point");
        return new ArrayList<>(stub.get().getPiecesBuilder().build().pieces());
    }

    static PortStructure village(ServerLevel level) {
        Structure s = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(VillageKeys.SEAFARER_VILLAGE);
        if (!(s instanceof PortStructure village)) throw new IllegalStateException("seafarer_village is not loaded: " + s);
        return village;
    }

    /** Vanilla's beardifier over {@code pieces}, as {@code Beardifier.forStructuresInChunk} builds it. */
    static Beardifier beardOf(List<StructurePiece> pieces, TerrainAdjustment adaptation) {
        ObjectArrayList<Beardifier.Rigid> rigids = new ObjectArrayList<>();
        ObjectArrayList<JigsawJunction> junctions = new ObjectArrayList<>();
        for (StructurePiece piece : pieces) {
            if (!(piece instanceof PoolElementStructurePiece p)) continue;
            if (p.getElement().getProjection() == StructureTemplatePool.Projection.RIGID) {
                rigids.add(new Beardifier.Rigid(p.getBoundingBox(), adaptation, p.getGroundLevelDelta()));
            }
            junctions.addAll(p.getJunctions());
        }
        return new Beardifier(rigids.iterator(), junctions.iterator());
    }

    private static BoundingBox single(GameTestHelper helper, StructureLayout layout, String name, long seed) {
        List<StructureLayout.Piece> found = layout.named(name);
        helper.assertValueEqual(found.size(), 1, "seed " + seed + ": " + name + " pieces");
        return found.get(0).box();
    }

    /**
     * The test's beach: stone under the sea (rows 1..4), water up to the sea surface, sand up to one above it on the
     * land side of {@link #SHORE_Z}, a stone rim round the sea keeps the water in.
     */
    private static void buildBeach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState sand = Blocks.SAND.defaultBlockState();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                boolean sea = z < SHORE_Z;
                boolean rim = x == 0 || x == SIZE - 1 || z == 0;
                for (int y = 1; y <= SEA_Y + 1; y++) {
                    BlockState state = sea ? (y <= 4 || (rim && y <= SEA_Y) ? stone : y <= SEA_Y ? water : null) : sand;
                    if (state != null) level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Takes away the GameTest's barrier ceiling (vanilla's gravity processor would lay the streets on it). */
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
    private static void place(ServerLevel level, StructureStart start) {
        BoundingBox box = start.getBoundingBox();
        ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ()));
        ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()));
        ChunkPos.rangeClosed(min, max).forEach(chunk -> start.placeInChunk(level, level.structureManager(),
                level.getChunkSource().getGenerator(), level.getRandom(),
                new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                        chunk.getMaxBlockX(), level.getMaxBuildHeight(), chunk.getMaxBlockZ()), chunk));
    }

    static Path dir() {
        return Path.of(System.getProperty(StructureLayoutGameTests.DIR_PROPERTY, "structure-layouts"));
    }

    private static void clearDumps(String prefix) {
        if (!Files.isDirectory(dir())) return;
        try (var files = Files.list(dir())) {
            for (Path f : files.filter(f -> f.getFileName().toString().startsWith(prefix)).toList()) Files.delete(f);
        } catch (IOException e) {
            throw new IllegalStateException("could not clear " + dir(), e);
        }
    }

    private static void write(List<String> lines, String name) {
        try {
            Files.createDirectories(dir());
            Files.write(dir().resolve(name), lines);
        } catch (IOException e) {
            throw new IllegalStateException("could not write " + name, e);
        }
    }
}
