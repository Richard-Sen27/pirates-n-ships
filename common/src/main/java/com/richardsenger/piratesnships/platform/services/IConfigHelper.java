package com.richardsenger.piratesnships.platform.services;

import com.richardsenger.piratesnships.core.config.ConfigSchema;

/**
 * Translates our config schema into the loader's config system. Called once per config type by
 * {@code PiratesNShipsCommon.init()} after every module declared its config. Feature code never uses this.
 */
public interface IConfigHelper {

    /** Freezes the schema, builds the loader spec, registers it and binds every value handle to it. */
    void register(ConfigSchema schema);
}
