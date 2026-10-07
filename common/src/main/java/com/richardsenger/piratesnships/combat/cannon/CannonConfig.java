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

    // ---- world rules, drops and glancing hits (Q2) -----------------------------------------------------------------

    public static final ConfigValue<Boolean> RESPECT_MOB_GRIEFING = S.bool("respect_mob_griefing", true,
            "Cannonballs break no blocks while the mobGriefing game rule is off");
    public static final ConfigValue<Boolean> RESPECT_SPAWN_PROTECTION = S.bool("respect_spawn_protection", true,
            "Cannonballs break no blocks inside the server's spawn protection (server.properties spawn-protection)");
    public static final ConfigValue<Boolean> DESTROYED_BLOCKS_DROP = S.bool("destroyed_blocks_drop", true,
            "Blocks a cannonball destroys drop their items as if mined");
    public static final ConfigValue<Boolean> GLANCING_HITS = S.bool("glancing_hits", true,
            "A ball that hits at an angle breaks fewer blocks (blocks_per_hit times the cosine of the angle to the face, "
                    + "at least 1) and pushes less; past glancing_bounce_degrees it bounces off without breaking anything");
    public static final ConfigValue<Double> GLANCING_BOUNCE_DEGREES = S.doubleRange("glancing_bounce_degrees", 75.0, 0.0, 90.0,
            "A ball whose flight is more than this many degrees off the hit face's normal bounces off (90 = never)");
    public static final ConfigValue<Double> GLANCING_BOUNCE_FACTOR = S.doubleRange("glancing_bounce_factor", 0.3, 0.0, 1.0,
            "Share of its speed towards the face a bouncing ball keeps, turned away from the face");

    // ---- the swivel gun (P2) ---------------------------------------------------------------------------------------

    private static final ConfigSection SWIVEL = S.section("swivel",
            "The swivel gun: a small gun on a fence or railing that turns freely; less range and damage, faster reload");
    public static final ConfigValue<Boolean> SWIVEL_ENABLED = SWIVEL.bool("enabled", true,
            "Swivel guns can be loaded, aimed and fired. Off = they are inert blocks");
    public static final ConfigValue<SwivelAmmo> SWIVEL_AMMO = SWIVEL.enumValue("ammo", SwivelAmmo.CANNONBALL,
            "What goes into a swivel gun after the gunpowder: a cannonball or lead shot");
    public static final ConfigValue<Integer> SWIVEL_AMMO_COUNT = SWIVEL.intRange("ammo_count", 1, 1, 64,
            "How many of the ammo items one load takes");
    public static final ConfigValue<Double> SWIVEL_DAMAGE = SWIVEL.doubleRange("damage", 8.0, 0.0, 1000.0,
            "Damage of a swivel shot hitting an entity (times combat.damage_multipliers.cannon_entity)");
    public static final ConfigValue<Double> SWIVEL_VELOCITY = SWIVEL.doubleRange("muzzle_velocity", 2.0, 0.1, 10.0,
            "Start speed of a swivel shot in blocks per tick, relative to the gun (gravity: cannons.gravity)");
    public static final ConfigValue<Integer> SWIVEL_RELOAD_TICKS = SWIVEL.intRange("reload_ticks", 60, 0, 6000,
            "Ticks after a shot before powder can go into the swivel gun again");
    public static final ConfigValue<Integer> SWIVEL_BLOCKS_PER_HIT = SWIVEL.intRange("blocks_per_hit", 0, 0, 16,
            "Blocks one swivel shot destroys (times combat.damage_multipliers.cannon_block; 0 = none)");
    public static final ConfigValue<Double> SWIVEL_RECOIL_IMPULSE = SWIVEL.doubleRange("recoil_impulse", 1.0, 0.0, 1000.0,
            "Impulse a swivel shot gives the firing ship against the barrel, in kpg*m/s (0 = no recoil)");
    public static final ConfigValue<Double> SWIVEL_IMPACT_IMPULSE = SWIVEL.doubleRange("impact_impulse", 1.0, 0.0, 1000.0,
            "Impulse a swivel shot gives the ship it hits, in kpg*m/s (0 = none)");
    public static final ConfigValue<Integer> SWIVEL_BALL_LIFETIME_TICKS = SWIVEL.intRange("ball_lifetime_ticks", 100, 1, 2400,
            "Ticks a swivel shot flies before it vanishes");
    public static final ConfigValue<Double> SWIVEL_MIN_ELEVATION = SWIVEL.doubleRange("min_elevation_degrees", -30.0, -90.0, 0.0,
            "Lowest barrel elevation of the swivel gun in degrees (negative = pointing down)");
    public static final ConfigValue<Double> SWIVEL_MAX_ELEVATION = SWIVEL.doubleRange("max_elevation_degrees", 45.0, 0.0, 90.0,
            "Highest barrel elevation of the swivel gun in degrees");
    public static final ConfigValue<Double> SWIVEL_AIM_REACH = SWIVEL.doubleRange("aim_reach", 4.0, 1.0, 16.0,
            "A player aiming a swivel gun lets go of it when further than this from the gun, in blocks");

    // ---- crew loading (C9) -----------------------------------------------------------------------------------------

    private static final ConfigSection CREW = S.section("crew",
            "Crew at a cannon or swivel gun loads it from powder and shot in a nearby container on the same ship");
    public static final ConfigValue<Boolean> CREW_ENABLED = CREW.bool("enabled", true,
            "Crew members load guns from a supply (the \"Load!\" order and the reload after a shot). Off = only players load");
    public static final ConfigValue<Integer> CREW_SUPPLY_RANGE = CREW.intRange("supply_range", 4, 0, 16,
            "Containers (chests, barrels, cargo crates) within this many blocks of the gun on the same ship are its supply");
    public static final ConfigValue<Integer> CREW_LOAD_TICKS = CREW.intRange("load_ticks", 80, 1, 6000,
            "Ticks a crew member needs to load a cannon (powder and ball); at least the reload time after a shot");
    public static final ConfigValue<Integer> CREW_SWIVEL_LOAD_TICKS = CREW.intRange("swivel_load_ticks", 40, 1, 6000,
            "Ticks a crew member needs to load a swivel gun; at least the reload time after a shot");
    public static final ConfigValue<Boolean> CREW_AUTO_RELOAD = CREW.bool("auto_reload", true,
            "After firing, a crew member loads the gun again by itself from the supply");

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

    public static float swivelDamage() {
        return CannonRules.entityDamage(SWIVEL_DAMAGE.get(), CombatConfig.CANNON_ENTITY_DAMAGE.get());
    }

    /** Blocks a swivel shot may destroy right now: 0 when {@code combat.cannon_block_damage} is off. */
    public static int swivelBlocksPerHit() {
        return CombatConfig.CANNON_BLOCK_DAMAGE.get()
                ? CannonRules.blocksPerHit(SWIVEL_BLOCKS_PER_HIT.get(), CombatConfig.CANNON_BLOCK_DAMAGE_MULTIPLIER.get()) : 0;
    }
}
