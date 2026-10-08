package com.richardsenger.piratesnships.crew.hiring;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;

import java.util.List;

/**
 * The hiring module {@code crew.hiring} (docs/design.md §7.1, §7.5, CRW1): candidates per port, the Crew tab of the
 * harbor desk, hiring onto the player's ship moored at the port, dismissal with the whistle, {@code /pirates crew hire}
 * and {@code dismiss}. The Crew tab's payloads are registered with the market's ({@code trade.net.MarketBackend}),
 * whose desk sessions they use; the whistle's sneak-use calls {@link Hiring#dismiss}.
 */
public final class HiringModule implements ModModule {

    @Override
    public String id() {
        return "crew.hiring";
    }

    @Override
    public void registerConfig() {
        HiringConfig.init();
    }

    @Override
    public void registerEvents() {
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> HiringCommands.register(dispatcher));
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(HiringText::lang);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(HiringGameTests.class);
    }
}
