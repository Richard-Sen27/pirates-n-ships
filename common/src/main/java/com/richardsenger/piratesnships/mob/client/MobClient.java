package com.richardsenger.piratesnships.mob.client;

import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.MobKind;
import com.richardsenger.piratesnships.platform.event.ClientEvents;

/** Client registration of the mobs (physical client only): the humanoids share one renderer class, a texture each; the shark has its own. */
public final class MobClient {

    private MobClient() {
    }

    public static void init() {
        ClientEvents.registerEntityRenderer(MobContent.PIRATE, ctx -> new HumanoidGeoRenderer<>(ctx, new SeafarerModel<>(MobKind.PIRATE.id())));
        ClientEvents.registerEntityRenderer(MobContent.SAILOR, ctx -> new HumanoidGeoRenderer<>(ctx, new SeafarerModel<>(MobKind.SAILOR.id())));
        ClientEvents.registerEntityRenderer(MobContent.NAVY_SOLDIER, ctx -> new HumanoidGeoRenderer<>(ctx, new SeafarerModel<>(MobKind.NAVY_SOLDIER.id())));
        ClientEvents.registerEntityRenderer(MobContent.NAVY_OFFICER, ctx -> new HumanoidGeoRenderer<>(ctx, new SeafarerModel<>(MobKind.NAVY_OFFICER.id())));
        ClientEvents.registerEntityRenderer(MobContent.SHARK, SharkRenderer::new);
    }
}
