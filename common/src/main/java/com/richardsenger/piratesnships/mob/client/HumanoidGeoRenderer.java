package com.richardsenger.piratesnships.mob.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.util.RenderUtil;

/**
 * GeckoLib renderer of any mob on the humanoid rig: the model plus the held items on the {@code right_hand} and
 * {@code left_hand} locator bones (rig contract, {@code art/README.md} "Entities"). Shared by the crew member and the
 * M3 mobs; a variant only passes another {@link GeoModel}.
 */
public class HumanoidGeoRenderer<T extends LivingEntity & GeoAnimatable> extends GeoEntityRenderer<T> {

    public HumanoidGeoRenderer(EntityRendererProvider.Context ctx, GeoModel<T> model) {
        super(ctx, model);
        this.shadowRadius = 0.5f;
        addRenderLayer(new HeldItemLayer<>(this));
    }

    /** The stack held by the hand of {@code bone} (right or left, after the entity's main arm), or null for other bones. */
    public static @Nullable ItemStack heldBy(String bone, LivingEntity entity) {
        boolean rightMain = entity.getMainArm() == HumanoidArm.RIGHT;
        return switch (bone) {
            case HumanoidGeoModel.RIGHT_HAND -> rightMain ? entity.getMainHandItem() : entity.getOffhandItem();
            case HumanoidGeoModel.LEFT_HAND -> rightMain ? entity.getOffhandItem() : entity.getMainHandItem();
            default -> null;
        };
    }

    /**
     * Held items on the hand locators, posed like vanilla's {@code ItemInHandLayer}: the locator sits where vanilla's
     * hand transform ends (rig contract), so only vanilla's rotation is left to apply (see {@link ItemFrame}).
     */
    static final class HeldItemLayer<T extends LivingEntity & GeoAnimatable> extends BlockAndItemGeoLayer<T> {

        HeldItemLayer(HumanoidGeoRenderer<T> renderer) {
            super(renderer, (bone, entity) -> heldBy(bone.getName(), entity), (bone, entity) -> null);
        }

        @Override
        protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, T animatable) {
            return HumanoidGeoModel.LEFT_HAND.equals(bone.getName())
                    ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        }

        /** Replaces {@link BlockAndItemGeoLayer}'s version, which turns the item by the bone's rotation a second time. */
        @Override
        public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            ItemStack stack = getStackForBone(bone, animatable);
            if (stack == null || stack.isEmpty()) return;
            poseStack.pushPose();
            ItemFrame.apply(poseStack, bone);
            renderStackForBone(poseStack, bone, stack, animatable, bufferSource, partialTick, packedLight, packedOverlay);
            poseStack.popPose();
        }

        @Override
        protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, T animatable,
                                          MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
            boolean left = HumanoidGeoModel.LEFT_HAND.equals(bone.getName());
            Minecraft.getInstance().getItemRenderer().renderStatic(animatable, stack, getTransformTypeForStack(bone, stack, animatable),
                    left, poseStack, bufferSource, animatable.level(), packedLight, packedOverlay, animatable.getId());
        }
    }

    /**
     * From the pose GeckoLib hands a render layer to the frame the held item is drawn in (before its display transform).
     * {@code GeoEntityRenderer#renderRecursively} calls the layers with the hand bone's position and rotation already
     * applied and its pivot undone, so only the move to the pivot is left. GeckoLib's own
     * {@code BlockAndItemGeoLayer#renderForBone} rotates by the bone again ({@code RenderUtil.translateAndRotateMatrixForBone}),
     * which turned the musket twice as far as the animation keys (M6b: the reload's barrel lay level and pointed backwards
     * instead of standing up). Then vanilla's hand rotation: {@code ItemInHandLayer} turns x by -90 and y by 180 in the
     * y-down model frame; in GeckoLib's y-up frame that is a single x rotation of -90. A key on the hand bone then turns
     * the item the way PAL's {@code right_item} bone turns the player's (art/README.md, "Entities", M6b).
     * A separate class so tests can use it without loading the renderer.
     */
    public static final class ItemFrame {

        private ItemFrame() {}

        public static void apply(PoseStack poseStack, GeoBone bone) {
            RenderUtil.translateToPivotPoint(poseStack, bone);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
        }
    }
}
