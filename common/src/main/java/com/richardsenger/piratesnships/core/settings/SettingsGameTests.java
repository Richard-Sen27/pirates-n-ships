package com.richardsenger.piratesnships.core.settings;

import com.richardsenger.piratesnships.audio.AudioConfig;
import com.richardsenger.piratesnships.combat.CombatConfig;
import com.richardsenger.piratesnships.core.config.ConfigType;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.CrewConfig;
import com.richardsenger.piratesnships.hazard.HazardConfig;
import com.richardsenger.piratesnships.ship.ShipConfig;
import com.richardsenger.piratesnships.survival.SurvivalConfig;
import com.richardsenger.piratesnships.world.WorldConfig;
import com.richardsenger.piratesnships.worldsim.WorldSimConfig;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** The settings sections are registered in a real server and return their defaults. Read-only: no own batch. */
public final class SettingsGameTests {

    private SettingsGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(SettingsGameTests.class);
    }

    @ModGameTest
    public static void serverConfigContainsSettingsSections(GameTestHelper helper) {
        Map<List<String>, String> server = ModConfigs.schema(ConfigType.SERVER).sections();
        for (String name : SettingsModule.SERVER_SECTIONS) {
            helper.assertTrue(server.containsKey(List.of(name)), "server config lacks section " + name);
        }
        Map<List<String>, String> client = ModConfigs.schema(ConfigType.CLIENT).sections();
        for (String name : SettingsModule.CLIENT_SECTIONS) {
            helper.assertTrue(client.containsKey(List.of(name)), "client config lacks section " + name);
        }
        helper.succeed();
    }

    @ModGameTest
    public static void serverSettingsReturnDefaults(GameTestHelper helper) {
        List<ConfigValue<?>> samples = List.of(
                ShipConfig.SINKING_ENABLED, ShipConfig.BUILD_DAYS_BRIGANTINE,
                HazardConfig.WAVE_AMPLITUDE, HazardConfig.KRAKEN_ENABLED,
                CrewConfig.MAX_CREW_MULTIPLIER,
                CombatConfig.RAIN_MISFIRE_CHANCE,
                SurvivalConfig.TIME_TO_FREEZE_SECONDS,
                WorldConfig.WRECK.spacing(), WorldConfig.SPAWN_WEIGHT_SHARK,
                WorldSimConfig.MAX_SIMULTANEOUS_VOYAGES);
        for (ConfigValue<?> v : samples) {
            helper.assertTrue(v.isLive(), v + " is not bound to the loaded server config");
            helper.assertValueEqual(v.get(), v.defaultValue(), v.toString());
        }
        helper.succeed();
    }

    @ModGameTest
    public static void clientSettingsReturnDefaults(GameTestHelper helper) {
        // The client config is not loaded on a dedicated server, so these resolve to their defaults.
        for (ConfigValue<?> v : List.<ConfigValue<?>>of(AudioConfig.MUSIC_VOLUME, HazardConfig.CAMERA_SWAY)) {
            helper.assertValueEqual(v.get(), v.defaultValue(), v.toString());
        }
        helper.succeed();
    }
}
