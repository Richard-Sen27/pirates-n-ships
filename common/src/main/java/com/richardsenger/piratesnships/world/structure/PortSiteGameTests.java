package com.richardsenger.piratesnships.world.structure;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.world.WorldConfig;
import com.richardsenger.piratesnships.world.island.IslandKeys;
import com.richardsenger.piratesnships.world.outpost.OutpostKeys;
import com.richardsenger.piratesnships.world.outpost.OutpostPieces;
import com.richardsenger.piratesnships.world.village.VillageKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.IntBinaryOperator;

/**
 * STR8, the sites of the ports (the human, 2026-10-12, after his first look at generated ports: "piers need to end in
 * water, not on land ... the paths are often very curly ... a structure completely on water, without proper land below
 * it ... don't create the structures right in ice biomes"). Each test plans through the real jigsaw assembler
 * ({@link PortStructure#plan}) on a built terrain that shows the fault, and checks the rule that fixes it:
 *
 * <ul>
 *     <li>{@link #piersEndInTheSea}: a sandbar under the pier's end (beyond what the shore probe sees): the best side
 *     is turned down and the next one taken; with {@code piers_end_in_water} off the pier ends on the sandbar; with the
 *     other side barred too the site is rejected;</li>
 *     <li>{@link #noHutStandsOverTheSea}: an inlet beside a camp: a hut STR2's three-of-five rule kept with a corner over
 *     the water is dropped, every land piece stands on land;</li>
 *     <li>{@link #wallRunsEndOnLand}: a headland: the outpost's wall run that ran out over the sea ends with a tower on
 *     land;</li>
 *     <li>{@link #streetsAreLevelOnBumpyGround}: a bumpy, rising shore: neighbouring street, trail and lane pieces differ
 *     by at most one block, where the ground under them differs by more;</li>
 *     <li>{@link #coldShoresTakeTheColdStructure}: on a snowy beach only the cold structure generates, on a beach only
 *     the other one.</li>
 * </ul>
 */
public final class PortSiteGameTests {

    /** The sea surface row of the plans, relative to the test's origin. */
    static final int SEA_Y = 10;
    /** Where every plan of this class is made (x, the shore's first land row z): far from every other test's site. */
    static final int PLAN_X = 12_288;
    static final int PLAN_SHORE_Z = 12_288;

    private PortSiteGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(PortSiteGameTests.class);
    }

    /** The three kinds' warm structures. */
    static final List<ResourceKey<Structure>> KINDS = List.of(VillageKeys.SEAFARER_VILLAGE, IslandKeys.PIRATE_ISLAND,
            OutpostKeys.NAVY_OUTPOST);

    // --- 1. piers end in water ----------------------------------------------------------------------------------

    @ModGameTest(timeoutTicks = 300, batch = "pirates_n_ships_config_world_piers")
    public static void piersEndInTheSea(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int seaY = seaY(helper);
        BlockPos site = new BlockPos(PLAN_X, seaY, PLAN_SHORE_Z + 8);
        // the sea to the north (first water 9 out, 14 of the probe's 24 columns) with a sandbar 20 to 21 out, under the pier's
        // run; the sea to the east from 12 out (13 columns). WG1 picks the north

        PortStructure.Terrain sandbar = (x, z) -> sea(seaY, z < PLAN_SHORE_Z && !(site.getZ() - z >= 20 && site.getZ() - z <= 21)
                || x - site.getX() >= 12);
        PortStructure.Terrain barred = (x, z) -> sea(seaY, z < PLAN_SHORE_Z && !(site.getZ() - z >= 20 && site.getZ() - z <= 21)
                || x - site.getX() >= 12 && !(x - site.getX() >= 22 && x - site.getX() <= 23));
        ChunkGenerator beach = StructureLayoutGameTests.beachGenerator(helper, level, seaY + 2);
        for (ResourceKey<Structure> key : KINDS) {
            PortStructure structure = structure(level, key);
            String kind = key.location().getPath();
            // the fault: WG1's pick without the rule, the pier ends on the sandbar
            ConfigOverrides.Handle<Boolean> off = ConfigOverrides.apply(WorldConfig.STRUCTURE_PIERS_END_IN_WATER, false);
            List<StructurePiece> before;
            try {
                before = plan(helper, level, structure, beach, site, seaY, sandbar, 7L).orElseThrow();
            } finally {
                off.restore();
            }
            helper.assertTrue(!PierExtensions.endsInWater(before, level.getStructureManager(), sandbar, seaY),
                    kind + ": without the rule the pier runs onto the sandbar");
            // the rule: the east side is taken, its pier and four blocks beyond lie over the sea
            List<StructurePiece> after = plan(helper, level, structure, beach, site, seaY, sandbar, 7L).orElse(null);
            helper.assertTrue(after != null, kind + ": the east side gives a port");
            helper.assertTrue(PierExtensions.endsInWater(after, level.getStructureManager(), sandbar, seaY),
                    kind + ": the pier ends in the sea");
            BoundingBox start = after.get(0).getBoundingBox();
            helper.assertTrue(start.maxX() >= site.getX() + 8, kind + ": the start faces east: " + start);
            // both sides barred: no port on this site
            helper.assertTrue(plan(helper, level, structure, beach, site, seaY, barred, 7L).isEmpty(),
                    kind + ": a site whose every side puts the pier on land is rejected");
        }
        helper.succeed();
    }

    // --- 4. no piece over water ---------------------------------------------------------------------------------

    @ModGameTest(timeoutTicks = 400)
    public static void noHutStandsOverTheSea(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int seaY = seaY(helper);
        PortStructure island = structure(level, IslandKeys.PIRATE_ISLAND);
        ChunkGenerator beach = StructureLayoutGameTests.beachGenerator(helper, level, seaY + 2);
        BlockPos site = new BlockPos(PLAN_X, seaY, PLAN_SHORE_Z + 9);
        PortStructure.Terrain straight = (x, z) -> sea(seaY, z < PLAN_SHORE_Z);
        int checked = 0;
        int found = 0;
        for (long seed : StructureLayoutGameTests.ISLAND_SEEDS) {
            List<StructurePiece> all = plan(helper, level, island, beach, site, seaY, straight, seed).orElseThrow();
            List<LandFilter.Node> nodes = PortStructure.landNodes(all);
            // an inlet (5 wide, 60 deep) somewhere beside the camp, at least 20 blocks off the site so the shore stays
            for (int ix = -60; ix <= 60 && found == checked; ix += 2) {
                if (Math.abs(ix) < 20) continue;
                int cx = PLAN_X + ix;
                LandFilter.Water inlet = (x, z) -> z < PLAN_SHORE_Z || Math.abs(x - cx) <= 2 && z < PLAN_SHORE_Z + 60;
                for (int i = 1; i < all.size(); i++) {
                    LandFilter.Node node = nodes.get(i);
                    if (!node.building()) continue;
                    boolean str2 = LandFilter.wetSamples(node.box(), inlet) < 3;
                    if (!str2 || LandFilter.onLand(node, inlet)) continue;
                    // the fault: STR2's rule kept this building with a corner over the water; the plan drops it now
                    BoundingBox hut = node.box();
                    PortStructure.Terrain terrain = (x, z) -> sea(seaY, inlet.at(x, z));
                    List<StructurePiece> kept = plan(helper, level, island, beach, site, seaY, terrain, seed).orElseThrow();
                    helper.assertTrue(kept.stream().noneMatch(p -> p.getBoundingBox().equals(hut)),
                            "seed " + seed + ", inlet at " + ix + ": the building at " + hut + " half over the water is dropped");
                    List<LandFilter.Node> keptNodes = PortStructure.landNodes(kept);
                    for (int k = 0; k < kept.size(); k++) {
                        LandFilter.Node n = keptNodes.get(k);
                        if (n.land()) {
                            helper.assertTrue(LandFilter.onLand(n, inlet), "seed " + seed + ": the land piece at " + n.box() + " stands on land");
                        }
                    }
                    found++;
                    break;
                }
            }
            checked++;
            if (found >= 2) break;
        }
        helper.assertTrue(found >= 1, "an inlet beside a camp puts a building half over the water under STR2's rule");
        helper.succeed();
    }

    // --- addendum: wall runs end on land ------------------------------------------------------------------------

    @ModGameTest(timeoutTicks = 400)
    public static void wallRunsEndOnLand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int seaY = seaY(helper);
        PortStructure outpost = structure(level, OutpostKeys.NAVY_OUTPOST);
        ChunkGenerator beach = StructureLayoutGameTests.beachGenerator(helper, level, seaY + 2);
        BlockPos site = new BlockPos(PLAN_X, seaY, PLAN_SHORE_Z + 9);
        PortStructure.Terrain straight = (x, z) -> sea(seaY, z < PLAN_SHORE_Z);
        var templates = level.getStructureManager();
        int closed = 0;
        for (long seed : OutpostLayoutGameTests.SEEDS) {
            List<StructurePiece> all = plan(helper, level, outpost, beach, site, seaY, straight, seed).orElseThrow();
            List<LandFilter.Node> nodes = PortStructure.landNodes(all);
            // the wall farthest east: the headland ends three blocks before its far end
            int east = Integer.MIN_VALUE;
            for (int i = 1; i < all.size(); i++) {
                if (!OutpostPieces.isWall(name(all.get(i)))) continue;
                BlockPos far = WallRuns.farEnd(all, nodes, i, templates);
                if (far != null) east = Math.max(east, far.getX());
            }
            if (east - site.getX() < 24) continue;
            int headland = east - 3;
            LandFilter.Water water = (x, z) -> z < PLAN_SHORE_Z || x > headland;
            PortStructure.Terrain terrain = (x, z) -> sea(seaY, water.at(x, z));
            List<StructurePiece> kept = plan(helper, level, outpost, beach, site, seaY, terrain, seed).orElseThrow();
            List<LandFilter.Node> keptNodes = PortStructure.landNodes(kept);
            for (int i = 1; i < kept.size(); i++) {
                if (!OutpostPieces.isWall(name(kept.get(i)))) continue;
                BlockPos far = WallRuns.farEnd(kept, keptNodes, i, templates);
                helper.assertTrue(far == null || !water.at(far.getX(), far.getZ()),
                        "seed " + seed + ": the wall " + name(kept.get(i)) + " ends at " + far + " over the sea");
            }
            OutpostLayoutGameTests.Runs runs = OutpostLayoutGameTests.runs(kept);
            helper.assertValueEqual(runs.runs().size(), 2, "seed " + seed + ": wall runs from the gate");
            for (int r = 0; r < 2; r++) {
                List<String> names = runs.names(r);
                helper.assertTrue(OutpostPieces.isTower(names.get(names.size() - 1)), "seed " + seed + ": the run " + names + " ends in a tower");
            }
            Constants.LOG.info("STR8 headland seed {}: runs {} / {}", seed, runs.names(0), runs.names(1));
            closed++;
        }
        helper.assertTrue(closed >= 2, "at least two seeds run a wall far enough east (" + closed + ")");
        helper.succeed();
    }

    // --- 2. level streets ---------------------------------------------------------------------------------------

    /**
     * The surface (topmost solid or water row) of the bumpy shore in plan coordinates: the sea north of
     * {@link #PLAN_SHORE_Z}, then land one above the sea surface rising one block every ten inland, with bumps of up to
     * three blocks.
     */
    static int bumpySurface(int seaY, int x, int z) {
        if (z < PLAN_SHORE_Z) return seaY;
        int bump = (int) Math.round(Math.sin(x / 4.0) + 1 + 0.5 * (Math.cos(z / 5.0) + 1));
        return seaY + 1 + bump + (z - PLAN_SHORE_Z) / 10;
    }

    @ModGameTest(timeoutTicks = 400)
    public static void streetsAreLevelOnBumpyGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int seaY = seaY(helper);
        ChunkGenerator bumpy = bumpyGenerator(level, (x, z) -> bumpySurface(seaY, x, z) + 1);
        PortStructure.Terrain terrain = (x, z) -> z < PLAN_SHORE_Z ? seaY - 4 : bumpySurface(seaY, x, z) + 1;
        BlockPos site = new BlockPos(PLAN_X, seaY, PLAN_SHORE_Z + 9);
        int pairs = 0;
        int rough = 0;
        for (ResourceKey<Structure> key : KINDS) {
            PortStructure structure = structure(level, key);
            for (long seed : new long[] {11L, 2024L, 4242L}) {
                List<StructurePiece> pieces = plan(helper, level, structure, bumpy, site, seaY, terrain, seed).orElse(null);
                if (pieces == null) continue;
                List<LandFilter.Node> nodes = PortStructure.landNodes(pieces);
                int steps = 0;
                int trails = 0;
                for (int i = 1; i < pieces.size(); i++) {
                    if (!isTrail(pieces.get(i))) continue;
                    trails++;
                    int parent = nodes.get(i).parent();
                    int bed = pieces.get(i).getBoundingBox().minY() - 1;
                    int parentRow;
                    if (parent == 0) parentRow = pieces.get(0).getBoundingBox().minY();
                    else if (parent > 0 && isTrail(pieces.get(parent))) parentRow = pieces.get(parent).getBoundingBox().minY() - 1;
                    else continue;
                    int step = Math.abs(bed - parentRow);
                    helper.assertTrue(step <= TrailLevels.MAX_STEP, key.location().getPath() + " seed " + seed + ": "
                            + name(pieces.get(i)) + " steps " + step + " from its parent");
                    if (step > 0) steps++;
                    pairs++;
                    // the fault: vanilla lays the piece block by block on this ground, which differs by more than a block
                    BoundingBox box = pieces.get(i).getBoundingBox();
                    int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
                    for (int x = box.minX(); x <= box.maxX(); x++) {
                        for (int z = box.minZ(); z <= box.maxZ(); z++) {
                            int s = bumpySurface(seaY, x, z);
                            min = Math.min(min, s);
                            max = Math.max(max, s);
                        }
                    }
                    if (max - min > 1) rough++;
                }
                Constants.LOG.info("STR8 levels {} seed {}: {} pieces, {} trail pieces, {} steps", key.location().getPath(), seed,
                        pieces.size(), trails, steps);
            }
        }
        helper.assertTrue(pairs >= 10, "trail pieces checked: " + pairs);
        helper.assertTrue(rough >= 5, "trail pieces on ground that differs by more than a block: " + rough);
        helper.succeed();
    }

    // --- STR9: ports keep their size on rough shores ------------------------------------------------------------

    /** The share of the flat beach's pieces a port keeps on the rough shore (STR9). */
    static final double ROUGH_SHARE = 0.8;

    /** The seeds the rough-shore size tests plan with (the layout tests' seeds). */
    static final long[] ROUGH_SEEDS = {7L, 11L, 2024L, 4242L, 5150L};

    /**
     * The first land row of the rough shore at column x: straight within 14 blocks of the site (so the pier's side is
     * the same as on the flat beach), elsewhere a coast that wiggles five blocks in and out.
     */
    static int roughCoast(int x) {
        return Math.abs(x - PLAN_X) < 14 ? PLAN_SHORE_Z : PLAN_SHORE_Z + (int) Math.round(5 * Math.sin((x - PLAN_X) / 7.0));
    }

    /**
     * The surface of a rough shore (STR9: the real coasts where STR8 kept a third to a half of a port): a wiggling coast
     * ({@link #roughCoast}) with a pond, land rising one block every six inland with bumps of up to three blocks.
     */
    static int roughSurface(int seaY, int x, int z) {
        if (roughWater(x, z)) return seaY;
        int bump = (int) Math.round(1.5 * (Math.sin(x / 3.0) + Math.cos(z / 4.0)) / 2 + 1.5);
        return seaY + 1 + bump + Math.max(0, z - PLAN_SHORE_Z) / 6;
    }

    /** The rough shore's water: the sea north of its coast and a round pond 30 blocks inland, 24 to the east. */
    static boolean roughWater(int x, int z) {
        if (z < roughCoast(x)) return true;
        int px = x - (PLAN_X + 24);
        int pz = z - (PLAN_SHORE_Z + 30);
        return px * px + pz * pz <= 16;
    }

    @ModGameTest(timeoutTicks = 600)
    public static void villageKeepsItsSizeOnARoughShore(GameTestHelper helper) {
        keepsItsSizeOnARoughShore(helper, VillageKeys.SEAFARER_VILLAGE);
    }

    @ModGameTest(timeoutTicks = 600)
    public static void islandKeepsItsSizeOnARoughShore(GameTestHelper helper) {
        keepsItsSizeOnARoughShore(helper, IslandKeys.PIRATE_ISLAND);
    }

    @ModGameTest(timeoutTicks = 600)
    public static void outpostKeepsItsSizeOnARoughShore(GameTestHelper helper) {
        keepsItsSizeOnARoughShore(helper, OutpostKeys.NAVY_OUTPOST);
    }

    /**
     * STR9: the port planned on the flat beach and on the rough shore with the same seeds keeps at least
     * {@value #ROUGH_SHARE} of the flat beach's pieces (summed over {@link #ROUGH_SEEDS}); with STR8's rules alone it
     * kept far fewer (logged, the {@code STR9 rough} lines).
     */
    private static void keepsItsSizeOnARoughShore(GameTestHelper helper, ResourceKey<Structure> key) {
        ServerLevel level = helper.getLevel();
        int seaY = seaY(helper);
        PortStructure structure = structure(level, key);
        BlockPos site = new BlockPos(PLAN_X, seaY, PLAN_SHORE_Z + 9);
        ChunkGenerator beach = StructureLayoutGameTests.beachGenerator(helper, level, seaY + 2);
        PortStructure.Terrain straight = (x, z) -> sea(seaY, z < PLAN_SHORE_Z);
        ChunkGenerator rough = bumpyGenerator(level, (x, z) -> roughSurface(seaY, x, z) + 1);
        PortStructure.Terrain roughTerrain = (x, z) -> roughWater(x, z) ? seaY - 4 : roughSurface(seaY, x, z) + 1;
        int flat = 0;
        int kept = 0;
        int planned = 0;
        int str8 = 0;
        StringBuilder seeds = new StringBuilder();
        for (long seed : ROUGH_SEEDS) {
            List<StructurePiece> onFlat = plan(helper, level, structure, beach, site, seaY, straight, seed).orElse(null);
            List<StructurePiece> onRough = plan(helper, level, structure, rough, site, seaY, roughTerrain, seed).orElse(null);
            helper.assertTrue(onFlat != null && onRough != null, key.location().getPath() + " seed " + seed + ": both shores give a port");
            PlanReport report = PlanReport.latest(structure.portKind()).orElseThrow();
            // STR8's rules alone, for the log (synchronous: the override is restored before any other test runs)
            ConfigOverrides.Handle<Boolean> off = ConfigOverrides.apply(WorldConfig.STRUCTURE_RESITE_PIECES, false);
            try {
                str8 += plan(helper, level, structure, rough, site, seaY, roughTerrain, seed).map(List::size).orElse(0);
            } finally {
                off.restore();
            }
            flat += onFlat.size();
            kept += onRough.size();
            planned += report.planned;
            seeds.append(" ").append(seed).append(": ").append(onRough.size()).append("/").append(onFlat.size());
            Constants.LOG.info("STR9 rough {} seed {}: flat {}, rough {}; {}", key.location().getPath(), seed, onFlat.size(), onRough.size(),
                    report.line());
        }
        Constants.LOG.info("STR9 rough {}: flat {} pieces, rough {} ({} planned, {} with STR8's rules alone){}", key.location().getPath(),
                flat, kept, planned, str8, seeds);
        helper.assertTrue(kept >= ROUGH_SHARE * flat, key.location().getPath() + ": the rough shore keeps " + kept + " of the flat beach's "
                + flat + " pieces (at least " + ROUGH_SHARE + ");" + seeds);
        helper.succeed();
    }

    // --- 5. cold shores -----------------------------------------------------------------------------------------

    @ModGameTest(timeoutTicks = 300)
    public static void coldShoresTakeTheColdStructure(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int seaY = seaY(helper);
        BlockPos site = new BlockPos(PLAN_X, seaY, PLAN_SHORE_Z + 9);
        PortStructure.Terrain straight = (x, z) -> sea(seaY, z < PLAN_SHORE_Z);
        ChunkGenerator beach = biomeGenerator(level, seaY + 2, Biomes.BEACH);
        ChunkGenerator snowy = biomeGenerator(level, seaY + 2, Biomes.SNOWY_BEACH);
        List<ResourceKey<Structure>> colds = List.of(VillageKeys.SEAFARER_VILLAGE_COLD, IslandKeys.PIRATE_ISLAND_COLD,
                OutpostKeys.NAVY_OUTPOST_COLD);
        for (int k = 0; k < KINDS.size(); k++) {
            PortStructure warm = structure(level, KINDS.get(k));
            PortStructure cold = structure(level, colds.get(k));
            String kind = KINDS.get(k).location().getPath();
            helper.assertTrue(!warm.cold() && cold.cold(), kind + ": one warm, one cold");
            helper.assertValueEqual(warm.coldBiomes(), Optional.of(PortStructureData.COLD_SHORES), kind + ": the cold biomes");
            helper.assertTrue(plan(helper, level, warm, beach, site, seaY, straight, 11L).isPresent(), kind + " on a beach");
            helper.assertTrue(plan(helper, level, cold, beach, site, seaY, straight, 11L).isEmpty(), kind + "_cold not on a beach");
            helper.assertTrue(plan(helper, level, warm, snowy, site, seaY, straight, 11L).isEmpty(), kind + " not on a snowy beach");
            helper.assertTrue(plan(helper, level, cold, snowy, site, seaY, straight, 11L).isPresent(), kind + "_cold on a snowy beach");
        }
        ConfigValue<Double> coldFrequency = WorldConfig.SEAFARER_VILLAGE_COLD_FREQUENCY;
        helper.assertValueEqual(coldFrequency.defaultValue(), 0.1, "cold_frequency default");
        helper.succeed();
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    static int seaY(GameTestHelper helper) {
        return helper.absolutePos(BlockPos.ZERO).getY() + SEA_Y;
    }

    /** A terrain column's first free row: the sea floor four under the surface, or land one above it. */
    static int sea(int seaY, boolean sea) {
        return sea ? seaY - 4 : seaY + 2;
    }

    static PortStructure structure(ServerLevel level, ResourceKey<Structure> key) {
        Structure s = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(key);
        if (!(s instanceof PortStructure port)) throw new IllegalStateException(key.location() + " is not loaded: " + s);
        return port;
    }

    /** The pieces {@link PortStructure#plan} gives at {@code site} with the level seed {@code seed}, or empty. */
    static Optional<List<StructurePiece>> plan(GameTestHelper helper, ServerLevel level, PortStructure structure, ChunkGenerator generator,
                                               BlockPos site, int seaY, PortStructure.Terrain terrain, long seed) {
        Structure.GenerationContext context = new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), seed, new ChunkPos(site), level, b -> true);
        return structure.plan(context, site, seaY, terrain, structure.maxDepth())
                .map(stub -> new ArrayList<>(stub.getPiecesBuilder().build().pieces()));
    }

    static boolean isTrail(StructurePiece piece) {
        return piece instanceof PoolElementStructurePiece p && p.getElement().getProjection() == StructureTemplatePool.Projection.TERRAIN_MATCHING;
    }

    static String name(StructurePiece piece) {
        return piece instanceof PoolElementStructurePiece p ? StructureLayout.templateName(p.getElement().toString()) : "?";
    }

    /** A flat generator whose first free height is {@code firstFree} and whose every biome is {@code biome}. */
    static ChunkGenerator biomeGenerator(ServerLevel level, int firstFree, ResourceKey<Biome> biome) {
        FlatLevelGeneratorSettings settings = ((FlatLevelSource) level.getChunkSource().getGenerator()).settings();
        Holder<Biome> holder = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biome);
        return new FlatLevelSource(settings.withBiomeAndLayers(List.of(new FlatLayerInfo(firstFree - level.getMinBuildHeight(), Blocks.STONE)),
                Optional.empty(), holder));
    }

    /** A flat generator whose first free height in every column is {@code firstFree(x, z)} (STR8's bumpy shores). */
    static ChunkGenerator bumpyGenerator(ServerLevel level, IntBinaryOperator firstFree) {
        FlatLevelGeneratorSettings settings = ((FlatLevelSource) level.getChunkSource().getGenerator()).settings();
        return new Bumpy(settings, firstFree);
    }

    /** {@link FlatLevelSource} with column heights from a function: what the jigsaw and the levelling read. */
    static final class Bumpy extends FlatLevelSource {
        private final IntBinaryOperator firstFree;

        Bumpy(FlatLevelGeneratorSettings settings, IntBinaryOperator firstFree) {
            super(settings);
            this.firstFree = firstFree;
        }

        @Override
        public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
            return firstFree.applyAsInt(x, z);
        }
    }
}
