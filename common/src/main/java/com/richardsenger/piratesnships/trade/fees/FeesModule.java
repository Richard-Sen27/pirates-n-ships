package com.richardsenger.piratesnships.trade.fees;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;

import java.util.List;

/**
 * Harbor dues charged in game (work package PRT1b, docs/design.md §10.3, plan {@code docs/plans/crew-and-ports.md}):
 * {@link DockingFees} on the level tick, {@link PortVisitData}, the desk's dues check. The config lives in
 * {@code TradeConfig}'s {@code cargo_trade.port_fees} section.
 */
public final class FeesModule implements ModModule {

    @Override
    public String id() {
        return "trade.fees";
    }

    @Override
    public void registerEvents() {
        CommonEvents.LEVEL_TICK_END.register(DockingFees::onLevelTick);
    }

    @Override
    public void gatherData(DataContributions data) {
        FeeText.lang(data);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(FeesGameTests.class);
    }
}
