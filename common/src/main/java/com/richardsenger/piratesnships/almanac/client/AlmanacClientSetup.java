package com.richardsenger.piratesnships.almanac.client;

import com.richardsenger.piratesnships.almanac.AlmanacContent;
import com.richardsenger.piratesnships.platform.event.ClientEvents;

/** Client setup of the {@code almanac} module (physical client only, from {@code AlmanacModule.initClient()}). */
public final class AlmanacClientSetup {

    private AlmanacClientSetup() {
    }

    public static void init() {
        // The placed tome: vanilla's book model lying on the block, opening and turning pages while read (ALM1)
        ClientEvents.registerBlockEntityRenderer(AlmanacContent.ALMANAC_BLOCK_ENTITY, AlmanacRenderer::new);
    }
}
