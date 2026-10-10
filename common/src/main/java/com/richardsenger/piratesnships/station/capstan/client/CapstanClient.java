package com.richardsenger.piratesnships.station.capstan.client;

import com.richardsenger.piratesnships.sailing.block.WinchAndCapstanData;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.ship.decor.wood.client.WoodModels;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.station.capstan.CapstanContent;
import net.minecraft.resources.ResourceLocation;

/** Client setup of the capstan in motion (CAP2; physical client only, from {@code StationModule.initClient()}). */
public final class CapstanClient {

    /**
     * The hand-made bars and drumhead ({@code models/block/capstan_bars.json}); {@link CapstanBarsRenderer} draws the
     * child of the capstan's wood ({@code block/<wood>_capstan_bars}, WOOD4).
     */
    static final ResourceLocation BARS_MODEL = WinchAndCapstanData.CAPSTAN_BARS_MODEL;

    private CapstanClient() {
    }

    public static void init() {
        WoodModels.register(SailingBlocks.CAPSTANS, BARS_MODEL);
        ClientEvents.registerBlockEntityRenderer(CapstanContent.CAPSTAN_ENTITY, CapstanBarsRenderer::new);
    }
}
