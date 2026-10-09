package com.richardsenger.piratesnships.station.capstan.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.station.capstan.CapstanContent;
import net.minecraft.resources.ResourceLocation;

/** Client setup of the capstan in motion (CAP2; physical client only, from {@code StationModule.initClient()}). */
public final class CapstanClient {

    /** The hand-made bars and drumhead ({@code models/block/capstan_bars.json}), drawn by {@link CapstanBarsRenderer}. */
    static final ResourceLocation BARS_MODEL = Constants.id("block/capstan_bars");

    private CapstanClient() {
    }

    public static void init() {
        ClientEvents.registerAdditionalModel(BARS_MODEL);
        ClientEvents.registerBlockEntityRenderer(CapstanContent.CAPSTAN_ENTITY, CapstanBarsRenderer::new);
    }
}
