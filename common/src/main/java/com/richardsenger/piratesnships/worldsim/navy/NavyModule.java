package com.richardsenger.piratesnships.worldsim.navy;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageKind;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageScheduler;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;

import java.util.List;

/**
 * The {@code worldsim.navy} module (WS4b, design.md §10.4 "Navy patrols"): navy patrols between the outposts
 * ({@link PatrolPlanner}, the PATROL planner of the voyage scheduler) that hunt Jolly Roger ships and wanted captains
 * ({@link Hunting}), engage them with the gun crews (WS4a) and break off on surrender; {@code /pirates world patrols}.
 * Config {@code world_simulation.navy.*}. Registered after {@code worldsim.materialize}, so on a voyage check the
 * materialiser has run before the hunt.
 */
public final class NavyModule implements ModModule {

    static final PatrolPlanner PATROLS = new PatrolPlanner();

    @Override
    public String id() {
        return "worldsim.navy";
    }

    @Override
    public void registerConfig() {
        NavyConfig.init();
    }

    @Override
    public void registerEvents() {
        VoyageScheduler.register(VoyageKind.PATROL, PATROLS);
        CommonEvents.SERVER_TICK_END.register(Hunting::onServerTick);
        CommonEvents.SERVER_STOPPED.register(server -> {
            PATROLS.clear();
            Hunting.onServerStopped();
        });
        CommonEvents.LIVING_DEATH.register(NavyKills::onDeath);
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> NavyCommands.register(dispatcher));
        Voyages.onEnd(Hunting::onVoyageEnded);
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(Hunting::lang);
        data.lang(NavyCommands::lang);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(NavyGameTests.class);
    }
}
