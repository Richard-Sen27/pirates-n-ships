package com.richardsenger.piratesnships.crew.npc.client;

import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.station.StationContent;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Client registration of the station module (physical client only): the crew member with the vanilla player model and
 * Steve's skin as a placeholder (no texture of ours), and no rendering for the invisible seat.
 */
public final class CrewClient {

    private static final ResourceLocation SKIN = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    private CrewClient() {
    }

    public static void init() {
        ClientEvents.registerEntityRenderer(StationContent.STATION_SEAT, NoopRenderer::new);
        ClientEvents.registerEntityRenderer(StationContent.CREW_MEMBER,
                ctx -> new HumanoidMobRenderer<CrewMember, CrewModel>(ctx, new CrewModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f) {
                    @Override
                    public ResourceLocation getTextureLocation(CrewMember entity) {
                        return SKIN;
                    }
                });
    }

    /** Humanoid model that stands at a station: riding the invisible seat must not bend it into the sitting pose. */
    static final class CrewModel extends HumanoidModel<CrewMember> {
        CrewModel(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(CrewMember entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            this.riding = false;
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        }
    }
}
