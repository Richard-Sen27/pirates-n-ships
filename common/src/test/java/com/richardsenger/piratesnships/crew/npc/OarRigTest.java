package com.richardsenger.piratesnships.crew.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.richardsenger.piratesnships.mob.client.HumanoidGeoRenderer;
import com.richardsenger.piratesnships.sailing.oars.Rowing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.keyframe.BoneAnimation;
import software.bernie.geckolib.animation.keyframe.Keyframe;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.math.MathValue;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.util.RenderUtil;

/**
 * OAR1 rig test of the rowing animations, composed end to end as the game draws the rower and its oar (GL1 rule 5):
 * GeckoLib's loaded keys (x and y negated, radians) on the baked crew rig (pivots x-mirrored),
 * {@link RenderUtil#prepMatrixForBone} down {@code root -> waist -> arm -> hand}, {@code HumanoidGeoRenderer}'s item
 * frame ({@link HumanoidGeoRenderer.ItemFrame}: to the hand's pivot, x by −90), vanilla's {@link ItemTransform} of the
 * oar's third-person entry from {@code models/item/oar.json} (mirrored by vanilla for the left hand) and the
 * {@code −0.5} model offset, all under {@code GeoEntityRenderer}'s turn by {@code 180 − bodyYaw}.
 *
 * <p>The rower faces aft (as {@code Rowing.aft} turns it); its oar must point outboard, on whichever side
 * {@link Rowing#leftHanded} puts it, dip below the deck in the drive, rise out of the water in the recovery, and the
 * blade must move toward the stern (the way the rower faces) while it is in the water, the handle the other way: a
 * mirrored stroke would row the ship backwards.
 */
class OarRigTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/pirates_n_ships");
    private static final Path RIG = ASSETS.resolve("geo/crew_member.geo.json");
    private static final Path ANIMATIONS = ASSETS.resolve("animations/crew_member.animation.json");
    private static final Path OAR = ASSETS.resolve("models/item/oar.json");
    /** The blade's tip and the end of the grip in the oar's model (px). */
    private static final Vector3f TIP = new Vector3f(8, 30, 8);
    private static final Vector3f GRIP_END = new Vector3f(8, -15, 8);
    /** The seat's height (sized 0.25 × 0.01): the rider's feet are this far above the deck. */
    private static final float SEAT_HEIGHT = 0.01f;

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static float keyedAt(List<Keyframe<MathValue>> frames, double tick, String what) {
        if (frames.size() == 1) return (float) frames.get(0).endValue().get();
        double t = 0;
        for (Keyframe<MathValue> k : frames) {
            t += k.length();
            if (Math.abs(t - tick) < 1e-6) return (float) k.endValue().get();
        }
        throw new AssertionError(what + " has no key at tick " + tick);
    }

    private static final List<String> BONES = List.of("root", "waist", "right_arm", "left_arm", "right_hand", "left_hand");

    /** The crew rig posed at a key time of {@code animation} (root, waist, arms and hands). */
    private static BakedGeoModel posed(String animation, double seconds) throws IOException {
        Model model = KeyFramesAdapter.GEO_GSON.fromJson(read(RIG), Model.class);
        BakedGeoModel baked = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(model));
        BakedAnimations anims = KeyFramesAdapter.GEO_GSON.fromJson(read(ANIMATIONS).getAsJsonObject("animations"), BakedAnimations.class);
        Animation anim = anims.getAnimation(animation);
        assertNotNull(anim, animation);
        double tick = seconds * 20;
        for (BoneAnimation b : anim.boneAnimations()) {
            if (!BONES.contains(b.boneName())) continue;
            GeoBone bone = baked.getBone(b.boneName()).orElseThrow();
            String what = animation + " " + b.boneName();
            if (!b.rotationKeyFrames().xKeyframes().isEmpty()) {
                bone.setRotX(keyedAt(b.rotationKeyFrames().xKeyframes(), tick, what));
                bone.setRotY(keyedAt(b.rotationKeyFrames().yKeyframes(), tick, what));
                bone.setRotZ(keyedAt(b.rotationKeyFrames().zKeyframes(), tick, what));
            }
            if (!b.positionKeyFrames().xKeyframes().isEmpty()) {
                bone.setPosX(keyedAt(b.positionKeyFrames().xKeyframes(), tick, what));
                bone.setPosY(keyedAt(b.positionKeyFrames().yKeyframes(), tick, what));
                bone.setPosZ(keyedAt(b.positionKeyFrames().zKeyframes(), tick, what));
            }
        }
        return baked;
    }

    private static ItemTransform thirdPerson() throws IOException {
        JsonObject e = read(OAR).getAsJsonObject("display").getAsJsonObject("thirdperson_righthand");
        return new ItemTransform(vec(e.getAsJsonArray("rotation")), vec(e.getAsJsonArray("translation")).mul(0.0625f),
                vec(e.getAsJsonArray("scale")));
    }

    private static Vector3f vec(JsonArray a) {
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    /** Where the oar's hand, grip end and blade tip are in the world (blocks). */
    private record Oar(Vector3f hand, Vector3f grip, Vector3f tip) {
    }

    private static Oar oar(String animation, double seconds, Vector3f pos, float bodyYaw, boolean left) throws IOException {
        BakedGeoModel baked = posed(animation, seconds);
        String side = left ? "left" : "right";
        PoseStack pose = new PoseStack();
        pose.translate(pos.x, pos.y, pos.z);
        pose.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
        for (String name : List.of("root", "waist", side + "_arm", side + "_hand")) {
            RenderUtil.prepMatrixForBone(pose, baked.getBone(name).orElseThrow());
        }
        GeoBone hand = baked.getBone(side + "_hand").orElseThrow();
        Vector3f handAt = pose.last().pose().transformPosition(new Vector3f(hand.getPivotX(), hand.getPivotY(), hand.getPivotZ()).div(16f));
        HumanoidGeoRenderer.ItemFrame.apply(pose, hand);
        thirdPerson().apply(left, pose);
        pose.translate(-0.5f, -0.5f, -0.5f);
        Vector3f tip = pose.last().pose().transformPosition(new Vector3f(TIP).div(16f));
        Vector3f grip = pose.last().pose().transformPosition(new Vector3f(GRIP_END).div(16f));
        return new Oar(handAt, grip, tip);
    }

    private static Vector3f step(Direction d) {
        return new Vector3f(d.getStepX(), 0, d.getStepZ());
    }

    private static float along(Vector3f v, Vector3f from, Direction d) {
        return new Vector3f(v).sub(from).dot(step(d));
    }

    /** Drive keys (blade in the water) and recovery keys (blade carried forward). */
    private static final double[] DRIVE = {0.0, 0.45, 0.9};
    private static final double[] RECOVERY = {1.2, 1.6};

    private static void checkStroke(String animation, float drop) throws IOException {
        for (Direction aft : Direction.Plane.HORIZONTAL) {
            for (Direction outboard : new Direction[]{aft.getClockWise(), aft.getCounterClockWise()}) {
                boolean left = Rowing.leftHanded(aft, outboard);
                float yaw = StationPoses.yaw(aft, null);
                Vector3f pos = new Vector3f(0.5f, SEAT_HEIGHT, 0.5f);
                String where = animation + " facing " + aft + ", oar " + outboard + (left ? " (left hand)" : " (right hand)");
                Oar[] drive = new Oar[DRIVE.length];
                for (int i = 0; i < DRIVE.length; i++) {
                    Oar o = oar(animation, DRIVE[i], pos, yaw, left);
                    drive[i] = o;
                    assertTrue(along(o.tip(), o.hand(), outboard) > 1.8, where + " " + DRIVE[i] + " s: the blade lies outboard " + o);
                    assertTrue(o.tip().y < pos.y - 0.6 + drop * 0, where + " " + DRIVE[i] + " s: the blade dips below the deck " + o);
                    assertTrue(along(o.grip(), o.hand(), outboard) < 0, where + ": the grip end is inboard of the hand " + o);
                    assertTrue(o.hand().y > pos.y + 0.6 - drop, where + ": the hands at chest height " + o);
                }
                // the drive: the blade moves toward the stern the rower faces, the handle toward the bow
                assertTrue(along(drive[1].tip(), drive[0].tip(), aft) > 0.5 && along(drive[2].tip(), drive[1].tip(), aft) > 0.5,
                        where + ": the blade sweeps aft in the water " + drive[0].tip() + " -> " + drive[1].tip() + " -> " + drive[2].tip());
                assertTrue(along(drive[2].hand(), drive[0].hand(), aft) < -0.2, where + ": the handle is pulled toward the bow");
                // the recovery: the blade out of the water, carried back toward the bow
                Oar release = oar(animation, RECOVERY[0], pos, yaw, left);
                Oar carry = oar(animation, RECOVERY[1], pos, yaw, left);
                for (Oar o : new Oar[]{release, carry}) {
                    assertTrue(o.tip().y > drive[1].tip().y + 0.8, where + ": the blade is lifted in the recovery " + o);
                    assertTrue(along(o.tip(), o.hand(), outboard) > 1.8, where + ": still outboard in the recovery " + o);
                }
                assertTrue(along(carry.tip(), release.tip(), aft) < -0.5, where + ": carried back toward the bow");
            }
        }
    }

    @Test
    void theStandingStrokeRowsOutboardAndAft() throws IOException {
        checkStroke("row_standing", 0f);
    }

    @Test
    void theSeatedStrokeRowsOutboardAndAftFromTheThwart() throws IOException {
        checkStroke("row_seated", 0.25f);
    }

    @Test
    void theSeatedRowerSitsOnTheThwart() throws IOException {
        BakedGeoModel seated = posed("row_seated", 0.0);
        assertEquals(-4f, seated.getBone("root").orElseThrow().getPosY(), 1e-4, "hips 4 px down onto the 8 px thwart");
    }

    @Test
    void leftHandedRowersAreTheOnesWhoseOarIsOnTheLeft() {
        // facing south (+z) the right hand is west
        assertTrue(!Rowing.leftHanded(Direction.SOUTH, Direction.WEST));
        assertTrue(Rowing.leftHanded(Direction.SOUTH, Direction.EAST));
        assertTrue(!Rowing.leftHanded(Direction.NORTH, Direction.EAST));
        assertTrue(!Rowing.leftHanded(Direction.EAST, Direction.SOUTH));
        assertTrue(!Rowing.leftHanded(Direction.WEST, Direction.NORTH));
    }
}
