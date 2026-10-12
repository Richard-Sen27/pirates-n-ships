package com.richardsenger.piratesnships.world.structure;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.world.WorldConfig;
import com.richardsenger.piratesnships.world.island.IslandData;
import com.richardsenger.piratesnships.world.island.IslandKeys;
import com.richardsenger.piratesnships.world.outpost.OutpostData;
import com.richardsenger.piratesnships.world.outpost.OutpostKeys;
import com.richardsenger.piratesnships.world.village.VillageData;
import com.richardsenger.piratesnships.world.village.VillageKeys;
import com.richardsenger.piratesnships.world.wreck.WreckData;
import com.richardsenger.piratesnships.world.wreck.WreckKeys;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * The configured spread placement in a running server (WG5): the four structure sets load with
 * {@code pirates_n_ships:configured_spread} and read the loaded server config, and a {@code /locate}-style search finds
 * a seafarer village and a navy outpost. The GameTest world is flat (plains only), where our structures never start,
 * so the search runs on a structure state built over a beach biome source, through the same
 * {@link ChunkGeneratorStructureState} and placement calls as vanilla's {@code ChunkGenerator#findNearestMapStructure}
 * (step by {@code spacing()}, ask {@code getPotentialStructureChunk}, check {@code isStructureChunk}); the terrain
 * check that follows in vanilla is the structures' own and tested in {@code WorldGameTests} and its siblings.
 */
public final class SpreadPlacementGameTests {

    private record Expected(ResourceKey<StructureSet> set, String config, int salt, ConfigValue<Integer> spacing,
                            ConfigValue<Integer> separation) {
    }

    private static final List<Expected> SETS = List.of(
            new Expected(VillageKeys.SEAFARER_VILLAGES, "seafarer_village", VillageData.SALT,
                    WorldConfig.SEAFARER_VILLAGE.spacing(), WorldConfig.SEAFARER_VILLAGE_SEPARATION),
            new Expected(IslandKeys.PIRATE_ISLANDS, "pirate_island", IslandData.SALT,
                    WorldConfig.PIRATE_ISLAND.spacing(), WorldConfig.PIRATE_ISLAND_SEPARATION),
            new Expected(OutpostKeys.NAVY_OUTPOSTS, "navy_outpost", OutpostData.SALT,
                    WorldConfig.NAVY_OUTPOST.spacing(), WorldConfig.NAVY_OUTPOST_SEPARATION),
            new Expected(WreckKeys.WRECKS, "wreck", WreckData.SALT, WorldConfig.WRECK.spacing(), WorldConfig.WRECK_SEPARATION));

    /** How many rings of grid cells the search walks (vanilla's {@code /locate} walks 100). */
    private static final int RINGS = 100;

    private SpreadPlacementGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(SpreadPlacementGameTests.class);
    }

    /** All four structure sets carry the configured spread with their config name, salt and defaults. */
    @ModGameTest
    public static void structureSetsLoadWithTheConfiguredSpread(GameTestHelper helper) {
        helper.assertValueEqual(BuiltInRegistries.STRUCTURE_PLACEMENT.getKey(PortStructures.CONFIGURED_SPREAD.get()),
                Constants.id("configured_spread"), "placement type id");
        Registry<StructureSet> sets = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        for (Expected e : SETS) {
            StructureSet set = sets.get(e.set());
            helper.assertTrue(set != null, e.set().location() + " is loaded");
            helper.assertTrue(set.placement() instanceof ConfiguredSpreadPlacement, e.set().location() + " uses configured_spread: "
                    + set.placement().getClass().getSimpleName());
            ConfiguredSpreadPlacement p = (ConfiguredSpreadPlacement) set.placement();
            helper.assertValueEqual(p.type(), PortStructures.CONFIGURED_SPREAD.get(), e.set().location() + " type");
            helper.assertValueEqual(p.config(), e.config(), e.set().location() + " config name");
            helper.assertValueEqual(p.salt(), e.salt(), e.set().location() + " salt");
            helper.assertValueEqual(p.jsonSpacing(), e.spacing().defaultValue(), e.set().location() + " JSON spacing");
            helper.assertValueEqual(p.jsonSeparation(), e.separation().defaultValue(), e.set().location() + " JSON separation");
            helper.assertTrue(e.spacing().isLive(), e.spacing() + " is loaded");
            helper.assertValueEqual(p.spacing(), e.spacing().get(), e.set().location() + " spacing from the config");
            helper.assertValueEqual(p.separation(), Math.min(e.separation().get(), e.spacing().get() - 1),
                    e.set().location() + " separation from the config");
        }
        helper.succeed();
    }

    /** A {@code /locate}-style search finds a seafarer village and a navy outpost near the world origin. */
    @ModGameTest
    public static void locateFindsAVillageAndAnOutpost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkGeneratorStructureState state = beachState(level);
        for (ResourceKey<Structure> key : List.of(VillageKeys.SEAFARER_VILLAGE, OutpostKeys.NAVY_OUTPOST)) {
            ConfiguredSpreadPlacement placement = placementOf(helper, state, key);
            ChunkPos found = locate(state, placement, new ChunkPos(0, 0));
            helper.assertTrue(found != null, key.location() + " found within " + RINGS + " rings");
            int reach = 2 * placement.spacing();
            helper.assertTrue(Math.abs(found.x) <= reach && Math.abs(found.z) <= reach,
                    key.location() + " found at chunk " + found + ", within two grid cells of the origin");
        }
        helper.succeed();
    }

    /** A changed spacing in the server config moves the grid the search walks (own batch: it writes the config). */
    @ModGameTest(batch = "pirates_n_ships_config_world_spread")
    public static void aChangedSpacingMovesTheVillageGrid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkGeneratorStructureState state = beachState(level);
        ConfiguredSpreadPlacement placement = placementOf(helper, state, VillageKeys.SEAFARER_VILLAGE);
        long seed = state.getLevelSeed();
        ConfigOverrides.during(helper, WorldConfig.SEAFARER_VILLAGE.spacing(), 12);
        ConfigOverrides.during(helper, WorldConfig.SEAFARER_VILLAGE_SEPARATION, 4);
        helper.assertValueEqual(placement.spacing(), 12, "spacing from the changed config");
        helper.assertValueEqual(placement.separation(), 4, "separation from the changed config");
        RandomSpreadStructurePlacement vanilla = new RandomSpreadStructurePlacement(12, 4,
                placement.spreadType(), placement.salt());
        for (int x = -60; x <= 60; x += 5) {
            for (int z = -60; z <= 60; z += 7) {
                helper.assertValueEqual(placement.getPotentialStructureChunk(seed, x, z), vanilla.getPotentialStructureChunk(seed, x, z),
                        "chunk of region " + x + "," + z);
            }
        }
        ChunkPos found = locate(state, placement, new ChunkPos(0, 0));
        helper.assertTrue(found != null && Math.abs(found.x) <= 24 && Math.abs(found.z) <= 24,
                "with spacing 12 a village start lies within two cells: " + found);
        helper.succeed();
    }

    /**
     * STR6: the outposts keep {@link OutpostData#VILLAGE_EXCLUSION_CHUNKS} chunks away from the seafarer villages, the
     * pirate islands {@link IslandData#PORT_EXCLUSION_CHUNKS} from both. Over a stretch of the beach world every chunk
     * where the outpost (island) placement would start lies outside the zone round every village (and outpost) start, and
     * the zones do exclude some of the outposts' (islands') own grid chunks, which they would take without them.
     */
    @ModGameTest(timeoutTicks = 200)
    public static void outpostsAndIslandsKeepAwayFromVillages(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkGeneratorStructureState state = beachState(level);
        ConfiguredSpreadPlacement villages = placementOf(helper, state, VillageKeys.SEAFARER_VILLAGE);
        ConfiguredSpreadPlacement outposts = placementOf(helper, state, OutpostKeys.NAVY_OUTPOST);
        ConfiguredSpreadPlacement islands = placementOf(helper, state, IslandKeys.PIRATE_ISLAND);
        helper.assertTrue(villages.exclusionZone().isEmpty() && villages.exclusionZones().isEmpty(), "villages exclude nothing");
        helper.assertTrue(outposts.exclusionZone().map(z -> z.otherSet().is(VillageKeys.SEAFARER_VILLAGES)
                && z.chunkCount() == OutpostData.VILLAGE_EXCLUSION_CHUNKS).orElse(false), "outposts: " + outposts.exclusionZone());
        helper.assertTrue(islands.exclusionZone().map(z -> z.otherSet().is(VillageKeys.SEAFARER_VILLAGES)
                && z.chunkCount() == IslandData.PORT_EXCLUSION_CHUNKS).orElse(false), "islands vs villages: " + islands.exclusionZone());
        helper.assertTrue(islands.exclusionZones().size() == 1 && islands.exclusionZones().get(0).otherSet().is(OutpostKeys.NAVY_OUTPOSTS)
                && islands.exclusionZones().get(0).chunkCount() == IslandData.PORT_EXCLUSION_CHUNKS, "islands vs outposts: " + islands.exclusionZones());

        long seed = state.getLevelSeed();
        int reach = 400;                       // chunks either side of the origin
        java.util.List<ChunkPos> villageStarts = starts(state, villages, reach);
        java.util.List<ChunkPos> outpostStarts = starts(state, outposts, reach);
        java.util.List<ChunkPos> islandStarts = starts(state, islands, reach);
        helper.assertTrue(villageStarts.size() > 100 && outpostStarts.size() > 30 && islandStarts.size() > 15,
                "starts: " + villageStarts.size() + " villages, " + outpostStarts.size() + " outposts, " + islandStarts.size() + " islands");
        assertApart(helper, "outpost", outpostStarts, "village", villageStarts, OutpostData.VILLAGE_EXCLUSION_CHUNKS);
        assertApart(helper, "island", islandStarts, "village", villageStarts, IslandData.PORT_EXCLUSION_CHUNKS);
        assertApart(helper, "island", islandStarts, "outpost", outpostStarts, IslandData.PORT_EXCLUSION_CHUNKS);
        // the zones act: some of the outposts' grid chunks (frequency 1 in the set) are dropped next to a village
        int dropped = 0;
        for (int x = -reach; x <= reach; x += outposts.spacing()) {
            for (int z = -reach; z <= reach; z += outposts.spacing()) {
                ChunkPos p = outposts.getPotentialStructureChunk(seed, x, z);
                if (!outposts.isStructureChunk(state, p.x, p.z)) dropped++;
            }
        }
        helper.assertTrue(dropped > 0, "the exclusion zone drops some outpost grid chunks");
        helper.succeed();
    }

    /** Every chunk in the square of {@code reach} chunks round the origin where {@code placement} starts its structure. */
    private static java.util.List<ChunkPos> starts(ChunkGeneratorStructureState state, ConfiguredSpreadPlacement placement, int reach) {
        java.util.List<ChunkPos> out = new java.util.ArrayList<>();
        int spacing = placement.spacing();
        for (int x = Math.floorDiv(-reach, spacing) * spacing; x <= reach; x += spacing) {
            for (int z = Math.floorDiv(-reach, spacing) * spacing; z <= reach; z += spacing) {
                ChunkPos p = placement.getPotentialStructureChunk(state.getLevelSeed(), x, z);
                if (placement.isStructureChunk(state, p.x, p.z)) out.add(p);
            }
        }
        return out;
    }

    /** No start of {@code these} within {@code chunks} chunks (a square) of a start of {@code others}. */
    private static void assertApart(GameTestHelper helper, String these, java.util.List<ChunkPos> starts, String others,
                                    java.util.List<ChunkPos> otherStarts, int chunks) {
        for (ChunkPos a : starts) {
            for (ChunkPos b : otherStarts) {
                helper.assertTrue(Math.abs(a.x - b.x) > chunks || Math.abs(a.z - b.z) > chunks,
                        these + " at chunk " + a + " lies within " + chunks + " chunks of the " + others + " at " + b);
            }
        }
    }

    /** The structure state of a world whose every biome is a beach (villages and outposts may start anywhere). */
    private static ChunkGeneratorStructureState beachState(ServerLevel level) {
        Holder<Biome> beach = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.BEACH);
        return ChunkGeneratorStructureState.createForNormal(level.getChunkSource().randomState(), level.getSeed(),
                new FixedBiomeSource(beach), level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).asLookup());
    }

    private static ConfiguredSpreadPlacement placementOf(GameTestHelper helper, ChunkGeneratorStructureState state,
                                                         ResourceKey<Structure> key) {
        Holder<Structure> structure = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(key);
        List<StructurePlacement> placements = state.getPlacementsForStructure(structure);
        helper.assertValueEqual(placements.size(), 1, key.location() + " placements");
        helper.assertTrue(placements.get(0) instanceof ConfiguredSpreadPlacement, key.location() + " is placed by configured_spread");
        return (ConfiguredSpreadPlacement) placements.get(0);
    }

    /**
     * The nearest chunk where the placement starts the structure, walking rings of grid cells around {@code origin} as
     * vanilla's {@code ChunkGenerator#getNearestGeneratedStructure} does (without its terrain check).
     */
    private static @Nullable ChunkPos locate(ChunkGeneratorStructureState state, RandomSpreadStructurePlacement placement,
                                             ChunkPos origin) {
        int spacing = placement.spacing();
        for (int ring = 0; ring <= RINGS; ring++) {
            ChunkPos best = null;
            long bestDistance = Long.MAX_VALUE;
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.abs(dx) != ring && Math.abs(dz) != ring) continue;
                    ChunkPos candidate = placement.getPotentialStructureChunk(state.getLevelSeed(),
                            origin.x + spacing * dx, origin.z + spacing * dz);
                    if (!placement.isStructureChunk(state, candidate.x, candidate.z)) continue;
                    long d = (long) (candidate.x - origin.x) * (candidate.x - origin.x) + (long) (candidate.z - origin.z) * (candidate.z - origin.z);
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = candidate;
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }
}
