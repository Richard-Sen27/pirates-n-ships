package com.richardsenger.piratesnships.ship.hull;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;

import java.util.List;

/**
 * The {@code ship.hull} module: hull analysis (compartments, docs/design.md §4.2) and the flooding model (§4.5). Pure
 * logic plus a small world adapter ({@code world.*}); the ship integration (Sable, sync, forces) lives elsewhere.
 */
public final class HullModule implements ModModule {

    @Override
    public String id() {
        return "ship.hull";
    }

    @Override
    public void registerConfig() {
        FloodingConfig.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        // Both tags ship empty: they are pack-maker overrides on top of the built-in rules of
        // world.HullBlockClassifier (which read vanilla tags such as #minecraft:slabs directly).
        data.blockTags(tags -> {
            tags.tag(HullTags.WATERTIGHT);
            tags.tag(HullTags.NOT_WATERTIGHT);
        });
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(HullGameTests.class);
    }
}
