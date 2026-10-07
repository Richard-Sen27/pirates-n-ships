package com.richardsenger.piratesnships.hazards.client;

import com.richardsenger.piratesnships.hazards.HazardsContent;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.renderer.entity.NoopRenderer;

/**
 * Client registration of the hazards (physical client only): the entities themselves draw nothing (vanilla
 * {@link NoopRenderer}); their particles and sounds come from the entity's client tick
 * ({@code hazards.HazardVisuals}).
 */
public final class HazardsClient {

    private HazardsClient() {
    }

    public static void init() {
        ClientEvents.registerEntityRenderer(HazardsContent.WATERSPOUT, NoopRenderer::new);
        ClientEvents.registerEntityRenderer(HazardsContent.WHIRLPOOL, NoopRenderer::new);
    }
}
