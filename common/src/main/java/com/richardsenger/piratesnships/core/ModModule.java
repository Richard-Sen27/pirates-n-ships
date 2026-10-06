package com.richardsenger.piratesnships.core;

import com.richardsenger.piratesnships.core.datagen.DataContributions;

import java.util.List;

/**
 * One feature module (docs/design.md §3.2). Each module implements this once (e.g. {@code ship.ShipModule}) and is
 * listed in {@link ModModules}. All hooks are optional and run during mod construction, in this order for every
 * module: {@link #registerConfig()}, {@link #registerContent()}, {@link #registerPayloads()},
 * {@link #registerEvents()}, then {@link #initClient()} on the physical client.
 */
public interface ModModule {

    /** The module id, equal to its package name (e.g. {@code "ship"}). */
    String id();

    /** Declare config sections (touch your {@code XxxConfig} classes so their static fields are created). */
    default void registerConfig() {
    }

    /** Declare registry entries via {@code ModRegistry} and register attachment keys. */
    default void registerContent() {
    }

    /** Register payloads via {@code Services.NETWORK}. */
    default void registerPayloads() {
    }

    /** Register listeners on {@code CommonEvents}. */
    default void registerEvents() {
    }

    /**
     * Physical client only: register on {@code ClientEvents} (renderers, HUD, key mappings). Delegate to a separate
     * client class so this module class never links client-only classes on a dedicated server.
     */
    default void initClient() {
    }

    /** Contribute datagen output (lang, models, recipes, loot, tags). Only runs in {@code runData}. */
    default void gatherData(DataContributions data) {
    }

    /** GameTest classes of this module (see {@code core.gametest.ModGameTest}). */
    default List<Class<?>> gameTestClasses() {
        return List.of();
    }
}
