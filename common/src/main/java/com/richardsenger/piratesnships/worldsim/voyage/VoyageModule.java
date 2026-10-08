package com.richardsenger.piratesnships.worldsim.voyage;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.trade.desk.HarborDeskService;
import com.richardsenger.piratesnships.worldsim.lane.Lanes;

import java.util.List;

/**
 * The {@code worldsim.voyage} module (WS2, design.md §10.4): sea lanes between ports ({@code worldsim.lane}),
 * abstract voyage records moved by {@link VoyageScheduler} on the server tick, merchant convoys that buy at their origin
 * and sell at their destination, lane distances and pirate risk for harbor master contracts, and
 * {@code /pirates world voyages}. Config {@code world_simulation.lanes.*} and {@code world_simulation.voyages.*}.
 */
public final class VoyageModule implements ModModule {

    @Override
    public String id() {
        return "worldsim.voyage";
    }

    @Override
    public void registerConfig() {
        VoyageConfig.init();
    }

    @Override
    public void registerEvents() {
        CommonEvents.SERVER_TICK_END.register(VoyageScheduler::onServerTick);
        CommonEvents.SERVER_STOPPED.register(server -> {
            Lanes.clear();
            VoyageScheduler.clear();
        });
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> VoyageCommands.register(dispatcher));
        HarborDeskService.setRouteLocator((server, origin, destination) -> Lanes.route(server, origin, destination)
                .map(r -> new HarborDeskService.ContractRoute(r.distance(), r.risk())));
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(VoyageCommands::lang);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(VoyageGameTests.class);
    }
}
