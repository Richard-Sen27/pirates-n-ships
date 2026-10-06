package com.richardsenger.piratesnships.core.config;

/** Which config file a value lives in (docs/design.md §17). */
public enum ConfigType {
    /** Gameplay settings. Per world, synced from the server to clients. */
    SERVER,
    /** Audio and visuals. Per client installation. */
    CLIENT
}
