package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.combat.CombatConfig;
import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code cannons} (docs/design.md §8.2, §17 "Combat"). The block damage toggle and the two
 * cannon damage multipliers already exist in the {@code combat} section ({@link CombatConfig#CANNON_BLOCK_DAMAGE},
 * {@code damage_multipliers.cannon_entity} and {@code cannon_block}) and are used from there, so there is one value per
 * setting (as the firearms do).
 */
public final class CannonConfig {

    private static final ConfigSection S = ModConfigs.server("cannons", "Cannons: loading, aiming, firing, cannonballs");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Cannons can be loaded, aimed and fired. Off = they are inert blocks");
    public static final ConfigValue<Double> DAMAGE = S.doubleRange("damage", 20.0, 0.0, 1000.0,
            "Damage of a cannonball hitting an entity (times combat.damage_multipliers.cannon_entity)");
    public static final ConfigValue<Double> MUZZLE_VELOCITY = S.doubleRange("muzzle_velocity", 3.0, 0.1, 10.0,
            "Start speed of a cannonball in blocks per tick, relative to the cannon");
    public static final ConfigValue<Double> GRAVITY = S.doubleRange("gravity", 0.03, 0.0, 1.0,
            "Downward acceleration of a cannonball in blocks per tick squared (arrows: 0.05)");
    public static final ConfigValue<Integer> RELOAD_TICKS = S.intRange("reload_ticks", 100, 0, 6000,
            "Ticks after a shot before powder can go into the cannon again");
    public static final ConfigValue<Integer> ELEVATION_STEPS = S.intRange("elevation_steps", 6, 1, 32,
            "Number of elevation steps from min_elevation_degrees to max_elevation_degrees");
    public static final ConfigValue<Double> MIN_ELEVATION = S.doubleRange("min_elevation_degrees", -5.0, -45.0, 45.0,
            "Lowest barrel elevation in degrees (negative = pointing down)");
    public static final ConfigValue<Double> MAX_ELEVATION = S.doubleRange("max_elevation_degrees", 20.0, -45.0, 60.0,
            "Highest barrel elevation in degrees");
    public static final ConfigValue<Integer> BLOCKS_PER_HIT = S.intRange("blocks_per_hit", 1, 0, 16,
            "Blocks one cannonball destroys: the hit block and the next ones along its path "
                    + "(times combat.damage_multipliers.cannon_block; combat.cannon_block_damage turns it off)");
    public static final ConfigValue<Double> RECOIL_IMPULSE = S.doubleRange("recoil_impulse", 8.0, 0.0, 1000.0,
            "Impulse a shot gives the firing ship against the barrel, in kpg*m/s (0 = no recoil)");
    public static final ConfigValue<Double> IMPACT_IMPULSE = S.doubleRange("impact_impulse", 8.0, 0.0, 1000.0,
            "Impulse a cannonball gives the ship it hits, along its flight, in kpg*m/s (0 = none)");
    public static final ConfigValue<Integer> BALL_LIFETIME_TICKS = S.intRange("ball_lifetime_ticks", 200, 1, 2400,
            "Ticks a cannonball flies before it vanishes");
    public static final ConfigValue<Double> WATER_SLOWDOWN = S.doubleRange("water_slowdown", 0.5, 0.0, 1.0,
            "Share of its speed a cannonball keeps per tick in water, on top of vanilla's water drag");
    public static final ConfigValue<Double> SINK_SPEED = S.doubleRange("sink_speed", 0.3, 0.0, 10.0,
            "A cannonball in water slower than this (blocks per tick) has sunk and is removed");

    private CannonConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    public static double elevationDegrees(int index) {
        return CannonRules.elevationDegrees(index, ELEVATION_STEPS.get(), MIN_ELEVATION.get(), MAX_ELEVATION.get());
    }

    public static int levelStep() {
        return CannonRules.levelStep(ELEVATION_STEPS.get(), MIN_ELEVATION.get(), MAX_ELEVATION.get());
    }

    public static float entityDamage() {
        return CannonRules.entityDamage(DAMAGE.get(), CombatConfig.CANNON_ENTITY_DAMAGE.get());
    }

    /** Blocks a hit may destroy right now: 0 when {@code combat.cannon_block_damage} is off. */
    public static int blocksPerHit() {
        return CombatConfig.CANNON_BLOCK_DAMAGE.get()
                ? CannonRules.blocksPerHit(BLOCKS_PER_HIT.get(), CombatConfig.CANNON_BLOCK_DAMAGE_MULTIPLIER.get()) : 0;
    }
}
