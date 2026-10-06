package com.richardsenger.piratesnships;

import net.fabricmc.api.ModInitializer;

/** Fabric entry point. TODO Fabric port (milestone 22): wire services like the NeoForge entry point does. */
public class PiratesNShips implements ModInitializer {

    @Override
    public void onInitialize() {
        PiratesNShipsCommon.init();
    }
}
