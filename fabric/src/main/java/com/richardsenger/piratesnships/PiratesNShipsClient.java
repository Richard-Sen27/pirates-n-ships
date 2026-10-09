package com.richardsenger.piratesnships;

import com.richardsenger.piratesnships.platform.FabricClientSetup;
import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric client entry point (FAB2, {@code fabric.mod.json} {@code client}): the twin of the {@code dist.isClient()}
 * branch of NeoForge's {@code PiratesNShips}. Runs after every mod's {@code main} entry point, so the common init and
 * all registry entries exist. Feature modules never add code here.
 */
public class PiratesNShipsClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        PiratesNShipsCommon.initClient();
        FabricClientSetup.attach();
    }
}
