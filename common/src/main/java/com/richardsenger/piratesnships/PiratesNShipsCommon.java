package com.richardsenger.piratesnships;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.ModModules;
import com.richardsenger.piratesnships.core.config.ConfigType;
import com.richardsenger.piratesnships.core.config.ModConfigs;
import com.richardsenger.piratesnships.platform.Services;

/** Loader-independent initialization. Called by each loader's entry point during mod construction. */
public final class PiratesNShipsCommon {

    private PiratesNShipsCommon() {
    }

    /** Runs every module hook (both sides), then registers the config schemas. */
    public static void init() {
        Constants.LOG.info("Initializing {} on {} ({})", Constants.MOD_NAME, Services.PLATFORM.getPlatformName(),
                Services.PLATFORM.getEnvironmentName());
        for (ModModule m : ModModules.ALL) m.registerConfig();
        for (ModModule m : ModModules.ALL) m.registerContent();
        for (ModModule m : ModModules.ALL) m.registerPayloads();
        for (ModModule m : ModModules.ALL) m.registerEvents();
        Services.CONFIG.register(ModConfigs.schema(ConfigType.SERVER));
        Services.CONFIG.register(ModConfigs.schema(ConfigType.CLIENT));
    }

    /** Physical client only, after {@link #init()}. */
    public static void initClient() {
        for (ModModule m : ModModules.ALL) m.initClient();
    }
}
