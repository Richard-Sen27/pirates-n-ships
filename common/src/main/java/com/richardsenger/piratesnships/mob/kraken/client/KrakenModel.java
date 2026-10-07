package com.richardsenger.piratesnships.mob.kraken.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.mob.kraken.Kraken;
import com.richardsenger.piratesnships.mob.kraken.KrakenTentacles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model of the kraken (rig contract: art/README.md, "Kraken (K1b)", and {@code KrakenRigTest}). After the
 * animations ran, each tentacle's first segment {@code tentacle_<i>_1} is aimed at its hit box ({@code KrakenPart} i,
 * synced by the server) and stretched along its length so the tentacle's tip ({@link #TENTACLE_LENGTH} from the root)
 * ends at the part's centre; the animations bend the second and third segments. The aim controls the roll as well
 * ({@link KrakenAim#suckerNormal}): a raised or level arm turns its sucker face down, so the curled tip and the curls of
 * the animations close down over the target; a hanging arm turns it towards the body's axis. A tentacle whose part is
 * not known yet leans {@link #REST_LEAN_DEG} outwards (straight up it would run through the head); a cut tentacle
 * shrinks to a stump in that pose. The part's world offset goes into the bone frame by undoing the renderer's turn by
 * {@code 180 - bodyYaw} only ({@link KrakenAim#toBoneFrame}); the baked pivots already carry GeckoLib's x flip.
 */
public class KrakenModel extends GeoModel<Kraken> {

    public static final ResourceLocation GEO = Constants.id("geo/kraken.geo.json");
    public static final ResourceLocation TEXTURE = Constants.id("textures/entity/kraken.png");
    public static final ResourceLocation ANIMATIONS = Constants.id("animations/kraken.animation.json");

    /** Rest length of a tentacle in px (segments 20 + 20 + 20; {@code Kraken#TENTACLE_LENGTH} in blocks). */
    public static final float TENTACLE_LENGTH = (float) (Kraken.TENTACLE_LENGTH * 16.0);
    /** Outward lean of a tentacle without a known part, degrees from straight up. */
    public static final float REST_LEAN_DEG = 25f;
    private static final float MIN_STRETCH = 0.4f;
    private static final float MAX_STRETCH = 8f;
    private static final float STUMP = 0.25f;

    @Override
    public ResourceLocation getModelResource(Kraken animatable) {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(Kraken animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(Kraken animatable) {
        return ANIMATIONS;
    }

    public static String tentacleBone(int i, int segment) {
        return "tentacle_" + i + "_" + segment;
    }

    @Override
    public void setCustomAnimations(Kraken kraken, long instanceId, AnimationState<Kraken> state) {
        float partialTick = state.getPartialTick();
        Vec3 origin = kraken.getPosition(partialTick);
        float bodyYaw = Mth.rotLerp(partialTick, kraken.yBodyRotO, kraken.yBodyRot);
        for (int i = 0; i < KrakenTentacles.COUNT; i++) {
            GeoBone bone = getAnimationProcessor().getBone(tentacleBone(i, 1));
            if (bone == null) continue;
            double px = bone.getPivotX(), pz = bone.getPivotZ();
            if (kraken.clientTentacleCut(i)) {
                setAim(bone, KrakenAim.aim(px, pz, KrakenAim.restDirection(px, pz, REST_LEAN_DEG * Mth.DEG_TO_RAD)));
                bone.setScaleX(STUMP);
                bone.setScaleY(STUMP);
                bone.setScaleZ(STUMP);
                continue;
            }
            Vec3 tip = kraken.clientPartCentre(i, partialTick);
            // the baked pivot is in the frame GeckoLib draws in (x already flipped against the file)
            KrakenAim.Reach reach = tip == null ? null : KrakenAim.reach(
                    new double[]{px, bone.getPivotY(), pz},
                    new double[]{tip.x - origin.x, tip.y - origin.y, tip.z - origin.z},
                    bodyYaw, TENTACLE_LENGTH, MIN_STRETCH, MAX_STRETCH);
            bone.setScaleX(1f);
            bone.setScaleZ(1f);
            if (reach == null) {
                setAim(bone, KrakenAim.aim(px, pz, KrakenAim.restDirection(px, pz, REST_LEAN_DEG * Mth.DEG_TO_RAD)));
                bone.setScaleY(1f);
                continue;
            }
            setAim(bone, reach.rot());
            bone.setScaleY(reach.stretch());
        }
    }

    private static void setAim(GeoBone bone, float[] rot) {
        bone.setRotX(rot[0]);
        bone.setRotY(rot[1]);
        bone.setRotZ(rot[2]);
    }
}
