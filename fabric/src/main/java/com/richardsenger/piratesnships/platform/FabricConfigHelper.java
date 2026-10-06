package com.richardsenger.piratesnships.platform;

import com.richardsenger.piratesnships.core.config.ConfigSchema;
import com.richardsenger.piratesnships.platform.services.IConfigHelper;

/** TODO Fabric port (milestone 22): same translation as NeoForgeConfigHelper, via Forge Config API Port. */
public class FabricConfigHelper implements IConfigHelper {

    @Override
    public void register(ConfigSchema schema) {
        throw new UnsupportedOperationException("Fabric port: milestone 22");
    }
}
