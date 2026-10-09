package com.richardsenger.piratesnships.combat.boarding.client;

import com.richardsenger.piratesnships.combat.boarding.BoardingContent;
import com.richardsenger.piratesnships.platform.event.ClientEvents;

/** Client setup of the boarding module (physical client only, from {@code BoardingModule.initClient()}). */
public final class BoardingClient {

    private BoardingClient() {
    }

    public static void init() {
        // BRD4: the straight board is drawn from the run's segment 0
        ClientEvents.registerBlockEntityRenderer(BoardingContent.PLANK_BLOCK_ENTITY, BoardingPlankRenderer::new);
        PlankPreview.init();
    }
}
