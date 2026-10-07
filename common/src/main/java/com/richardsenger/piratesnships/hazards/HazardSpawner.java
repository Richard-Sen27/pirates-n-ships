package com.richardsenger.piratesnships.hazards;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Natural forming of waterspouts and whirlpools (docs/design.md §12), from the level tick: every
 * {@code spawn_check_interval_ticks}, for each player, one roll per kind by {@link SpawnRules}; a hit tries a few spots
 * 48–96 blocks from the player and places the hazard on the first open-water one. The world questions (at sea? open
 * water there?) go through {@link Site} so tests can answer them for a test area.
 */
public final class HazardSpawner {

    /** Spots tried per successful roll before giving up. */
    static final int ATTEMPTS = 4;
    /** Whirlpools need at least this much water under the surface. */
    static final int WHIRLPOOL_DEPTH = 4;

    private HazardSpawner() {
    }

    /** The world as the spawner sees it. */
    public interface Site {
        /** In an ocean biome under open sky. */
        boolean atSea(ServerLevel level, Player player);

        /** In a deep-ocean biome under open sky. */
        boolean inDeepOcean(ServerLevel level, Player player);

        /** The base (water line, column centre) for a hazard of {@code kind} at x/z, or null when that is no open water. */
        @Nullable Vec3 openWater(ServerLevel level, double x, double z, HazardKind kind);
    }

    /** The real world: biome tags {@code #minecraft:is_ocean} / {@code #is_deep_ocean}, loaded chunks only. */
    public static final Site WORLD = new Site() {
        @Override
        public boolean atSea(ServerLevel level, Player player) {
            BlockPos eyes = BlockPos.containing(player.getEyePosition());
            return level.getBiome(eyes).is(BiomeTags.IS_OCEAN) && level.canSeeSky(eyes);
        }

        @Override
        public boolean inDeepOcean(ServerLevel level, Player player) {
            BlockPos eyes = BlockPos.containing(player.getEyePosition());
            return level.getBiome(eyes).is(BiomeTags.IS_DEEP_OCEAN) && level.canSeeSky(eyes);
        }

        @Override
        public @Nullable Vec3 openWater(ServerLevel level, double x, double z, HazardKind kind) {
            BlockPos column = BlockPos.containing(x, level.getSeaLevel(), z);
            if (!level.isLoaded(column)) {
                return null; // never load chunks for a hazard
            }
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ());
            BlockPos surface = new BlockPos(column.getX(), top - 1, column.getZ());
            FluidState water = level.getFluidState(surface);
            if (!water.is(FluidTags.WATER) || !water.isSource() || !level.canSeeSky(surface.above())) {
                return null;
            }
            if (kind == HazardKind.WHIRLPOOL) {
                if (!level.getBiome(surface).is(BiomeTags.IS_DEEP_OCEAN)) return null;
                for (int d = 1; d < WHIRLPOOL_DEPTH; d++) {
                    if (!level.getFluidState(surface.below(d)).is(FluidTags.WATER)) return null;
                }
            } else if (!level.getBiome(surface).is(BiomeTags.IS_OCEAN)) {
                return null;
            }
            return new Vec3(column.getX() + 0.5, top, column.getZ() + 0.5);
        }
    };

    /** Level tick hook. */
    public static void onLevelTick(ServerLevel level) {
        if (!SpawnRules.isCheckTick(level.getGameTime(), HazardsConfig.SPAWN_CHECK_INTERVAL_TICKS.get())) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            for (HazardKind kind : HazardKind.values()) {
                trySpawn(level, player, kind, WORLD, level.getRandom());
            }
        }
    }

    /** One spawn roll of {@code kind} for {@code player}; the new hazard, or null when none formed. */
    public static @Nullable HazardEntity trySpawn(ServerLevel level, Player player, HazardKind kind, Site site, RandomSource random) {
        boolean rolled = switch (kind) {
            case WATERSPOUT -> SpawnRules.rollWaterspout(HazardsConfig.WATERSPOUTS_ENABLED.get(), level.isThundering(),
                    site.atSea(level, player), aliveNear(level, player, kind), HazardsConfig.WATERSPOUT_MAX_PER_PLAYER.get(),
                    HazardsConfig.WATERSPOUT_CHANCE.get(), random.nextDouble());
            case WHIRLPOOL -> SpawnRules.rollWhirlpool(HazardsConfig.WHIRLPOOLS_ENABLED.get(), site.inDeepOcean(level, player),
                    aliveNear(level, player, kind), HazardsConfig.WHIRLPOOL_MAX_PER_PLAYER.get(),
                    HazardsConfig.WHIRLPOOL_CHANCE.get(), random.nextDouble());
        };
        if (!rolled) {
            return null;
        }
        for (int i = 0; i < ATTEMPTS; i++) {
            double[] off = SpawnRules.offset(random.nextDouble(), random.nextDouble(),
                    HazardsConfig.SPAWN_MIN_DISTANCE.get(), HazardsConfig.SPAWN_MAX_DISTANCE.get());
            Vec3 base = site.openWater(level, player.getX() + off[0], player.getZ() + off[1], kind);
            if (base != null) {
                return spawn(level, kind, base, random);
            }
        }
        return null;
    }

    /** Hazards of {@code kind} near {@code player} that count toward {@code max_per_player}. */
    public static int aliveNear(ServerLevel level, Player player, HazardKind kind) {
        double radius = kind == HazardKind.WATERSPOUT ? HazardsConfig.WATERSPOUT_RADIUS.get() : HazardsConfig.WHIRLPOOL_RADIUS.get();
        double range = SpawnRules.capRange(Math.max(HazardsConfig.SPAWN_MIN_DISTANCE.get(), HazardsConfig.SPAWN_MAX_DISTANCE.get()), radius);
        return level.getEntitiesOfClass(HazardEntity.class, player.getBoundingBox().inflate(range, 64, range),
                e -> e.kind() == kind && e.isAlive()).size();
    }

    /** Places a configured hazard of {@code kind} with its base at {@code base}; null if the level refused it. */
    public static @Nullable HazardEntity spawn(ServerLevel level, HazardKind kind, Vec3 base, RandomSource random) {
        HazardEntity e = HazardsContent.type(kind).create(level);
        if (e == null) {
            return null;
        }
        e.configure(random);
        e.moveTo(base.x, base.y, base.z, 0f, 0f);
        return level.addFreshEntity(e) ? e : null;
    }
}
