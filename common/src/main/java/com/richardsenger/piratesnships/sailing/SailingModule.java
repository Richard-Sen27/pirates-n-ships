package com.richardsenger.piratesnships.sailing;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.sailing.wind.WindSync;

import java.util.List;

/**
 * The {@code sailing} module (docs/design.md §5): the wind field, its client sync, and the pure force model for
 * sails, rudder, keel and anchor ({@code sailing.force}). Applying the forces to Sable ships is up to the ship
 * integration.
 */
public final class SailingModule implements ModModule {

    @Override
    public String id() {
        return "sailing";
    }

    @Override
    public void registerConfig() {
        SailingConfig.init();
    }

    @Override
    public void registerPayloads() {
        WindSync.registerPayloads();
    }

    @Override
    public void registerEvents() {
        CommonEvents.SERVER_TICK_END.register(WindSync::onServerTick);
        CommonEvents.PLAYER_LOGIN.register(WindSync::onLogin);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(SailingGameTests.class);
    }
}
