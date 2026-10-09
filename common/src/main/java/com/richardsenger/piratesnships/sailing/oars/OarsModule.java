package com.richardsenger.piratesnships.sailing.oars;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.ship.assembly.ShipSplits;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import java.util.List;

/**
 * Oars (OAR1, docs/design.md §5.3b): the whistle's Row / Ship oars toggle ({@link Rowing}), the crew rowing at the rail
 * ({@link OarSpots}, {@link OarSeat}) or seated on an oar bench ({@link OarBenchBlock}, {@link OarBenchStation}), the
 * oars' push ({@link OarRules}, {@link OarThrust}), the oar item and the label sync ({@link OarsSync}).
 */
public final class OarsModule implements ModModule {

    @Override
    public String id() {
        return "oars";
    }

    @Override
    public void registerConfig() {
        OarsConfig.init();
    }

    @Override
    public void registerContent() {
        OarsContent.init();
    }

    @Override
    public void registerPayloads() {
        OarsSync.registerPayloads();
    }

    @Override
    public void registerEvents() {
        CommonEvents.LEVEL_TICK_END.register(Rowing::onLevelTick);
        CommonEvents.LEVEL_TICK_END.register(OarsSync::onLevelTick);
        SableShips.onPhysicsTick(OarThrust::onPhysicsTick);
        SableShips.onShipRemoved(Rowing::onShipRemoved);
        ShipSplits.onSplit(Rowing::onSplit);
        OarBenchStation.registerPoses();
        CommonEvents.SERVER_STOPPED.register(server -> {
            Rowing.onServerStopped();
            OarsSync.onServerStopped();
        });
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.sailing.oars.client.OarsClient.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        OarsData.gather(data);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(OarGameTests.class);
    }
}
