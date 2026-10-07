package com.richardsenger.piratesnships.world.island;

import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.MobKind;
import com.richardsenger.piratesnships.mob.entity.Pirate;
import com.richardsenger.piratesnships.world.WorldConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;

/**
 * The pirate's spawn rule (design.md §9 "Spawns on pirate islands", WG2), registered as its spawn placement
 * ({@code ON_GROUND}, {@code MOTION_BLOCKING_NO_LEAVES}) by the world module. The island's spawn overrides are the only
 * natural spawn list that names the pirate, so natural pirates appear inside the camp's pieces.
 *
 * <p>Vanilla's {@code Monster.checkMonsterSpawnRules} would demand darkness; pirates stand about their camp in
 * daylight, so this uses the plain mob rule (a sturdy, non-glowing block below: sand, planks, gravel) and adds two
 * limits for natural spawns: {@code world.spawn_weights.pirate} above 0, the pirate type enabled
 * ({@code mobs.enabled.pirate}), and fewer than {@code world.structures.pirate_island.max_pirates} pirates within
 * {@link #CAP_RADIUS} blocks. The cap matters because pirates never despawn ({@code SeafarerMob.removeWhenFarAway}), so
 * the hostile mob cap alone would let a camp fill up. Spawn eggs, spawners and commands skip the limits.
 */
public final class PirateIslandSpawns {

    public static final double CAP_RADIUS = 32.0;

    private PirateIslandSpawns() {
    }

    public static boolean check(EntityType<Pirate> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        if (!Mob.checkMobSpawnRules(type, level, spawnType, pos, random)) return false;
        if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION) return true;
        return naturalAllowed(WorldConfig.SPAWN_WEIGHT_PIRATE.get(), MobConfig.enabled(MobKind.PIRATE).get(),
                level.getEntitiesOfClass(Pirate.class, new AABB(pos).inflate(CAP_RADIUS)).size(),
                WorldConfig.PIRATE_ISLAND_MAX_PIRATES.get());
    }

    /** Pure part of the rule. */
    public static boolean naturalAllowed(int spawnWeight, boolean enabled, int piratesNearby, int cap) {
        return spawnWeight > 0 && enabled && piratesNearby < cap;
    }
}
