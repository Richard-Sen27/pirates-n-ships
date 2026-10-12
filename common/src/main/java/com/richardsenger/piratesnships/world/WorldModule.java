package com.richardsenger.piratesnships.world;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.world.island.IslandData;
import com.richardsenger.piratesnships.world.island.PirateIslandSpawns;
import com.richardsenger.piratesnships.world.outpost.OutpostData;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.world.port.PortArguments;
import com.richardsenger.piratesnships.world.port.PortNameBook;
import com.richardsenger.piratesnships.world.port.PortService;
import com.richardsenger.piratesnships.world.structure.ConnectionsGameTests;
import com.richardsenger.piratesnships.world.structure.StructureBlockEntityGameTests;
import com.richardsenger.piratesnships.world.structure.StructureLayoutGameTests;
import com.richardsenger.piratesnships.world.structure.ConnectionsProcessor;
import com.richardsenger.piratesnships.world.structure.PortStructures;
import com.richardsenger.piratesnships.world.treasure.TreasureMapGameTests;
import com.richardsenger.piratesnships.world.treasure.TreasureMaps;
import com.richardsenger.piratesnships.world.village.VillageData;
import com.richardsenger.piratesnships.world.wreck.WreckData;
import com.richardsenger.piratesnships.world.wreck.WreckGameTests;
import com.richardsenger.piratesnships.world.wreck.WreckLoot;
import com.richardsenger.piratesnships.world.wreck.WreckStructures;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * The {@code world} module (design.md §10.1, §10.4, WG1, WG2, WG3, WK1): the port structures (custom structure type
 * {@code pirates_n_ships:port_village} over vanilla jigsaw pools: the seafarer village, the pirate island with its
 * buried treasure and pirate spawns, and the navy outpost with its garrison), the port registry with berths and
 * treasure sites, the binding of harbor desks to the port they stand in, and the wrecks on the ocean floor (custom
 * structure type {@code pirates_n_ships:wreck}). Port pieces and wrecks share the connections processor (WG4).
 * Also the treasure maps (TM1, {@link TreasureMaps}) and the ports' proper names (NAME3, {@link PortNameBook}).
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
        WreckStructures.init();
        TreasureMaps.registerContent();
        // The pirate's spawn rule lives here because its only natural spawns are the island's (WG2)
        Services.REGISTRY.registerSpawnPlacement(MobContent.PIRATE, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PirateIslandSpawns::check);
    }

    @Override
    public void registerPayloads() {
        Services.NETWORK.registerToClient(PortNameBook.Sync.TYPE, PortNameBook.Sync.CODEC, (p, player) -> PortNameBook.accept(p));
    }

    @Override
    public void registerEvents() {
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> WorldCommands.register(dispatcher));
        CommonEvents.SERVER_STARTED.register(server -> {
            PortService.installDeskLocator();
            PortNameBook.installServer(server);
            PortService.openAllMarkets(server);
        });
        CommonEvents.SERVER_STOPPED.register(server -> {
            PortService.removeDeskLocator();
            PortNameBook.removeServer();
        });
        CommonEvents.PLAYER_LOGIN.register(PortNameBook::sendTo);
        TreasureMaps.registerEvents();
    }

    @Override
    public void initClient() {
        ClientEvents.CLIENT_DISCONNECT.register(mc -> PortNameBook.clearClient());
        TreasureMaps.initClient();
    }

    @Override
    public void gatherData(DataContributions data) {
        ConnectionsProcessor.gather(data);
        com.richardsenger.piratesnships.world.structure.PortStructureData.gatherShores(data);
        VillageData.gather(data);
        WreckLoot.gather(data);
        com.richardsenger.piratesnships.world.structure.StructureLoot.gather(data);
        IslandData.gather(data);
        WreckData.gather(data);
        OutpostData.gather(data);
        data.lang(WorldCommands::lang);
        data.lang(PortArguments::lang);
        TreasureMaps.gatherData(data);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(WorldGameTests.class, PirateIslandGameTests.class, NavyOutpostGameTests.class, WreckGameTests.class,
                TreasureMapGameTests.class, ConnectionsGameTests.class,
                StructureBlockEntityGameTests.class, StructureLayoutGameTests.class,
                com.richardsenger.piratesnships.world.structure.OutpostLayoutGameTests.class,
                com.richardsenger.piratesnships.world.village.VillageLayoutGameTests.class,
                com.richardsenger.piratesnships.world.structure.SpreadPlacementGameTests.class,
                com.richardsenger.piratesnships.world.village.BerthDepthGameTests.class,
                com.richardsenger.piratesnships.world.port.ShipSpotGameTests.class,

                com.richardsenger.piratesnships.world.structure.StructureLootGameTests.class,
                com.richardsenger.piratesnships.world.structure.DoorAccessGameTests.class,
                com.richardsenger.piratesnships.world.structure.PortSiteGameTests.class,
                com.richardsenger.piratesnships.world.structure.PortTerrainGameTests.class,
                com.richardsenger.piratesnships.world.port.PortNameGameTests.class);
    }
}
