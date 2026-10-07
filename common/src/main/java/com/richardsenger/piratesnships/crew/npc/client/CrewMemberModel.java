package com.richardsenger.piratesnships.crew.npc.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * The crew member's GeckoLib model: geometry, texture and animations of the humanoid rig ({@code art/README.md},
 * "Entities"). The head bone follows the look direction after the animations ran, so animations never key the head's
 * x and y rotation.
 */
public class CrewMemberModel extends GeoModel<CrewMember> {

    public static final ResourceLocation GEO = id("geo/crew_member.geo.json");
    public static final ResourceLocation TEXTURE = id("textures/entity/crew_member.png");
    public static final ResourceLocation ANIMATIONS = id("animations/crew_member.animation.json");

    /** Bone names of the rig contract that code uses. */
    public static final String HEAD = "head";
    public static final String RIGHT_HAND = "right_hand";
    public static final String LEFT_HAND = "left_hand";

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path);
    }

    @Override
    public ResourceLocation getModelResource(CrewMember animatable) {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(CrewMember animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(CrewMember animatable) {
        return ANIMATIONS;
    }

    @Override
    public void setCustomAnimations(CrewMember animatable, long instanceId, AnimationState<CrewMember> animationState) {
        GeoBone head = getAnimationProcessor().getBone(HEAD);
        EntityModelData data = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head != null && data != null) {
            head.setRotX(data.headPitch() * Mth.DEG_TO_RAD);
            head.setRotY(data.netHeadYaw() * Mth.DEG_TO_RAD);
        }
    }
}
