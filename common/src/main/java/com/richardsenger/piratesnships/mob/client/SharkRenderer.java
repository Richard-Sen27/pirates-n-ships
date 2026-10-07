package com.richardsenger.piratesnships.mob.client;

import com.richardsenger.piratesnships.mob.entity.Shark;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** GeckoLib renderer of the shark: the model as it is, with a shadow the size of its body. */
public class SharkRenderer extends GeoEntityRenderer<Shark> {

    public SharkRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new SharkModel());
        this.shadowRadius = 0.6f;
    }
}
