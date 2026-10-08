package com.richardsenger.piratesnships.core.settings;

import com.richardsenger.piratesnships.audio.AudioConfig;
import com.richardsenger.piratesnships.combat.CombatConfig;
import com.richardsenger.piratesnships.core.ModModules;
import com.richardsenger.piratesnships.core.config.ConfigSchema;
import com.richardsenger.piratesnships.core.config.ConfigType;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;
import com.richardsenger.piratesnships.crew.CrewConfig;
import com.richardsenger.piratesnships.hazard.HazardConfig;
import com.richardsenger.piratesnships.hazards.HazardsConfig;
import com.richardsenger.piratesnships.ship.ShipConfig;
import com.richardsenger.piratesnships.survival.SurvivalConfig;
import com.richardsenger.piratesnships.world.WorldConfig;
import com.richardsenger.piratesnships.worldsim.WorldSimConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Schema checks for the sections declared by {@link SettingsModule}, plus whole-schema uniqueness. */
class SettingsConfigTest {

    @BeforeAll
    static void declareEverything() {
        // Some module classes hold Minecraft constants; bootstrap so every module's registerConfig() can run.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ModModules.ALL.forEach(m -> m.registerConfig());
    }

    private static List<ConfigValue<?>> ownValues() {
        List<ConfigValue<?>> own = new ArrayList<>();
        for (ConfigValue<?> v : ModConfigs.schema(ConfigType.SERVER).values()) {
            if (SettingsModule.SERVER_SECTIONS.contains(v.path().getFirst())) own.add(v);
        }
        for (ConfigValue<?> v : ModConfigs.schema(ConfigType.CLIENT).values()) {
            if (SettingsModule.CLIENT_SECTIONS.contains(v.path().getFirst())) own.add(v);
        }
        return own;
    }

    @Test
    void everySectionIsDeclaredAndNonEmpty() {
        check(ConfigType.SERVER, SettingsModule.SERVER_SECTIONS);
        check(ConfigType.CLIENT, SettingsModule.CLIENT_SECTIONS);
    }

    private static void check(ConfigType type, List<String> names) {
        ConfigSchema schema = ModConfigs.schema(type);
        for (String name : names) {
            assertTrue(schema.sections().containsKey(List.of(name)), type + " lacks section " + name);
            assertTrue(schema.values().stream().anyMatch(v -> v.path().getFirst().equals(name)), type + " section " + name + " is empty");
        }
    }

    @Test
    void ownValuesAreWellFormed() {
        List<ConfigValue<?>> own = ownValues();
        // 44 since H1: the six hazards values (waterspouts, whirlpools, kraken: enabled + chance) moved to hazards.HazardsConfig
        assertTrue(own.size() >= 44, "expected all settings values, found " + own.size());
        for (ConfigValue<?> v : own) {
            String c = v.comment();
            assertFalse(c == null || c.isBlank(), v + " has no comment");
            assertEquals(c.strip(), c, v + " comment has leading or trailing whitespace");
            assertFalse(c.endsWith("."), v + " comment ends with a period (house style)");
            for (String part : v.path()) assertTrue(part.matches("[a-z][a-z0-9]*(_[a-z0-9]+)*"), v + " is not snake_case");
            if (v.min() != null || v.max() != null) {
                double def = ((Number) v.defaultValue()).doubleValue();
                assertTrue(v.min().doubleValue() <= def && def <= v.max().doubleValue(), v + " default outside its range");
                assertTrue(v.min().doubleValue() < v.max().doubleValue(), v + " has an empty range");
            } else {
                assertTrue(v.kind() != ConfigValue.Kind.INT && v.kind() != ConfigValue.Kind.DOUBLE, v + " is a number without a range");
            }
        }
        ModConfigs.schema(ConfigType.SERVER).sections().forEach((path, comment) -> {
            if (SettingsModule.SERVER_SECTIONS.contains(path.getFirst())) {
                assertFalse(comment.isBlank(), "section " + path + " has no comment");
                assertEquals(comment.strip(), comment, "section " + path + " comment whitespace");
            }
        });
    }

    @Test
    void ownValuesAreServerOrClientAsDesigned() {
        assertEquals(ConfigType.SERVER, ShipConfig.SINKING_ENABLED.type());
        assertEquals(ConfigType.SERVER, HazardConfig.WAVE_AMPLITUDE.type());
        assertEquals(ConfigType.CLIENT, HazardConfig.CAMERA_SWAY.type());
        assertEquals(ConfigType.CLIENT, AudioConfig.MUSIC_VOLUME.type());
    }

    @Test
    void noDuplicatePathsOrTranslationKeysAcrossBothSchemas() {
        Map<String, String> keys = new HashMap<>();
        for (ConfigType type : ConfigType.values()) {
            ConfigSchema schema = ModConfigs.schema(type);
            Set<List<String>> paths = new HashSet<>();
            for (ConfigValue<?> v : schema.values()) {
                assertTrue(paths.add(v.path()), "duplicate value path " + v);
                assertFalse(schema.sections().containsKey(v.path()), v + " is both a value and a section");
                claim(keys, v.translationKey(), v.toString());
            }
            for (List<String> section : schema.sections().keySet()) {
                // The lang file has one key per path regardless of config type, so server and client must not overlap.
                claim(keys, ConfigValue.translationKey(section), type + " section " + String.join(".", section));
            }
        }
    }

    private static void claim(Map<String, String> keys, String key, String owner) {
        String previous = keys.putIfAbsent(key, owner);
        assertNull(previous, "translation key " + key + " used by both " + previous + " and " + owner);
    }

    @Test
    void valuesReadDefaultsWithoutALoader() {
        assertFalse(ShipConfig.SINKING_ENABLED.isLive());
        assertTrue(ShipConfig.SINKING_ENABLED.get());
        assertEquals(0, ShipConfig.WRECK_PERSISTENCE_DAYS.get());
        assertTrue(ShipConfig.SHIPWRIGHT_ORDERS.get());
        assertEquals(2.0, ShipConfig.BUILD_DAYS_SLOOP.get());
        assertEquals(500, ShipConfig.PRICE_BRIGANTINE.get());
        assertTrue(HazardConfig.WAVES_ENABLED.get());
        assertEquals(1.0, HazardConfig.WAVE_AMPLITUDE.get());
        assertTrue(HazardsConfig.KRAKEN_ENABLED.get());
        assertEquals(0.05, HazardsConfig.KRAKEN_CHANCE_PER_DAY.get());
        assertFalse(HazardConfig.CAMERA_SWAY.get()); // WV1: camera sway is opt-in (design.md §5.4)
        assertTrue(CrewConfig.WAGES_ENABLED.get());
        assertFalse(CrewConfig.MUTINY_ENABLED.get());
        assertEquals(2, CrewConfig.WAGE_PER_DAY.get());
        assertEquals(1.0, CrewConfig.MAX_CREW_MULTIPLIER.get());
        assertTrue(CombatConfig.FIREARM_MISFIRE_IN_RAIN.get());
        assertTrue(CombatConfig.CANNON_BLOCK_DAMAGE.get());
        assertEquals(1.0, CombatConfig.FIREARM_DAMAGE.get());
        assertTrue(SurvivalConfig.COLD_WATER_ENABLED.get());
        assertEquals(1, SurvivalConfig.FREEZE_TICKS_PER_TICK.get());
        assertEquals(2400, SurvivalConfig.WARM_EFFECT_TICKS.get());
        assertTrue(SurvivalConfig.SWIM_HUNGER_ENABLED.get());
        assertEquals(1.5, SurvivalConfig.SWIM_EXHAUSTION_MULTIPLIER.get());
        assertEquals(64, WorldConfig.PIRATE_ISLAND.spacing().get());
        assertEquals(1.0, WorldConfig.WRECK.frequency().get());
        assertEquals(List.of("world", "structures", "navy_outpost", "spacing"), WorldConfig.NAVY_OUTPOST.spacing().path());
        assertEquals(10, WorldConfig.SPAWN_WEIGHT_PIRATE.get());
        assertTrue(WorldSimConfig.ENABLED.get());
        assertEquals(192, WorldSimConfig.MATERIALIZE_RADIUS.get());
        assertEquals(5.0, WorldSimConfig.RAID_COOLDOWN_DAYS.get());
        assertTrue(AudioConfig.MUSIC_ENABLED.get());
        assertEquals(1.0, AudioConfig.AMBIENCE_VOLUME.get());
    }

    @Test
    void overridesWorkWithoutALoader() {
        try {
            CrewConfig.MUTINY_ENABLED.set(true);
            assertTrue(CrewConfig.MUTINY_ENABLED.get());
        } finally {
            CrewConfig.MUTINY_ENABLED.reset();
        }
        assertFalse(CrewConfig.MUTINY_ENABLED.get());
    }
}
