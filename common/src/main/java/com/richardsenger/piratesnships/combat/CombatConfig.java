package com.richardsenger.piratesnships.combat;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code combat} (docs/design.md §17, group "Combat"; §4.6, §8.1, §8.2). Sword combat has its
 * own section ({@code melee}). Declared ahead of the feature by {@code core.settings.SettingsModule}; nothing reads
 * these values yet.
 */
public final class CombatConfig {

    private static final ConfigSection S = ModConfigs.server("combat", "Firearms, cannons and damage");

    public static final ConfigValue<Boolean> FIREARM_MISFIRE_IN_RAIN = S.bool("firearm_misfire_in_rain", true,
            "Pistols and muskets can misfire when fired in the rain");
    public static final ConfigValue<Double> RAIN_MISFIRE_CHANCE = S.doubleRange("rain_misfire_chance", 0.25, 0.0, 1.0,
            "Chance that a pistol or musket misfires when fired in the rain (0 to 1)");
    public static final ConfigValue<Boolean> CANNON_BLOCK_DAMAGE = S.bool("cannon_block_damage", true,
            "Cannonballs destroy the ship and world blocks they hit. Off = cannonballs only hurt entities and push ships");

    private static final ConfigSection DAMAGE = S.section("damage_multipliers", "Multipliers on the damage of ranged weapons");

    public static final ConfigValue<Double> FIREARM_DAMAGE = DAMAGE.doubleRange("firearm", 1.0, 0.0, 10.0,
            "Multiplier on the damage pistols and muskets deal (1 = normal)");
    public static final ConfigValue<Double> CANNON_ENTITY_DAMAGE = DAMAGE.doubleRange("cannon_entity", 1.0, 0.0, 10.0,
            "Multiplier on the damage cannonballs deal to entities (1 = normal)");
    public static final ConfigValue<Double> CANNON_BLOCK_DAMAGE_MULTIPLIER = DAMAGE.doubleRange("cannon_block", 1.0, 0.0, 10.0,
            "Multiplier on how many blocks a cannonball destroys (1 = normal)");

    private CombatConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
