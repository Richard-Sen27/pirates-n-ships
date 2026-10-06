package com.richardsenger.piratesnships;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Mod-wide constants. */
public final class Constants {

    /** The mod id. Also the namespace of every registry entry, asset and GameTest. */
    public static final String MOD_ID = "pirates_n_ships";
    /** Human-readable mod name. */
    public static final String MOD_NAME = "Pirates 'n' Ships";
    /** Shared logger. */
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    private Constants() {
    }

    /** Returns {@code pirates_n_ships:<path>}. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
