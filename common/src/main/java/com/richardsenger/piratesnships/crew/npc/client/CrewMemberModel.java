package com.richardsenger.piratesnships.crew.npc.client;

import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.mob.client.HumanoidGeoModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * The crew member's GeckoLib model: the humanoid rig ({@code art/README.md}, "Entities") with the crew member's
 * geometry, texture and animations. The head following the look direction is the shared {@link HumanoidGeoModel},
 * which the M3 mobs use with their own textures.
 * <p>
 * Lying in a hammock (ART1d, the {@code sleep} animation) the head does not follow the look: animations may not key
 * the head's x/y rotation (rig contract), so this model holds it here, tilted chin to chest by {@link #SLEEP_HEAD_RAISE}
 * so it rests on the canvas rising towards the hammock's end instead of hanging into it.
 */
public class CrewMemberModel extends HumanoidGeoModel<CrewMember> {

    public static final ResourceLocation GEO = RIG_GEO;
    public static final ResourceLocation TEXTURE = entityTexture("crew_member");
    public static final ResourceLocation ANIMATIONS = RIG_ANIMATIONS;

    /** Degrees the sleeper's head is tilted towards the chest (raised off the canvas). */
    public static final float SLEEP_HEAD_RAISE = 15f;

    public CrewMemberModel() {
        super(GEO, TEXTURE, ANIMATIONS);
    }

    @Override
    public void setCustomAnimations(CrewMember animatable, long instanceId, AnimationState<CrewMember> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        if (animatable.isResting() && !animatable.isWorking()) {
            GeoBone head = getAnimationProcessor().getBone(HEAD);
            if (head != null) {
                // GeckoLib bone space: negative x = chin to chest (a look down, like EntityModelData's flipped pitch)
                head.setRotX(-SLEEP_HEAD_RAISE * Mth.DEG_TO_RAD);
                head.setRotY(0);
            }
        }
    }
}
