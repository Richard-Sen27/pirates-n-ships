package com.richardsenger.piratesnships.ship.screen;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.ship.assembly.HelmBlock;
import java.util.List;

/**
 * Module {@code ship.screen} (HGUI1, docs/design.md §7.2): the ship screen at the helm. Sneak-use with an empty hand on
 * the helm of an assembled ship opens a server-fed screen for its owner to manage the ship (name, flag, hull, load,
 * anchor and sails, provisions, pay, disassembly) and its crew (the whistle's orders, release, dismissal, station
 * assignments). Installs {@link HelmBlock.ManageHandler}; the whistle stays as it is.
 */
public final class ShipScreenModule implements ModModule {

    @Override
    public String id() {
        return "ship.screen";
    }

    @Override
    public void registerConfig() {
        ShipScreenConfig.init();
    }

    @Override
    public void registerContent() {
        HelmBlock.setManageHandler(ShipScreens::onSneakUse);
    }

    @Override
    public void registerPayloads() {
        ShipScreens.registerPayloads();
    }

    @Override
    public void registerEvents() {
        CommonEvents.SERVER_TICK_END.register(ShipScreens::onServerTick);
        CommonEvents.PLAYER_LOGOUT.register(ShipScreens::onLogout);
        CommonEvents.SERVER_STOPPED.register(server -> ShipScreens.clear());
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.ship.screen.client.ShipScreenClient.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(ShipScreenText::lang);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(ShipScreenGameTests.class);
    }
}
