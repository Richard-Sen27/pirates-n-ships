package com.richardsenger.piratesnships.world;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.world.port.PortService;
import com.richardsenger.piratesnships.world.village.VillageData;
import com.richardsenger.piratesnships.world.village.VillageStructures;

import java.util.List;

/**
 * The {@code world} module (design.md §10.1, §10.4, WG1): the seafarer village structure (custom structure type
 * {@code pirates_n_ships:port_village} over vanilla jigsaw pools), the port registry with berths, and the binding of
 * harbor desks to the port they stand in.
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
        VillageStructures.init();
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
        data.lang(WorldCommands::lang);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(WorldGameTests.class);
    }
}
