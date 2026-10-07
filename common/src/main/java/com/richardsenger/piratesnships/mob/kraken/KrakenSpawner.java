package com.richardsenger.piratesnships.mob.kraken;

import com.richardsenger.piratesnships.hazards.HazardSpawner;
import com.richardsenger.piratesnships.hazards.HazardsConfig;
import com.richardsenger.piratesnships.hazards.SpawnRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Natural appearing of the kraken (docs/design.md §12), from the level tick, on the H1 hazards' schedule and site rules
 * ({@link HazardSpawner}, {@link SpawnRules}): every {@code hazards.spawn_check_interval_ticks}, one roll per player in
 * the deep ocean ({@link HazardSpawner.Site#inDeepOcean}) at {@code hazards.kraken.chance_per_day} converted to the
 * check interval, ×{@code night_multiplier} at night and ×{@code thunder_multiplier} in a thunderstorm
 * ({@link KrakenRules}); never within {@code min_separation} blocks of another kraken. A hit tries a few spots
 * {@code hazards.spawn_min_distance}–{@code spawn_max_distance} blocks from the player and places the kraken
 * {@link Kraken#LURK_DEPTH} under the surface of the first deep-ocean column with at least {@code min_water_depth}
 * blocks of water.
 */
public final class KrakenSpawner {

    static final int ATTEMPTS = 4;

    private KrakenSpawner() {
    }

    /** The world as the spawner sees it (tests answer for a test area). */
    public interface Site {
        boolean inDeepOcean(ServerLevel level, Player player);

        /** Where to place a kraken at x/z (its feet), or null when that is no deep, open, loaded water. */
        @Nullable Vec3 deepWater(ServerLevel level, double x, double z, int minDepth);
    }

    /** The real world: the hazards' deep-ocean test for the player, a loaded deep-ocean column of water for the spot. */
    public static final Site WORLD = new Site() {
        @Override
        public boolean inDeepOcean(ServerLevel level, Player player) {
            return HazardSpawner.WORLD.inDeepOcean(level, player);
        }

        @Override
        public @Nullable Vec3 deepWater(ServerLevel level, double x, double z, int minDepth) {
            BlockPos column = BlockPos.containing(x, level.getSeaLevel(), z);
            if (!level.isPositionEntityTicking(column)) {
                // never load chunks for a kraken; and only where entities tick, so it moves at once and other
                // krakens' separation checks can see it: Level#getEntitiesOfClass skips entity sections that are not
                // accessible, so an entity added to a chunk that is loaded but not entity-loaded is invisible to
                // lookups (K1a: the spawner test missed such a kraken once in three runs)
                return null;
            }
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ());
            BlockPos surface = new BlockPos(column.getX(), top - 1, column.getZ());
            FluidState water = level.getFluidState(surface);
            if (!water.is(FluidTags.WATER) || !level.canSeeSky(surface.above()) || !level.getBiome(surface).is(BiomeTags.IS_DEEP_OCEAN)) {
                return null;
            }
            for (int d = 1; d < minDepth; d++) {
                if (!level.getFluidState(surface.below(d)).is(FluidTags.WATER)) return null;
            }
            double depth = Math.min(Kraken.LURK_DEPTH, minDepth - 4);
            return new Vec3(column.getX() + 0.5, top - depth, column.getZ() + 0.5);
        }
    };

    /** Level tick hook. */
    public static void onLevelTick(ServerLevel level) {
        int interval = HazardsConfig.SPAWN_CHECK_INTERVAL_TICKS.get();
        if (!SpawnRules.isCheckTick(level.getGameTime(), interval) || !HazardsConfig.KRAKEN_ENABLED.get()) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isCreative()) {
                continue;
            }
            trySpawn(level, player, WORLD, level.getRandom());
        }
    }

    /** The chance per spawn check for a player in the deep ocean, from the config and the world's time and weather. */
    public static double chancePerCheck(ServerLevel level) {
        double day = KrakenRules.dayChance(HazardsConfig.KRAKEN_CHANCE_PER_DAY.get(), level.isNight(), level.isThundering(),
                KrakenConfig.NIGHT_MULTIPLIER.get(), KrakenConfig.THUNDER_MULTIPLIER.get());
        return KrakenRules.perCheck(day, HazardsConfig.SPAWN_CHECK_INTERVAL_TICKS.get());
    }

    /** One spawn roll for {@code player}; the new kraken, or null when none appeared. */
    public static @Nullable Kraken trySpawn(ServerLevel level, Player player, Site site, RandomSource random) {
        double roll = random.nextDouble();
        boolean enabled = HazardsConfig.KRAKEN_ENABLED.get();
        double chance = chancePerCheck(level);
        // the cheap tests first; the separation scan only after the roll hit
        if (!enabled || roll >= chance || !site.inDeepOcean(level, player)) {
            return null;
        }
        double separation = KrakenConfig.MIN_SEPARATION.get();
        if (!KrakenRules.rollSpawn(enabled, true, nearestKraken(level, player.position(), separation), separation, chance, roll)) {
            return null;
        }
        for (int i = 0; i < ATTEMPTS; i++) {
            double[] off = SpawnRules.offset(random.nextDouble(), random.nextDouble(),
                    HazardsConfig.SPAWN_MIN_DISTANCE.get(), HazardsConfig.SPAWN_MAX_DISTANCE.get());
            Vec3 at = site.deepWater(level, player.getX() + off[0], player.getZ() + off[1], KrakenConfig.MIN_WATER_DEPTH.get());
            if (at != null && nearestKraken(level, at, separation) >= separation) {
                return spawn(level, at, MobSpawnType.EVENT);
            }
        }
        return null;
    }

    /** Distance from {@code at} to the nearest loaded kraken within {@code range}, or infinity. */
    public static double nearestKraken(ServerLevel level, Vec3 at, double range) {
        if (range <= 0) return Double.POSITIVE_INFINITY;
        double best = Double.POSITIVE_INFINITY;
        for (Kraken k : level.getEntitiesOfClass(Kraken.class, new AABB(at, at).inflate(range), Kraken::isAlive)) {
            best = Math.min(best, k.position().distanceTo(at));
        }
        return best;
    }

    /** Places a kraken with its feet at {@code at}; null if the level refused it. */
    public static @Nullable Kraken spawn(ServerLevel level, Vec3 at, MobSpawnType reason) {
        Kraken k = KrakenContent.KRAKEN.get().create(level);
        if (k == null) return null;
        k.moveTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360f, 0f);
        k.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), reason, null);
        return level.addFreshEntity(k) ? k : null;
    }
}
