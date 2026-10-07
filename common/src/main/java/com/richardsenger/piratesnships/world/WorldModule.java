package com.richardsenger.piratesnships.world;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.world.island.IslandData;
import com.richardsenger.piratesnships.world.island.PirateIslandSpawns;
import com.richardsenger.piratesnships.world.port.PortService;
import com.richardsenger.piratesnships.world.structure.PortStructures;
import com.richardsenger.piratesnships.world.village.VillageData;
import com.richardsenger.piratesnships.world.wreck.WreckLoot;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * The {@code world} module (design.md §10.1, §10.4, WG1, WG2): the port structures (custom structure type
 * {@code pirates_n_ships:port_village} over vanilla jigsaw pools: the seafarer village and the pirate island with its
 * buried treasure and pirate spawns), the port registry with berths and treasure sites, and the binding of harbor
 * desks to the port they stand in.
 */
public final class WorldModule implements ModModule {

    @Override
    public String id() {
        return "world";
    }

    @Override
    public void registerConfig() {
        WorldConfig.init();
    }

    @Override
    public void registerContent() {
        PortStructures.init();
        // The pirate's spawn rule lives here because its only natural spawns are the island's (WG2)
        Services.REGISTRY.registerSpawnPlacement(MobContent.PIRATE, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PirateIslandSpawns::check);
    }

    @Override
    public void registerEvents() {
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> WorldCommands.register(dispatcher));
        CommonEvents.SERVER_STARTED.register(server -> {
            PortService.installDeskLocator();
            PortService.openAllMarkets(server);
        });
        CommonEvents.SERVER_STOPPED.register(server -> PortService.removeDeskLocator());
    }

    @Override
    public void gatherData(DataContributions data) {
        VillageData.gather(data);
        WreckLoot.gather(data);
        IslandData.gather(data);
        data.lang(WorldCommands::lang);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(WorldGameTests.class, PirateIslandGameTests.class);
    }
}
