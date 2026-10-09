package com.richardsenger.piratesnships.apparel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.apparel.CoatTails;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;

/**
 * The worn coat with tails (ART9): vanilla's outer armour model (body and arms inflated by 1.0, the model vanilla's
 * {@code HumanoidArmorLayer} uses for the chest slot) plus two skirt pieces, {@code tail_right} and {@code tail_left},
 * children of the body so they follow its pose. Each is a back plate (5 x 7 x 1 px, split 1 px at the centre of the
 * back) and a side plate (1 x 7 x 6 px) hanging from the body's bottom back edge to the knee. Drawn on a 64 x 32 layer
 * texture: body and arms where vanilla has them, the tails in the head area, which a chest-only material never uses
 * (back plates at 0,0 and 0,8; side plates at 12,0 and 26,0).
 *
 * <p>The loader copies vanilla's pose and part visibility onto this model before drawing it, but nothing calls
 * {@code setupAnim} on an armour model, so the tails are posed from the copied legs and body in
 * {@link #renderToBuffer} ({@link CoatTails}). Blockbench source: {@code art/models/entity/coat_armor.bbmodel} (Modded
 * Entity, Mojang mappings); {@link #createLayer()} is its exported {@code createBodyLayer()} on vanilla's armour mesh.
 */
public class CoatArmorModel extends HumanoidModel<LivingEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Constants.id("coat_armor"), "main");
    public static final String TAIL_RIGHT = "tail_right";
    public static final String TAIL_LEFT = "tail_left";
    /** The tails' hinge in the body's frame: the bottom back edge of the body (y 12, z 3 = the plain body's back). */
    public static final float HINGE_Y = 12f;
    public static final float HINGE_Z = 3f;

    public final ModelPart tailRight;
    public final ModelPart tailLeft;

    public CoatArmorModel(ModelPart root) {
        super(root);
        this.tailRight = body.getChild(TAIL_RIGHT);
        this.tailLeft = body.getChild(TAIL_LEFT);
    }

    /** Vanilla's outer armour mesh (deformation 1.0) with the tails on the body. 64 x 32, like an armour layer. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = HumanoidArmorModel.createBodyLayer(new CubeDeformation(1.0F));
        PartDefinition body = mesh.getRoot().getChild("body");
        body.addOrReplaceChild(TAIL_RIGHT, CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-5.5F, 0.0F, 0.1F, 5.0F, 7.0F, 1.0F)
                        .texOffs(12, 0).addBox(-6.5F, 0.0F, -4.9F, 1.0F, 7.0F, 6.0F),
                PartPose.offset(0.0F, HINGE_Y, HINGE_Z));
        body.addOrReplaceChild(TAIL_LEFT, CubeListBuilder.create()
                        .texOffs(0, 8).addBox(0.5F, 0.0F, 0.1F, 5.0F, 7.0F, 1.0F)
                        .texOffs(26, 0).addBox(5.5F, 0.0F, -4.9F, 1.0F, 7.0F, 6.0F),
                PartPose.offset(0.0F, HINGE_Y, HINGE_Z));
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** Poses the tails from the copied leg and body rotations ({@link CoatTails}). */
    public void poseTails() {
        tailRight.xRot = CoatTails.pitch(rightLeg.xRot, body.xRot);
        tailLeft.xRot = CoatTails.pitch(leftLeg.xRot, body.xRot);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        poseTails();
        super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
