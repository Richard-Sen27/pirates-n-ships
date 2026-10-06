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

    /**
     * A top-level section of the server (gameplay, synced) config. One per feature module. The name must be unique
     * across server <i>and</i> client config (see {@link #client}).
     */
    public static ConfigSection server(String name, String comment) {
        return declare(SERVER, CLIENT, name, comment);
    }

    /**
     * A top-level section of the client (audio, visuals) config. The name must not also be a server section:
     * translation keys ({@code pirates_n_ships.configuration.<path>}) carry no config type, so equal names would
     * produce duplicate lang keys. Declaring one throws {@link IllegalStateException} naming both sections. Use e.g.
     * {@code "wave_effects"} next to the server's {@code "waves"}.
     */
    public static ConfigSection client(String name, String comment) {
        return declare(CLIENT, SERVER, name, comment);
    }

    private static synchronized ConfigSection declare(ConfigSchema schema, ConfigSchema other, String name, String comment) {
        List<String> path = List.of(name);
        if (other.hasSection(path)) {
            throw new IllegalStateException("Config section " + schema.type() + ":" + name + " clashes with the existing section "
                    + other.type() + ":" + name + ". Client and server sections share translation keys "
                    + "(" + ConfigValue.translationKey(path) + "), so top-level names must be unique across both; rename one.");
        }
        return new ConfigSection(schema, path, comment);
    }

    /** The schema of one config type (used by the config service and the lang datagen). */
    public static ConfigSchema schema(ConfigType type) {
        return type == ConfigType.SERVER ? SERVER : CLIENT;
    }
}
