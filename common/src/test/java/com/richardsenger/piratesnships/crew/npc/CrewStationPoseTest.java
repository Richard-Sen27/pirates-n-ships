package com.richardsenger.piratesnships.crew.npc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ART7 rig tests of the station animations, composed end to end as the game draws them (GL1 rule 5): GeckoLib's loaded
 * key values (rotation x and y negated, radians) on the baked crew rig (pivots x-mirrored), {@link RenderUtil#prepMatrixForBone}
 * down {@code root -> waist -> arm}, the hand locator's baked pivot, and {@code GeoEntityRenderer}'s turn by
 * {@code 180 - bodyYaw} at the entity's position. The world is the helm's: the helm block at the origin facing each of
 * the four directions, the helmsman standing at the centre of the block on its {@code FACING} side (on the station
 * seat, 0.01 blocks up) with the yaw {@link StationPoses#yaw} gives him towards the helm, and the wheel drawn as
 * {@code sailing.helm.client.HelmWheelRenderer} draws it: the north-facing model turned by {@code 180 - facing} about
 * the block centre, the axle at (8, 13, 3.75) px of the model along its z, the rim 5.9 px from it.
 */
class CrewStationPoseTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/pirates_n_ships");
    private static final Path RIG = ASSETS.resolve("geo/crew_member.geo.json");
    private static final Path ANIMATIONS = ASSETS.resolve("animations/crew_member.animation.json");

    /** {@code HelmWheelRenderer.AXLE_*} (px of the north-facing model) and the rim's mean radius in helm_wheel.json. */
    private static final Vector3f AXLE = new Vector3f(8f, 13f, 3.75f);
    private static final float RIM_RADIUS = 5.9f;
    /** The station seat's height (StationContent: sized 0.25 × 0.01): the rider's feet are this far above the deck. */
    private static final float SEAT_HEIGHT = 0.01f;

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    /** The value a keyframe channel reaches at {@code tick}, which must be a key time (lengths are in ticks). */
    private static float keyedAt(List<Keyframe<MathValue>> frames, double tick, String what) {
        double t = 0;
        for (Keyframe<MathValue> k : frames) {
            t += k.length();
            if (Math.abs(t - tick) < 1e-6) return (float) k.endValue().get();
        }
        throw new AssertionError(what + " has no key at tick " + tick);
    }

    /** The crew rig posed at a key time of {@code animation}. */
    private static BakedGeoModel posed(String animation, double seconds) throws IOException {
        Model model = KeyFramesAdapter.GEO_GSON.fromJson(read(RIG), Model.class);
        BakedGeoModel baked = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(model));
        BakedAnimations anims = KeyFramesAdapter.GEO_GSON.fromJson(read(ANIMATIONS).getAsJsonObject("animations"), BakedAnimations.class);
        Animation anim = anims.getAnimation(animation);
        assertNotNull(anim, animation);
        double tick = seconds * 20;
        for (BoneAnimation b : anim.boneAnimations()) {
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

    /** World position (blocks) of the point {@code locator}'s pivot after {@code chain}, for an entity at {@code pos} with {@code bodyYaw}. */
    private static Vector3f world(BakedGeoModel baked, Vector3f pos, float bodyYaw, List<String> chain, String locator) {
        PoseStack pose = new PoseStack();
        pose.translate(pos.x, pos.y, pos.z);
        pose.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
        for (String name : chain) RenderUtil.prepMatrixForBone(pose, baked.getBone(name).orElseThrow());
        GeoBone at = baked.getBone(locator).orElseThrow();
        return pose.last().pose().transformPosition(new Vector3f(at.getPivotX(), at.getPivotY(), at.getPivotZ()).div(16f));
    }

    /** The helm wheel's model-to-world matrix as HelmWheelRenderer builds it (wheel angle 0), for a helm at the origin. */
    private static Matrix4f wheelModel(Direction facing) {
        PoseStack pose = new PoseStack();
        pose.translate(0.5f, 0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        pose.translate(-0.5f, 0f, -0.5f);
        return new Matrix4f(pose.last().pose());
    }

    /** Distance (px) of a world point from the wheel's rim circle. */
    private static double fromRim(Direction facing, Vector3f world) {
        Vector3f p = wheelModel(facing).invert().transformPosition(new Vector3f(world)).mul(16f).sub(AXLE);
        double radial = Math.hypot(p.x, p.y) - RIM_RADIUS;
        return Math.hypot(radial, p.z);
    }

    /** Clock angle (degrees clockwise from the top, as the helmsman sees the wheel) of a world point. */
    private static double clock(Direction facing, Vector3f world) {
        Vector3f p = wheelModel(facing).invert().transformPosition(new Vector3f(world)).mul(16f).sub(AXLE);
        // the helmsman looks along +z of the north-facing model (from z < 0); his right is the model's -x
        return Math.toDegrees(Math.atan2(-p.x, p.y));
    }

    private record Hands(Vector3f right, Vector3f left) {
    }

    private static Hands helmsmanHands(String animation, double seconds, Direction facing) throws IOException {
        BlockPos helm = BlockPos.ZERO;
        BlockPos spot = helm.relative(facing);
        Direction toward = StationPoses.toward(spot, helm);
        assertEquals(facing.getOpposite(), toward, "the helmsman's spot is on the FACING side");
        float yaw = StationPoses.yaw(toward, null);
        Vector3f pos = new Vector3f(spot.getX() + 0.5f, spot.getY() + SEAT_HEIGHT, spot.getZ() + 0.5f);
        BakedGeoModel baked = posed(animation, seconds);
        return new Hands(world(baked, pos, yaw, List.of("root", "waist", "right_arm"), "right_hand"),
                world(baked, pos, yaw, List.of("root", "waist", "left_arm"), "left_hand"));
    }

    @Test
    void helmHoldPutsBothHandsOnTheRimAtTwoAndTenOClock() throws IOException {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (double t : new double[]{0.0, 2.0}) {
                Hands h = helmsmanHands("helm_hold", t, facing);
                double r = fromRim(facing, h.right()), l = fromRim(facing, h.left());
                assertTrue(r < 0.3 * 16 && l < 0.3 * 16, facing + " " + t + " s: hands " + r + " / " + l + " px from the rim");
                assertTrue(r < 1 && l < 1, facing + " " + t + " s: hands " + r + " / " + l + " px from the rim (fitted to 0.2)");
                assertEquals(60, clock(facing, h.right()), 8, facing + ": the right hand at two o'clock");
                assertEquals(-60, clock(facing, h.left()), 8, facing + ": the left hand at ten o'clock");
            }
        }
    }

    @Test
    void helmTurnRightTurnsBothHandsClockwiseOnTheRim() throws IOException {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Hands start = helmsmanHands("helm_turn_right", 0.0, facing);
            Hands end = helmsmanHands("helm_turn_right", 0.6, facing);
            for (Hands h : new Hands[]{start, helmsmanHands("helm_turn_right", 0.3, facing), end}) {
                assertTrue(fromRim(facing, h.right()) < 1.5 && fromRim(facing, h.left()) < 1.5,
                        facing + ": gripping hands off the rim " + fromRim(facing, h.right()) + " / " + fromRim(facing, h.left()));
            }
            assertTrue(clock(facing, end.right()) - clock(facing, start.right()) > 30, facing + ": right hand clockwise");
            assertTrue(clock(facing, end.left()) - clock(facing, start.left()) > 30, facing + ": left hand clockwise");
            Hands regrip = helmsmanHands("helm_turn_right", 0.8, facing);
            assertTrue(fromRim(facing, regrip.right()) < 0.3 * 16, facing + ": the hands stay at the wheel while they regrip");
        }
    }

    @Test
    void helmTurnLeftIsTheMirrorImage() throws IOException {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Hands start = helmsmanHands("helm_turn_left", 0.0, facing);
            Hands end = helmsmanHands("helm_turn_left", 0.6, facing);
            assertTrue(fromRim(facing, end.right()) < 1.5 && fromRim(facing, end.left()) < 1.5, facing + ": on the rim");
            assertTrue(clock(facing, end.right()) - clock(facing, start.right()) < -30, facing + ": right hand counter-clockwise");
            assertTrue(clock(facing, end.left()) - clock(facing, start.left()) < -30, facing + ": left hand counter-clockwise");
        }
    }

    @Test
    void cannonFireLungesTheLinstockHandBelowTheShoulderAndForward() throws IOException {
        Vector3f pos = new Vector3f();
        float yaw = 0f; // facing south (+z)
        BakedGeoModel touch = posed("cannon_fire", 0.25);
        Vector3f hand = world(touch, pos, yaw, List.of("root", "waist", "right_arm"), "right_hand");
        Vector3f shoulder = world(touch, pos, yaw, List.of("root", "waist"), "right_arm");
        assertTrue(hand.y < shoulder.y - 0.25, "linstock hand " + hand.y + " not well below the shoulder " + shoulder.y);
        assertTrue(hand.z > 0.6, "the lunge reaches forward to the gun: " + hand.z);
        BakedGeoModel ready = posed("cannon_fire", 0.0);
        Vector3f raised = world(ready, pos, yaw, List.of("root", "waist", "right_arm"), "right_hand");
        Vector3f readyShoulder = world(ready, pos, yaw, List.of("root", "waist"), "right_arm");
        assertTrue(raised.y > readyShoulder.y, "the linstock is held high before the lunge");
    }

    @Test
    void cannonAimRestsAHandOnTheBreechWhileLeaningIn() throws IOException {
        BakedGeoModel aim = posed("cannon_aim", 0.0);
        Vector3f hand = world(aim, new Vector3f(), 0f, List.of("root", "waist", "right_arm"), "right_hand");
        Vector3f neck = world(aim, new Vector3f(), 0f, List.of("root", "waist"), "head");
        // the gun's barrel axis is 9.5 px up, its breech top about 13 px; the gun stands in the next block
        assertEquals(12.5 / 16, hand.y, 2.0 / 16, "hand at the height of the breech");
        assertTrue(hand.z > 0.5, "hand reaches into the next block: " + hand.z);
        assertTrue(neck.z > 0.2, "leaning in over the gun: " + neck.z);
    }
}
