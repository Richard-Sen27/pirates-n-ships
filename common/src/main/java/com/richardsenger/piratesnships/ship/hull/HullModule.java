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
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullViewGameTests;
import com.richardsenger.piratesnships.ship.hull.client.FloodSurfaceStore;
import com.richardsenger.piratesnships.ship.hull.runtime.FloodSurfacePayload;
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
        com.richardsenger.piratesnships.ship.hull.pump.PumpVisualsConfig.init(); // the rocking pump handle (PMP1)
    }

    @Override
    public void registerContent() {
        ShipForces.register();
        HullRepairContent.init();
    }

    @Override
    public void registerPayloads() {
        Services.NETWORK.registerToClient(HullRegionsPayload.TYPE, HullRegionsPayload.CODEC, HullRuntimes::onRegionsPayload);
        // flood surfaces (FLD1): plain data into the client store, drawn by client.FloodSurfaceRenderer
        Services.NETWORK.registerToClient(FloodSurfacePayload.TYPE, FloodSurfacePayload.CODEC,
                (p, player) -> FloodSurfaceStore.CLIENT.accept(p, player.level().getGameTime()));
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
        SableShips.onClientShipRemoved((level, ship) -> FloodSurfaceStore.CLIENT.remove(ship));
        SableShips.onPhysicsTick(HullRuntimes::onPhysicsTick);
        com.richardsenger.piratesnships.ship.ShipBlockChanges.register(HullRuntimes::onBlockChanged);
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.ship.hull.client.ShipHudClient.init();
        // water plants of the world are not drawn inside dry hulls (HV1)
        com.richardsenger.piratesnships.platform.event.ClientEvents.CLIENT_TICK_END.register(
                com.richardsenger.piratesnships.ship.hull.client.HiddenWaterPlants::tick);
        com.richardsenger.piratesnships.platform.event.ClientEvents.CLIENT_DISCONNECT.register(
                mc -> com.richardsenger.piratesnships.ship.hull.client.HiddenWaterPlants.reset());
        // the water surface inside flooded compartments (FLD1)
        com.richardsenger.piratesnships.ship.hull.client.FloodSurfaceRenderer.init();
        // the bilge pump's handle rocks while it is worked (PMP1)
        com.richardsenger.piratesnships.ship.hull.pump.client.PumpClient.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        // Pack-maker overrides on top of the built-in rules of world.HullBlockClassifier (which read vanilla tags such
        // as #minecraft:slabs directly). Only our hull patch is in WATERTIGHT (pump.HullRepairData).
        data.blockTags(tags -> {
            tags.tag(HullTags.WATERTIGHT);
            tags.tag(HullTags.NOT_WATERTIGHT);
            // world blocks the client hides inside dry hulls (HV1)
            tags.tag(HullTags.HIDDEN_IN_DRY_HULL).add(net.minecraft.world.level.block.Blocks.SEAGRASS,
                    net.minecraft.world.level.block.Blocks.TALL_SEAGRASS, net.minecraft.world.level.block.Blocks.KELP,
                    net.minecraft.world.level.block.Blocks.KELP_PLANT, net.minecraft.world.level.block.Blocks.SEA_PICKLE,
                    net.minecraft.world.level.block.Blocks.BUBBLE_COLUMN);
        });
        data.lang(lang -> lang.add(ShipForces.BUOYANCY_KEY, "Hull Buoyancy"));
        HullRepairData.gather(data); // bilge pump and hull patch (G4)
        ShipHudText.gather(data); // ship HUD (HUD1)
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(HullGameTests.class, DryHullGameTests.class, DryHullViewGameTests.class,
                PumpPatchGameTests.class, ShipStatusGameTests.class);
    }
}
