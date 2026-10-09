package com.richardsenger.piratesnships.ship.hull.pump.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.ship.hull.pump.HullRepairContent;
import net.minecraft.resources.ResourceLocation;

/** Client setup of the bilge pump (PMP1; physical client only, from {@code HullModule.initClient()}). */
public final class PumpClient {

    /** The hand-made stand-alone parts {@link PumpHandleRenderer} draws (models/block/*.json). */
    static final ResourceLocation HANDLE_MODEL = Constants.id("block/bilge_pump_handle");
    static final ResourceLocation ROD_MODEL = Constants.id("block/bilge_pump_rod");

    private PumpClient() {
    }

    public static void init() {
        ClientEvents.registerAdditionalModel(HANDLE_MODEL);
        ClientEvents.registerAdditionalModel(ROD_MODEL);
        ClientEvents.registerBlockEntityRenderer(HullRepairContent.BILGE_PUMP_ENTITY, PumpHandleRenderer::new);
    }
}
