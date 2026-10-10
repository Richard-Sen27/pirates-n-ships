package com.richardsenger.piratesnships.world.structure;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.island.IslandData;
import com.richardsenger.piratesnships.world.island.IslandKeys;
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
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Natural layouts of the jigsaw structures (STR2 for the pirate island; STR3 and STR4 add the village and the outpost
 * here with the same tools): the structure is generated headless under several seeds through the real jigsaw
 * assembler ({@link PortStructure#plan}) and every instance must not be a line and must be varied.
 *
 * <ul>
 *     <li>{@link #pirateIslandLayoutsAreNatural}: six seeds on a straight shore (sea to the north), only the layout:
 *     per instance at least {@link #MIN_PIECES} pieces, a footprint aspect of at least {@link #MIN_ASPECT}, at least
 *     {@link #MIN_VARIANTS} distinct buildings of the huts pool, the jetty's far end over the water; no two instances
 *     with the same piece sequence. Each instance's pieces and boxes go to {@code layout-<seed>.txt}
 *     ({@link StructureLayout#lines}; {@code tools/structure_check.py} reads them).</li>
 *     <li>{@code pirateIslandPlaced<n>}: three of the seeds placed for real on a 160 by 160 beach (sand one above
 *     the sea, the sea to the north): the jetty's far end stands in water, the port registers with its two berths, and
 *     the region (the structure's box and 8 blocks round it) is written as a Sponge schematic
 *     {@code pirate_island_<seed>.schem} for {@code tools/render_generated_structure.py}.</li>
 * </ul>
 * The files go to {@value #DIR_PROPERTY} (default {@code structure-layouts} in the run directory).
 *
 * <p>The placed tests generate with a flat generator whose surface is the beach's (the jigsaw puts terrain-matching
 * trails and the huts hanging from them at the generator's surface height, vanilla's gravity processor then lays the
 * trails on the real blocks), so the camp lands on the sand as it would on a real beach; terrain adaptation (the
 * beard) is a noise-generator effect and does not run here.
 */
public final class StructureLayoutGameTests {

    /** System property: the folder for the layout dumps and the region schematics. */
    public static final String DIR_PROPERTY = "pirates_n_ships.layout.dir";
    /**
     * The pirate island's layout seeds; {@link #RENDER_SEEDS} of them are placed and dumped. They lie far apart:
     * {@link PortStructure#plan} draws the layout random from the first of {@code seed}, {@code seed + 1}, ... that
     * gives the shore's rotation, so neighbouring seeds can share a layout (1 and 2 did).
     */
    public static final long[] ISLAND_SEEDS = {11L, 2024L, 4242L, 31337L, 65536L, 99991L};
    public static final long[] RENDER_SEEDS = {11L, 2024L, 4242L};
    public static final int MIN_PIECES = 10;
    public static final double MIN_ASPECT = 0.5;
    public static final int MIN_VARIANTS = 4;
    /** The beach the placed tests build: sea surface row, first land row (canonical z), the camp's site. */
    static final int SEA_Y = 10;
    static final int SHORE_Z = 70;
    static final int SITE_X = 80;
    static final int SITE_Z = SHORE_Z + 9;
    static final int SIZE = 160;
    static final int HEIGHT = 40;
    /** The region dump reaches this far round the structure's box. */
    static final int MARGIN = 8;
    /**
     * Where every island layout is planned (x, first land row z): the layout depends on the site's chunk as well as
     * on the seed, so a fixed site keeps a seed's layout the same in every run, wherever the test's arena lies. The
     * placed tests move the pieces into their arena afterwards.
     */
    static final int PLAN_X = 4104;
    static final int PLAN_SHORE_Z = 4160;

    private StructureLayoutGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(StructureLayoutGameTests.class);
    }

    @ModGameTest(timeoutTicks = 200)
    public static void pirateIslandLayoutsAreNatural(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int seaY = helper.absolutePos(BlockPos.ZERO).getY() + SEA_Y;
        Set<String> buildings = IslandData.HUTS.keySet();
        Map<String, Long> sequences = new HashMap<>();
        clearDumps("layout-");
        for (long seed : ISLAND_SEEDS) {
            StructureLayout layout = StructureLayout.of(planIsland(helper, level, seaY, seed));
            BoundingBox jetty = single(helper, layout, "jetty", seed);
            int shoreZ = PLAN_SHORE_Z;
            boolean waterEnd = jetty.minZ() < shoreZ;
            write(layout.lines(IslandKeys.PIRATE_ISLAND.location().toString(), seed, buildings, waterEnd),
                    "layout-" + seed + ".txt");
            Constants.LOG.info("STR2 layout seed {}: {} pieces, {} by {}, aspect {}, variants {}", seed, layout.pieces().size(),
                    layout.bounds().getXSpan(), layout.bounds().getZSpan(), String.format(java.util.Locale.ROOT, "%.2f", layout.aspect()),
                    layout.variants(buildings));
            helper.assertTrue(layout.pieces().size() >= MIN_PIECES,
                    "seed " + seed + ": " + layout.pieces().size() + " pieces, at least " + MIN_PIECES);
            helper.assertTrue(layout.aspect() >= MIN_ASPECT, String.format(java.util.Locale.ROOT,
                    "seed %d: aspect %.2f (%d by %d), at least %.2f", seed, layout.aspect(), layout.bounds().getXSpan(),
                    layout.bounds().getZSpan(), MIN_ASPECT));
            helper.assertTrue(layout.variants(buildings).size() >= MIN_VARIANTS,
                    "seed " + seed + ": buildings " + layout.variants(buildings) + ", at least " + MIN_VARIANTS + " distinct");
            helper.assertTrue(waterEnd, "seed " + seed + ": the jetty's far end (z " + jetty.minZ() + ") is over the water (z < " + shoreZ + ")");
            Long same = sequences.put(layout.sequence(), seed);
            helper.assertTrue(same == null, "seeds " + same + " and " + seed + " give the same piece sequence");
        }
        helper.succeed();
    }

    /**
     * The beard (terrain adaptation {@code beard_thin}) levels the ground round the huts but leaves the jetty's water
     * alone: vanilla's beardifier, fed the island's pieces the way {@code Beardifier.forStructuresInChunk} feeds them,
     * adds nothing at the jetty's far end (the jetty's ground level is out of reach, {@link PortStructure#unbearded}),
     * and fills under the ground row beside a building.
     */
    @ModGameTest(timeoutTicks = 100)
    public static void pirateIslandBeardSparesTheJetty(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int seaY = helper.absolutePos(BlockPos.ZERO).getY() + SEA_Y;
        PortStructure island = island(level);
        helper.assertValueEqual(island.terrainAdaptation(), net.minecraft.world.level.levelgen.structure.TerrainAdjustment.BEARD_THIN,
                "terrain adaptation");
        List<StructurePiece> pieces = planIsland(helper, level, seaY, ISLAND_SEEDS[0]);
        var rigids = new it.unimi.dsi.fastutil.objects.ObjectArrayList<net.minecraft.world.level.levelgen.Beardifier.Rigid>();
        var junctions = new it.unimi.dsi.fastutil.objects.ObjectArrayList<net.minecraft.world.level.levelgen.structure.pools.JigsawJunction>();
        for (StructurePiece piece : pieces) {
            if (!(piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece p)) continue;
            if (p.getElement().getProjection() == net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.Projection.RIGID) {
                rigids.add(new net.minecraft.world.level.levelgen.Beardifier.Rigid(p.getBoundingBox(), island.terrainAdaptation(),
                        p.getGroundLevelDelta()));
            }
            junctions.addAll(p.getJunctions());
        }
        net.minecraft.world.level.levelgen.Beardifier beard = new net.minecraft.world.level.levelgen.Beardifier(rigids.iterator(), junctions.iterator());
        StructureLayout layout = StructureLayout.of(pieces);
        BoundingBox jetty = single(helper, layout, "jetty", ISLAND_SEEDS[0]);
        for (int y = seaY - 4; y <= seaY + 2; y++) {
            double d = beard.compute(new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(
                    (jetty.minX() + jetty.maxX()) / 2, y, jetty.minZ()));
            helper.assertTrue(Math.abs(d) < 1e-6, "no beard at the jetty's far end (y " + y + "): " + d);
        }
        StructureLayout.Piece hut = layout.pieces().stream().filter(p -> IslandData.HUTS.containsKey(p.name())
                && !p.name().startsWith("treasure")).findFirst().orElse(null);
        helper.assertTrue(hut != null, "a building on the island");
        double under = beard.compute(new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(
                hut.box().minX() - 1, hut.box().minY() - 1, hut.box().minZ() + 1));
        helper.assertTrue(under > 0.05, "the beard fills the ground beside " + hut.name() + ": " + under);
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = 400, batch = "pirates_n_ships_structure_layout")
    public static void pirateIslandPlaced1(GameTestHelper helper) {
        placeIsland(helper, RENDER_SEEDS[0]);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = 400, batch = "pirates_n_ships_structure_layout")
    public static void pirateIslandPlaced2(GameTestHelper helper) {
        placeIsland(helper, RENDER_SEEDS[1]);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = 400, batch = "pirates_n_ships_structure_layout")
    public static void pirateIslandPlaced3(GameTestHelper helper) {
        placeIsland(helper, RENDER_SEEDS[2]);
    }

    /** Builds the beach, places the island of {@code seed} on it, checks the jetty and the port, dumps the region. */
    private static void placeIsland(GameTestHelper helper, long seed) {
        ServerLevel level = helper.getLevel();
        PortStructure island = island(level);
        buildBeach(helper);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int seaY = origin.getY() + SEA_Y;
        BlockPos site = new BlockPos(origin.getX() + SITE_X, seaY, origin.getZ() + SITE_Z);
        // the seed's layout (planned at the fixed site), moved onto this arena's beach
        List<StructurePiece> pieces = planIsland(helper, level, seaY, seed);
        int dx = site.getX() - PLAN_X;
        int dz = site.getZ() - (PLAN_SHORE_Z + SITE_Z - SHORE_Z);
        pieces.forEach(piece -> piece.move(dx, 0, dz));
        StructureStart start = new StructureStart(island, new ChunkPos(site), 0, new PiecesContainer(pieces));
        StructureLayout layout = StructureLayout.of(pieces);
        clearCeiling(helper);
        place(level, start);

        // the jetty's far end stands in water: the sea surface under its seaward row holds water
        BoundingBox jetty = single(helper, layout, "jetty", seed);
        int water = 0;
        for (int x = jetty.minX(); x <= jetty.maxX(); x++) {
            BlockState s = level.getBlockState(new BlockPos(x, seaY, jetty.minZ()));
            if (s.getFluidState().isSource()) water++;
        }
        helper.assertTrue(water >= 2, "seed " + seed + ": the jetty's far end stands in water (" + water + " water columns)");
        helper.assertTrue(level.getBlockState(new BlockPos(jetty.minX(), seaY - 2, jetty.minZ())).is(Blocks.WATER),
                "seed " + seed + ": water under the jetty's far end");

        // the trails lie on the beach: their bed replaces the sand row (or is laid as boards on the sea surface)
        for (StructureLayout.Piece trail : layout.pieces()) {
            if (!trail.name().startsWith("path")) continue;
            int bed = 0;
            for (int x = trail.box().minX(); x <= trail.box().maxX(); x++) {
                for (int z = trail.box().minZ(); z <= trail.box().maxZ(); z++) {
                    for (int y : new int[] {seaY, seaY + 1}) {
                        BlockState s = level.getBlockState(new BlockPos(x, y, z));
                        if (s.is(Blocks.GRAVEL) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.SPRUCE_PLANKS)) bed++;
                    }
                }
            }
            helper.assertTrue(bed >= 3, "seed " + seed + ": " + trail.name() + " at " + trail.box() + " lies on the beach (" + bed + " bed blocks)");
        }

        Port port = PortRegistry.get(level.getServer()).index()
                .byId(PortService.portId(PortKind.PIRATE_ISLAND, PortService.centreOf(new PiecesContainer(pieces)))).orElse(null);
        helper.assertTrue(port != null, "seed " + seed + ": the port is registered");
        helper.assertValueEqual(port.berths().size(), 2, "seed " + seed + ": berths");

        BoundingBox b = layout.bounds();
        BoundingBox region = new BoundingBox(b.minX() - MARGIN, Math.max(level.getMinBuildHeight(), b.minY() - MARGIN),
                b.minZ() - MARGIN, b.maxX() + MARGIN, Math.min(level.getMaxBuildHeight() - 1, b.maxY() + MARGIN), b.maxZ() + MARGIN);
        try {
            RegionSchematic.write(level, region, helper.absolutePos(BlockPos.ZERO), dir().resolve("pirate_island_" + seed + ".schem"));
        } catch (IOException e) {
            throw new IllegalStateException("could not write the region of seed " + seed, e);
        }
        helper.succeed();
    }

    // --- helpers ----------------------------------------------------------------------------------------------

    /**
     * The island of {@code seed} planned through the real jigsaw assembler at the fixed site ({@link #PLAN_X}, the
     * shore at {@link #PLAN_SHORE_Z}, the sea to the north, the sea surface at {@code seaY}, sand one above it), with
     * the trails' (and the buildings') height from a flat generator at the sand's surface, as on a real beach: the
     * jigsaw's collision test is three-dimensional, so trails at another height would pass under the camp.
     */
    static List<StructurePiece> planIsland(GameTestHelper helper, ServerLevel level, int seaY, long seed) {
        PortStructure island = island(level);
        BlockPos site = new BlockPos(PLAN_X, seaY, PLAN_SHORE_Z + SITE_Z - SHORE_Z);
        PortStructure.Terrain terrain = (x, z) -> z < PLAN_SHORE_Z ? seaY - 4 : seaY + 2;
        Structure.GenerationContext context = context(level, beachGenerator(helper, level, seaY + 2), site, seed);
        Optional<Structure.GenerationStub> stub = island.plan(context, site, seaY, terrain, island.maxDepth());
        helper.assertTrue(stub.isPresent(), "seed " + seed + ": the shore gives a generation point");
        return new java.util.ArrayList<>(stub.get().getPiecesBuilder().build().pieces());
    }

    static PortStructure island(ServerLevel level) {
        Structure s = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(IslandKeys.PIRATE_ISLAND);
        if (!(s instanceof PortStructure island)) throw new IllegalStateException("pirate_island is not loaded: " + s);
        return island;
    }

    static Structure.GenerationContext context(ServerLevel level, ChunkGenerator generator, BlockPos site, long seed) {
        return new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), seed, new ChunkPos(site), level, b -> true);
    }

    private static BoundingBox single(GameTestHelper helper, StructureLayout layout, String name, long seed) {
        List<StructureLayout.Piece> found = layout.named(name);
        helper.assertValueEqual(found.size(), 1, "seed " + seed + ": " + name + " pieces");
        return found.get(0).box();
    }

    /**
     * The test's beach: stone under the sea (rows 1..4), water up to the sea surface, sand up to one above it; the
     * shore lies so far south (z {@value #SHORE_Z}) that every piece the jigsaw may place (within 64 of the camp)
     * stays inside the arena, and a stone rim round the sea keeps the water in.
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

    /**
     * Takes away the barrier ceiling the GameTest framework puts over a test without sky access: vanilla's gravity
     * processor lays a terrain-matching piece on the world surface heightmap, which the ceiling would be.
     */
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

    /**
     * A flat generator whose first free height is {@code firstFree} (the beach's): the level's flat settings with one
     * stone layer reaching up to it. The jigsaw reads the heights of terrain-matching pieces from it.
     */
    public static ChunkGenerator beachGenerator(GameTestHelper helper, ServerLevel level, int firstFree) {
        ChunkGenerator base = level.getChunkSource().getGenerator();
        helper.assertTrue(base instanceof FlatLevelSource, "the GameTest level is flat: " + base);
        FlatLevelGeneratorSettings settings = ((FlatLevelSource) base).settings();
        int layers = firstFree - level.getMinBuildHeight();
        return new FlatLevelSource(settings.withBiomeAndLayers(List.of(new FlatLayerInfo(layers, Blocks.STONE)),
                Optional.empty(), settings.getBiome()));
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
        return Path.of(System.getProperty(DIR_PROPERTY, "structure-layouts"));
    }

    /** Deletes the earlier runs' dumps whose names start with {@code prefix} (the folder may hold other seeds' files). */
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
