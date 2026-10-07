package com.richardsenger.piratesnships.core.settings;

import com.richardsenger.piratesnships.combat.CombatConfig;
import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.crew.CrewConfig;
import com.richardsenger.piratesnships.hazard.HazardConfig;
import com.richardsenger.piratesnships.ship.ShipConfig;
import com.richardsenger.piratesnships.world.WorldConfig;
import com.richardsenger.piratesnships.worldsim.WorldSimConfig;

import java.util.List;

/**
 * Declares the config groups of docs/design.md §17 whose features don't exist yet, so the whole settings table is in
 * the config files and on the config screen from the start. Nothing reads these values yet. When the feature package
 * of one of these modules is written, its own {@link ModModule} calls that config class's {@code init()} and the
 * call here is removed.
 */
public final class SettingsModule implements ModModule {

    /** Top-level server sections declared by this module. */
    public static final List<String> SERVER_SECTIONS = List.of(
            "ships", "waves", "hazards", "crew", "combat", "world", "world_simulation");

    /** Top-level client sections declared by this module. */
    public static final List<String> CLIENT_SECTIONS = List.of("wave_effects");

    @Override
    public String id() {
        return "core.settings";
    }

    @Override
    public void registerConfig() {
        ShipConfig.init();
        HazardConfig.init();
        CrewConfig.init();
        CombatConfig.init();
        WorldConfig.init();
        WorldSimConfig.init();
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(SettingsGameTests.class);
    }
}
