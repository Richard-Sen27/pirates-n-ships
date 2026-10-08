package com.richardsenger.piratesnships.world;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.world.island.IslandData;
import com.richardsenger.piratesnships.world.island.PirateIslandSpawns;
import com.richardsenger.piratesnships.world.outpost.OutpostData;
import com.richardsenger.piratesnships.world.port.PortService;
import com.richardsenger.piratesnships.world.structure.PortStructures;
import com.richardsenger.piratesnships.world.treasure.TreasureMapGameTests;
import com.richardsenger.piratesnships.world.treasure.TreasureMaps;
import com.richardsenger.piratesnships.world.village.VillageData;
import com.richardsenger.piratesnships.world.wreck.WreckLoot;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * The {@code world} module (design.md §10.1, §10.4, WG1, WG2, WG3): the port structures (custom structure type
 * {@code pirates_n_ships:port_village} over vanilla jigsaw pools: the seafarer village, the pirate island with its
 * buried treasure and pirate spawns, and the navy outpost with its garrison), the port registry with berths and treasure sites, and the binding of harbor
 * desks to the port they stand in; the treasure maps (TM1, {@link TreasureMaps}).
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
        TreasureMaps.registerContent();
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
        TreasureMaps.registerEvents();
    }

    @Override
    public void initClient() {
        TreasureMaps.initClient();
    }

    @Override
    public void gatherData(DataContributions data) {
        VillageData.gather(data);
        WreckLoot.gather(data);
        IslandData.gather(data);
        OutpostData.gather(data);
        data.lang(WorldCommands::lang);
        TreasureMaps.gatherData(data);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(WorldGameTests.class, PirateIslandGameTests.class, NavyOutpostGameTests.class, TreasureMapGameTests.class);
    }
}
