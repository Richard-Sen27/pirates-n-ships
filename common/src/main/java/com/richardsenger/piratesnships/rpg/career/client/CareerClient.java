package com.richardsenger.piratesnships.rpg.career.client;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.rpg.career.ClientCareer;

/** Client side of the careers module (physical client only, from {@code CareerModule.initClient()}). */
public final class CareerClient {

    private CareerClient() {
    }

    public static void init() {
        ClientEvents.CLIENT_DISCONNECT.register(mc -> ClientCareer.reset());
        ClientCareer.setOpener(CareerScreen::open);
        RankHud.init(); // HON1
    }
}
