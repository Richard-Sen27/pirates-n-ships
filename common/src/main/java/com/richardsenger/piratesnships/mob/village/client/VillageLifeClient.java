package com.richardsenger.piratesnships.mob.village.client;

import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.client.HumanoidGeoRenderer;
import com.richardsenger.piratesnships.mob.village.VillageLifeContent;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.renderer.entity.NoopRenderer;

/** Client registration of the village life module (physical client only): the villager on the rig, the seat unseen. */
public final class VillageLifeClient {

    private VillageLifeClient() {
    }

    public static void init() {
        ClientEvents.registerEntityRenderer(MobContent.VILLAGER, ctx -> new HumanoidGeoRenderer<>(ctx, new VillagerModel()));
        ClientEvents.registerEntityRenderer(VillageLifeContent.BENCH_SEAT, NoopRenderer::new);
    }
}
