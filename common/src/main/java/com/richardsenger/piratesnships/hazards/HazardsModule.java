package com.richardsenger.piratesnships.hazards;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipForces;

import java.util.List;

/**
 * The {@code hazards} module (work package H1, docs/design.md §12): waterspouts that form over the ocean in
 * thunderstorms and whirlpools in the deep ocean, as invisible entities with a force field on entities, boats and Sable
 * ships, sail tearing, natural forming near players at sea, {@code /pirates hazard} and client particles. Config
 * sections {@code hazards} (server) and {@code hazard_visuals} (client). The kraken is not part of it.
 */
public final class HazardsModule implements ModModule {

    @Override
    public String id() {
        return "hazards";
    }

    @Override
    public void registerConfig() {
        HazardsConfig.init();
    }

    @Override
    public void registerContent() {
        HazardsContent.init();
        ShipForces.registerHazards();
    }

    @Override
    public void registerPayloads() {
        Services.NETWORK.registerToClient(HazardPushPayload.TYPE, HazardPushPayload.CODEC, HazardPushPayload::apply);
    }

    @Override
    public void registerEvents() {
        CommonEvents.LEVEL_TICK_END.register(HazardSpawner::onLevelTick);
        CommonEvents.SERVER_STOPPED.register(server -> HazardShipForces.clear());
        SableShips.onPhysicsTick(HazardShipForces::onPhysicsTick);
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> HazardCommands.register(dispatcher));
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.hazards.client.HazardsClient.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .add(HazardsContent.WATERSPOUT.get().getDescriptionId(), "Waterspout")
                .add(HazardsContent.WHIRLPOOL.get().getDescriptionId(), "Whirlpool")
                .add(HazardCommands.KEY_SPAWNED, "Spawned a %s at %s %s %s")
                .add(HazardCommands.KEY_UNKNOWN, "Unknown hazard: %s (waterspout or whirlpool)")
                .add(HazardCommands.KEY_DISABLED, "%s is disabled in the server config (hazards)")
                .add(HazardCommands.KEY_FAILED, "Could not spawn the %s here")
                .add(HazardCommands.KEY_CLEARED, "Removed %s hazards")
                .add(ShipForces.HAZARDS_KEY, "Sea Hazards"));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(HazardGameTests.class);
    }
}
