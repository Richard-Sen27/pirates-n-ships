package com.richardsenger.piratesnships.crew.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * GeckoLib renderer of the crew member: the humanoid rig plus the held items on the {@code right_hand} and
 * {@code left_hand} locator bones. Riding the station seat adds no offset here: the seat (through Sable) places the
 * entity itself, GeckoLib only zeroes the leg swing of a passenger.
 */
public class CrewMemberRenderer extends GeoEntityRenderer<CrewMember> {

    public CrewMemberRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CrewMemberModel());
        this.shadowRadius = 0.5f;
        addRenderLayer(new HeldItemLayer(this));
    }

    /** The stack held by the hand of {@code bone} (right or left, after the entity's main arm), or null for other bones. */
    static @Nullable ItemStack heldBy(String bone, CrewMember entity) {
        boolean rightMain = entity.getMainArm() == HumanoidArm.RIGHT;
        return switch (bone) {
            case CrewMemberModel.RIGHT_HAND -> rightMain ? entity.getMainHandItem() : entity.getOffhandItem();
            case CrewMemberModel.LEFT_HAND -> rightMain ? entity.getOffhandItem() : entity.getMainHandItem();
            default -> null;
        };
    }

    /**
     * Held items on the hand locators, posed like vanilla's {@code ItemInHandLayer}: the locator sits where vanilla's
     * hand transform ends (rig contract), so only vanilla's rotation is left to apply. Vanilla rotates x by -90 and y by
     * 180 in the y-down model frame; in GeckoLib's y-up frame that is a single x rotation of -90.
     */
    static final class HeldItemLayer extends BlockAndItemGeoLayer<CrewMember> {

        HeldItemLayer(CrewMemberRenderer renderer) {
            super(renderer, (bone, entity) -> heldBy(bone.getName(), entity), (bone, entity) -> null);
        }

        @Override
        protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, CrewMember animatable) {
            return CrewMemberModel.LEFT_HAND.equals(bone.getName())
                    ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        }

        @Override
        protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, CrewMember animatable,
                                          MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
            boolean left = CrewMemberModel.LEFT_HAND.equals(bone.getName());
            poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
            Minecraft.getInstance().getItemRenderer().renderStatic(animatable, stack, getTransformTypeForStack(bone, stack, animatable),
                    left, poseStack, bufferSource, animatable.level(), packedLight, packedOverlay, animatable.getId());
        }
    }
}
