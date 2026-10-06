package com.richardsenger.piratesnships.ship.hull;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullConfig;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRegionsPayload;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipForces;

import java.util.List;

/**
 * The {@code ship.hull} module: hull analysis (compartments, docs/design.md §4.2) and the flooding model (§4.5). Pure
 * logic plus a small world adapter ({@code world.*}), and the per-ship runtime ({@code runtime.*}, spike 2): water
 * occlusion of dry compartments, their client sync, flooding ticks and the buoyancy correction.
 */
public final class HullModule implements ModModule {

    @Override
    public String id() {
        return "ship.hull";
    }

    @Override
    public void registerConfig() {
        FloodingConfig.init();
        DryHullConfig.init();
    }

    @Override
    public void registerContent() {
        ShipForces.register();
    }

    @Override
    public void registerPayloads() {
        Services.NETWORK.registerToClient(HullRegionsPayload.TYPE, HullRegionsPayload.CODEC, HullRuntimes::onRegionsPayload);
    }

    @Override
    public void registerEvents() {
        CommonEvents.LEVEL_TICK_END.register(HullRuntimes::onLevelTick);
        CommonEvents.SERVER_STOPPED.register(server -> HullRuntimes.onServerStopped());
        SableShips.onShipRemoved(HullRuntimes::onShipRemoved);
        SableShips.onClientShipRemoved(HullRuntimes::onClientShipRemoved);
        SableShips.onPhysicsTick(HullRuntimes::onPhysicsTick);
    }

    @Override
    public void gatherData(DataContributions data) {
        // Both tags ship empty: they are pack-maker overrides on top of the built-in rules of
        // world.HullBlockClassifier (which read vanilla tags such as #minecraft:slabs directly).
        data.blockTags(tags -> {
            tags.tag(HullTags.WATERTIGHT);
            tags.tag(HullTags.NOT_WATERTIGHT);
        });
        data.lang(lang -> lang.add(ShipForces.BUOYANCY_KEY, "Hull Buoyancy"));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(HullGameTests.class, DryHullGameTests.class);
    }
}
