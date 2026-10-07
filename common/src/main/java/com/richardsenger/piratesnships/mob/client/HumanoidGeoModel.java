package com.richardsenger.piratesnships.mob.client;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * GeckoLib model of any mob on the humanoid rig ({@code art/README.md}, "Entities"): geometry, texture and animations
 * by id, so a variant (pirate, sailor, navy) is the same class with its own geometry and texture. Every variant's
 * geometry keeps the rig's bones, so the shared animations drive it. The head bone follows the look direction after
 * the animations ran, so animations never key the head's x and y rotation.
 */
public class HumanoidGeoModel<T extends LivingEntity & GeoAnimatable> extends GeoModel<T> {

    /** The shared rig geometry and animations (the crew member's files). */
    public static final ResourceLocation RIG_GEO = Constants.id("geo/crew_member.geo.json");
    public static final ResourceLocation RIG_ANIMATIONS = Constants.id("animations/crew_member.animation.json");

    /** Bone names of the rig contract that code uses. */
    public static final String HEAD = "head";
    public static final String WAIST = "waist";
    public static final String RIGHT_ARM = "right_arm";
    public static final String LEFT_ARM = "left_arm";
    public static final String RIGHT_HAND = "right_hand";
    public static final String LEFT_HAND = "left_hand";

    private final ResourceLocation geo;
    private final ResourceLocation texture;
    private final ResourceLocation animations;

    public HumanoidGeoModel(ResourceLocation geo, ResourceLocation texture, ResourceLocation animations) {
        this.geo = geo;
        this.texture = texture;
        this.animations = animations;
    }

    /**
     * A variant with the shared animations: its texture {@code textures/entity/<name>.png} and its own geometry
     * {@code geo/<name>.geo.json} on the rig (M3-art: the pirate, sailor, navy soldier and officer models).
     */
    public HumanoidGeoModel(ResourceLocation texture) {
        this(geoFor(texture), texture, RIG_ANIMATIONS);
    }

    /** {@code textures/entity/<name>.png} of this mod. */
    public static ResourceLocation entityTexture(String name) {
        return Constants.id("textures/entity/" + name + ".png");
    }

    /** The geometry that goes with an entity texture: {@code textures/entity/<name>.png} → {@code geo/<name>.geo.json}. */
    public static ResourceLocation geoFor(ResourceLocation texture) {
        String path = texture.getPath();
        String prefix = "textures/entity/", suffix = ".png";
        if (!path.startsWith(prefix) || !path.endsWith(suffix)) {
            throw new IllegalArgumentException("not an entity texture: " + texture);
        }
        return ResourceLocation.fromNamespaceAndPath(texture.getNamespace(),
                "geo/" + path.substring(prefix.length(), path.length() - suffix.length()) + ".geo.json");
    }

    @Override
    public ResourceLocation getModelResource(T animatable) {
        return geo;
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return texture;
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return animations;
    }

    @Override
    public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
        GeoBone head = getAnimationProcessor().getBone(HEAD);
        EntityModelData data = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head != null && data != null) {
            head.setRotX(data.headPitch() * Mth.DEG_TO_RAD);
            head.setRotY(data.netHeadYaw() * Mth.DEG_TO_RAD);
        }
    }
}
