package com.richardsenger.piratesnships.crew.npc.client;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.station.StationContent;
import net.minecraft.client.renderer.entity.NoopRenderer;

/**
 * Client registration of the crew (physical client only): the crew member with its GeckoLib rig
 * ({@link CrewMemberRenderer}) and no rendering for the invisible station seat.
 */
public final class CrewClient {

    private CrewClient() {
    }

    public static void init() {
        ClientEvents.registerEntityRenderer(StationContent.STATION_SEAT, NoopRenderer::new);
        ClientEvents.registerEntityRenderer(StationContent.CREW_MEMBER, CrewMemberRenderer::new);
    }
}
