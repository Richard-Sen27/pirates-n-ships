package com.richardsenger.piratesnships.world.structure;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.WorldConfig;
import com.richardsenger.piratesnships.world.island.IslandKeys;
import com.richardsenger.piratesnships.world.outpost.OutpostKeys;
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
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * STR9 on real coastlines (the human, 2026-10-13: "i regenerated the same seed ... they are A LOT smaller now"): the
 * port sites the dedicated-server smoke test's {@code /locate} planned on its seed (2718281828), planned again here
 * through {@link PortStructure#plan} on vanilla's overworld noise with that seed, exactly as
 * {@code findGenerationPoint} does (the chunk's middle, the ocean-floor heights; the frequency roll aside). Each plan's
 * {@link PlanReport} is logged; the test asserts that every kind keeps most of what its jigsaw planned (STR8 kept 33–51
 * percent of the pieces at these sites, the rules that prune most being the land samples of a trail near the start and
 * everything hanging from it).
 */
public final class RealCoastGameTests {

    /** The smoke test's seed ({@code tools/server_smoke.sh}, {@code SMOKE_SEED}). */
    static final long SEED = 2_718_281_828L;

    /** Sites (the chunk middles) per kind, from the smoke run's {@code STR9:} lines after STR8. */
    static final Map<PortKind, int[][]> SITES = new EnumMap<>(PortKind.class);

    static {
        SITES.put(PortKind.SEAFARER_VILLAGE, RealCoastSites.VILLAGES);
        SITES.put(PortKind.PIRATE_ISLAND, RealCoastSites.ISLANDS);
        SITES.put(PortKind.NAVY_OUTPOST, RealCoastSites.OUTPOSTS);
    }

    /** The share of the planned pieces every kind keeps over its sites (STR8: 0.33 to 0.51). */
    static final double MIN_KEPT = 0.7;

    private RealCoastGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(RealCoastGameTests.class);
    }

    @ModGameTest(timeoutTicks = 6000, batch = "pirates_n_ships_config_world_resite")
    public static void smokeSeedPortsKeepTheirPieces(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // STR8's rules alone (resite_pieces off), then STR9's: the same jigsaw on the same coasts
        Map<PortKind, PlanReport> str8;
        ConfigOverrides.Handle<Boolean> off = ConfigOverrides.apply(WorldConfig.STRUCTURE_RESITE_PIECES, false);
        try {
            str8 = measure(level, "STR8");
        } finally {
            off.restore();
        }
        Map<PortKind, PlanReport> str9 = measure(level, "STR9");
        StringBuilder failures = new StringBuilder();
        for (PortKind kind : SITES.keySet()) {
            PlanReport before = str8.get(kind);
            PlanReport after = str9.get(kind);
            double share = after.planned == 0 ? 0 : (double) after.kept / after.planned;
            double was = before.planned == 0 ? 0 : (double) before.kept / before.planned;
            Constants.LOG.info("STR9 real coast {}: kept {} of {} ({}) after STR8, {} of {} ({}) after STR9", kind.getSerializedName(),
                    before.kept, before.planned, String.format("%.2f", was), after.kept, after.planned, String.format("%.2f", share));
            if (after.x < SITES.get(kind).length / 2) {
                failures.append(kind.getSerializedName()).append(": only ").append(after.x).append(" sites plan; ");
            }
            if (share < MIN_KEPT) failures.append(kind.getSerializedName()).append(" keeps ").append(String.format("%.2f", share)).append("; ");
            if (after.kept < before.kept) failures.append(kind.getSerializedName()).append(" keeps less than STR8; ");
        }
        helper.assertTrue(failures.isEmpty(), failures.toString());
        helper.succeed();
    }

    /** Every site of every kind planned on the seed's noise; per kind the sum of the reports ({@code x}: the sites that plan). */
    static Map<PortKind, PlanReport> measure(ServerLevel level, String label) {
        var registries = level.registryAccess();
        Holder<MultiNoiseBiomeSourceParameterList> preset = registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        MultiNoiseBiomeSource biomes = MultiNoiseBiomeSource.createFromPreset(preset);
        Holder<NoiseGeneratorSettings> noise = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(biomes, noise);
        RandomState random = RandomState.create(noise.value(), registries.lookupOrThrow(Registries.NOISE), SEED);
        int seaSurface = generator.getSeaLevel() - 1;
        Map<PortKind, PlanReport> out = new EnumMap<>(PortKind.class);
        for (Map.Entry<PortKind, int[][]> entry : SITES.entrySet()) {
            PortKind kind = entry.getKey();
            List<PortStructure> structures = structures(level, kind);
            int sites = 0;
            PlanReport sum = new PlanReport(kind, 0, 0);
            for (int[] site : entry.getValue()) {
                BlockPos candidate = new BlockPos(site[0], seaSurface, site[1]);
                Structure.GenerationContext context = new Structure.GenerationContext(registries, generator, biomes, random,
                        level.getStructureManager(), SEED, new ChunkPos(candidate), level, b -> true);
                PortStructure.Terrain terrain = (x, z) -> generator.getFirstFreeHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                PlanReport report = null;
                for (PortStructure structure : structures) {
                    if (structure.plan(context, candidate, seaSurface, terrain, structure.maxDepth()).isPresent()) {
                        report = PlanReport.latest(kind).orElse(null);
                        break;
                    }
                }
                if (report == null) {
                    Constants.LOG.info("{} real coast: {} at {} {}: no port", label, kind.getSerializedName(), site[0], site[1]);
                    continue;
                }
                Constants.LOG.info("{} real coast: {} roots {}", label, report.line(), report.roots);
                sites++;
                add(sum, report);
            }
            PlanReport counted = new PlanReport(kind, sites, 0);
            add(counted, sum);
            Constants.LOG.info("{} real coast {}: {} sites; {}", label, kind.getSerializedName(), sites, counted.line());
            out.put(kind, counted);
        }
        return out;
    }

    private static void add(PlanReport sum, PlanReport r) {
        sum.planned += r.planned;
        sum.kept += r.kept;
        sum.trail += r.trail;
        sum.land += r.land;
        sum.corners += r.corners;
        sum.wall += r.wall;
        sum.children += r.children;
        for (int i = 0; i < sum.childrenOf.length; i++) sum.childrenOf[i] += r.childrenOf[i];
        sum.ramps += r.ramps;
        sum.shifted += r.shifted;
        sum.swapped += r.swapped;
        sum.rerooted += r.rerooted;
    }

    /** The kind's warm and cold structures (a site plans as the one its biome takes). */
    static List<PortStructure> structures(ServerLevel level, PortKind kind) {
        List<ResourceKey<Structure>> keys = switch (kind) {
            case SEAFARER_VILLAGE -> List.of(VillageKeys.SEAFARER_VILLAGE, VillageKeys.SEAFARER_VILLAGE_COLD);
            case PIRATE_ISLAND -> List.of(IslandKeys.PIRATE_ISLAND, IslandKeys.PIRATE_ISLAND_COLD);
            case NAVY_OUTPOST -> List.of(OutpostKeys.NAVY_OUTPOST, OutpostKeys.NAVY_OUTPOST_COLD);
            default -> List.of();
        };
        return keys.stream().map(k -> PortSiteGameTests.structure(level, k)).toList();
    }
}
