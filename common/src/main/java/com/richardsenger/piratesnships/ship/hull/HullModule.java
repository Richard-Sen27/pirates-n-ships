package com.richardsenger.piratesnships.ship.hull;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.ship.hull.pump.HullRepairContent;
import com.richardsenger.piratesnships.ship.hull.pump.HullRepairData;
import com.richardsenger.piratesnships.ship.hull.pump.PumpPatchGameTests;
import com.richardsenger.piratesnships.ship.hull.client.ClientShipStatus;
import com.richardsenger.piratesnships.ship.hull.client.ShipHudConfig;
import com.richardsenger.piratesnships.ship.hull.client.ShipHudText;
import com.richardsenger.piratesnships.ship.hull.net.ShipStatusPayload;
import com.richardsenger.piratesnships.ship.hull.net.ShipStatusSync;
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
 * occlusion of dry compartments, their client sync, flooding ticks and the buoyancy correction. The counter-measures
 * to flooding ({@code pump.*}, G4): the bilge pump (also a crew station) and the hull patch. The ship HUD
 * ({@code net.*} sends the status, {@code client.*} draws it, HUD1).
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
        ShipHudConfig.init();
    }

    @Override
    public void registerContent() {
        ShipForces.register();
        HullRepairContent.init();
    }

    @Override
    public void registerPayloads() {
        Services.NETWORK.registerToClient(HullRegionsPayload.TYPE, HullRegionsPayload.CODEC, HullRuntimes::onRegionsPayload);
        Services.NETWORK.registerToClient(ShipStatusPayload.TYPE, ShipStatusPayload.CODEC, (p, player) -> ClientShipStatus.accept(p));
    }

    @Override
    public void registerEvents() {
        CommonEvents.LEVEL_TICK_END.register(HullRuntimes::onLevelTick);
        CommonEvents.SERVER_STOPPED.register(server -> HullRuntimes.onServerStopped());
        CommonEvents.LEVEL_TICK_END.register(ShipStatusSync::onLevelTick); // ship HUD (HUD1)
        CommonEvents.SERVER_STOPPED.register(server -> ShipStatusSync.onServerStopped());
        SableShips.onShipRemoved(HullRuntimes::onShipRemoved);
        SableShips.onClientShipRemoved(HullRuntimes::onClientShipRemoved);
        SableShips.onPhysicsTick(HullRuntimes::onPhysicsTick);
        com.richardsenger.piratesnships.ship.ShipBlockChanges.register(HullRuntimes::onBlockChanged);
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.ship.hull.client.ShipHudClient.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        // Pack-maker overrides on top of the built-in rules of world.HullBlockClassifier (which read vanilla tags such
        // as #minecraft:slabs directly). Only our hull patch is in WATERTIGHT (pump.HullRepairData).
        data.blockTags(tags -> {
            tags.tag(HullTags.WATERTIGHT);
            tags.tag(HullTags.NOT_WATERTIGHT);
        });
        data.lang(lang -> lang.add(ShipForces.BUOYANCY_KEY, "Hull Buoyancy"));
        HullRepairData.gather(data); // bilge pump and hull patch (G4)
        ShipHudText.gather(data); // ship HUD (HUD1)
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(HullGameTests.class, DryHullGameTests.class, PumpPatchGameTests.class,
                ShipStatusGameTests.class);
    }
}
