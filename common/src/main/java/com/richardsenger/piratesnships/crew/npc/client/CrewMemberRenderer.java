package com.richardsenger.piratesnships.crew.npc.client;

import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.mob.client.HumanoidGeoRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * GeckoLib renderer of the crew member: the shared humanoid renderer (rig plus held items on the hand locators) with
 * the {@link CrewMemberModel}. Riding the station seat adds no offset here: the seat (through Sable) places the entity
 * itself, GeckoLib only zeroes the leg swing of a passenger.
 */
public class CrewMemberRenderer extends HumanoidGeoRenderer<CrewMember> {

    public CrewMemberRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CrewMemberModel());
    }
}
