package com.richardsenger.piratesnships.crew.npc.client;

import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.mob.client.HumanoidGeoModel;
import net.minecraft.resources.ResourceLocation;

/**
 * The crew member's GeckoLib model: the humanoid rig ({@code art/README.md}, "Entities") with the crew member's
 * geometry, texture and animations. All behaviour (the head following the look direction) is the shared
 * {@link HumanoidGeoModel}, which the M3 mobs use with their own textures.
 */
public class CrewMemberModel extends HumanoidGeoModel<CrewMember> {

    public static final ResourceLocation GEO = RIG_GEO;
    public static final ResourceLocation TEXTURE = entityTexture("crew_member");
    public static final ResourceLocation ANIMATIONS = RIG_ANIMATIONS;

    public CrewMemberModel() {
        super(GEO, TEXTURE, ANIMATIONS);
    }
}
