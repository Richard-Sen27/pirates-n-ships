package com.richardsenger.piratesnships.core.config;

import java.util.List;

/**
 * Entry point of our config wrapper. The backing library (NeoForge {@code ModConfigSpec} on NeoForge, Forge Config
 * API Port on Fabric) is hidden behind {@code Services.CONFIG}; see docs/design.md §3.3 and §21.
 */
public final class ModConfigs {

    private static final ConfigSchema SERVER = new ConfigSchema(ConfigType.SERVER);
    private static final ConfigSchema CLIENT = new ConfigSchema(ConfigType.CLIENT);

    private ModConfigs() {
    }

    /** A top-level section of the server (gameplay, synced) config. One per feature module. */
    public static ConfigSection server(String name, String comment) {
        return new ConfigSection(SERVER, List.of(name), comment);
    }

    /** A top-level section of the client (audio, visuals) config. */
    public static ConfigSection client(String name, String comment) {
        return new ConfigSection(CLIENT, List.of(name), comment);
    }

    /** The schema of one config type (used by the config service and the lang datagen). */
    public static ConfigSchema schema(ConfigType type) {
        return type == ConfigType.SERVER ? SERVER : CLIENT;
    }
}
