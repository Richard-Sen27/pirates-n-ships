package com.richardsenger.piratesnships.worldsim.raid;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageKind;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageScheduler;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;

import java.util.List;

/**
 * The {@code worldsim.raid} module (WS5, design.md §10.4 "Pirate raids on navy settlements", "Retaliation"): the
 * rising raid chance at settlements with players ({@link RaidTracker}), the RAID voyages ({@link RaidPlanner}), the
 * announcement and bells ({@link RaidAnnouncer}), the landing and withdrawal ({@link RaidLanding}) and
 * {@code /pirates world raid}. Config {@code world_simulation.raids.*}.
 */
public final class RaidModule implements ModModule {

    @Override
    public String id() {
        return "worldsim.raid";
    }

    @Override
    public void registerConfig() {
        RaidConfig.init();
    }

    @Override
    public void registerEvents() {
        VoyageScheduler.register(VoyageKind.RAID, new RaidPlanner());
        CommonEvents.SERVER_TICK_END.register(RaidTracker::onServerTick);
        CommonEvents.SERVER_TICK_END.register(RaidLanding::onServerTick);
        CommonEvents.SERVER_TICK_END.register(RaidAnnouncer::onServerTick);
        CommonEvents.SERVER_STOPPED.register(server -> {
            RaidAnnouncer.clear();
            RaidPlanner.clear();
        });
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> RaidCommands.register(dispatcher));
        Voyages.onEnd(RaidLanding::onVoyageEnded);
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(RaidCommands::lang);
        data.lang(RaidAnnouncer::lang);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(RaidGameTests.class);
    }
}
