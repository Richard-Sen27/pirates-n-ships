package com.richardsenger.piratesnships.station.client;

import com.richardsenger.piratesnships.crew.npc.client.CrewClient;
import com.richardsenger.piratesnships.station.order.WhistleMenu;

/** Client init of the station module (physical client only): crew rendering and the whistle's radial menu. */
public final class StationClient {

    private StationClient() {
    }

    public static void init() {
        CrewClient.init();
        WhistleMenu.setOpener(WhistleMenuScreen::open);
    }
}
