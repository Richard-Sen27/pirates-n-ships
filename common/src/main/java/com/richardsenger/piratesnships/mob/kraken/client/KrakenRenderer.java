package com.richardsenger.piratesnships.mob.kraken.client;

import com.richardsenger.piratesnships.mob.kraken.Kraken;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * GeckoLib renderer of the kraken: the model as it is with a wide shadow. The tentacles reach far beyond the body, so
 * the kraken's culling box is grown by the tentacle reach ({@code Kraken#getBoundingBoxForCulling}); its ten hit-box
 * parts render nothing ({@code NoopRenderer}).
 */
public class KrakenRenderer extends GeoEntityRenderer<Kraken> {

    public KrakenRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new KrakenModel());
        this.shadowRadius = 2.0f;
    }
}
