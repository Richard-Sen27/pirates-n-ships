package com.richardsenger.piratesnships.worldsim.faction;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;

import java.util.List;

/**
 * The {@code worldsim.faction} module (WS1, design.md §10.4): the saved faction state of Navy, Pirates and Merchants,
 * shifted by world events and deeds, decaying every overworld day; {@code /pirates world factions}.
 */
public final class FactionModule implements ModModule {

    @Override
    public String id() {
        return "worldsim.faction";
    }

    @Override
    public void registerConfig() {
        FactionConfig.init();
    }

    @Override
    public void registerEvents() {
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> FactionCommands.register(dispatcher));
        CommonEvents.SERVER_TICK_END.register(Factions::observeDay);
        FactionDeeds.register();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(FactionCommands::lang);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(FactionGameTests.class);
    }
}
