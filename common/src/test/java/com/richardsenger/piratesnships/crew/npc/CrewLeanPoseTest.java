package com.richardsenger.piratesnships.crew.npc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.richardsenger.piratesnships.crew.lean.LeanRules;
import com.richardsenger.piratesnships.crew.lean.RailSpots;
import com.richardsenger.piratesnships.ship.decor.railing.RailingRules;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
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
 * RAIL5 rig test of the {@code lean} animation, composed end to end as the game draws it (art/README.md "GeckoLib bone
 * rotations in code", rule 5; the pattern of {@link CrewStationPoseTest}): GeckoLib's loaded key values (x and y
 * negated on load, radians) on the baked crew rig (pivots x-mirrored), {@link RenderUtil#prepMatrixForBone} down
 * {@code root -> waist -> arm}, the hand locators' baked pivots, and {@code GeoEntityRenderer}'s turn by
 * {@code 180 - bodyYaw}. The world is a railing block at the origin railed on its {@code outboard} edge (the rail
 * {@link RailingRules#RAIL_DEPTH} px deep along that edge, its top at {@link RailingRules#RAIL_TOP} px), the leaner
 * standing at {@link RailSpots#leanPoint} with the yaw {@link StationPoses#yaw} gives it towards the outboard side, as
 * {@code crew.lean.RailLean} and {@code CrewMember} place and turn it. A sign error in the keys (or in their file
 * convention) would put the hands behind the leaner or under the rail.
 */
class CrewLeanPoseTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/pirates_n_ships");
    private static final Path RIG = ASSETS.resolve("geo/crew_member.geo.json");
    private static final Path ANIMATIONS = ASSETS.resolve("animations/crew_member.animation.json");
    private static final double PX = 1.0 / 16.0;

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static float keyedAt(List<Keyframe<MathValue>> frames, double tick, String what) {
        double t = 0;
        for (Keyframe<MathValue> k : frames) {
            t += k.length();
            if (Math.abs(t - tick) < 1e-6) return (float) k.endValue().get();
        }
        throw new AssertionError(what + " has no key at tick " + tick);
    }

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

    /** World position (blocks) of {@code locator}'s pivot after {@code chain}, for an entity at {@code pos} with {@code bodyYaw}. */
    private static Vector3f world(BakedGeoModel baked, Vec3 pos, float bodyYaw, List<String> chain, String locator) {
        PoseStack pose = new PoseStack();
        pose.translate(pos.x, pos.y, pos.z);
        pose.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
        for (String name : chain) RenderUtil.prepMatrixForBone(pose, baked.getBone(name).orElseThrow());
        GeoBone at = baked.getBone(locator).orElseThrow();
        return pose.last().pose().transformPosition(new Vector3f(at.getPivotX(), at.getPivotY(), at.getPivotZ()).div(16f));
    }

    /** How far {@code p} lies outboard of the railing block's centre [blocks]. */
    private static double out(Direction outboard, Vector3f p) {
        return (p.x - 0.5) * outboard.getStepX() + (p.z - 0.5) * outboard.getStepZ();
    }

    /** How far {@code p} lies to the side of the leaner's line [blocks]. */
    private static double side(Direction outboard, Vector3f p) {
        Direction s = outboard.getClockWise();
        return (p.x - 0.5) * s.getStepX() + (p.z - 0.5) * s.getStepZ();
    }

    private record Arm(Vector3f shoulder, Vector3f hand) {
        /** Height of the arm's centre line where it is {@code o} blocks outboard of the block centre, or NaN. */
        double heightAt(Direction outboard, double o) {
            double a = out(outboard, shoulder), b = out(outboard, hand);
            if ((o - a) * (o - b) > 0 || Math.abs(b - a) < 1e-6) return Double.NaN;
            double t = (o - a) / (b - a);
            return shoulder.y + (hand.y - shoulder.y) * t;
        }
    }

    private static Arm[] arms(Direction outboard, double seconds) throws IOException {
        BakedGeoModel baked = posed("lean", seconds);
        Vec3 pos = RailSpots.leanPoint(new RailSpots.Spot(BlockPos.ZERO, outboard));
        float yaw = StationPoses.yaw(outboard, null);
        return new Arm[] {
                new Arm(world(baked, pos, yaw, List.of("root", "waist"), "right_arm"),
                        world(baked, pos, yaw, List.of("root", "waist", "right_arm"), "right_hand")),
                new Arm(world(baked, pos, yaw, List.of("root", "waist"), "left_arm"),
                        world(baked, pos, yaw, List.of("root", "waist", "left_arm"), "left_hand"))};
    }

    /**
     * The lowest point of an arm cube (baked, x-mirrored: the right arm x 4..8, the left -8..-4; y 12..24, z -2..2 px,
     * the contract cubes of art/README.md) that lies above the rail's depth, sampled through the posed chain [blocks].
     */
    private static double underside(Direction outboard, double seconds, boolean right) throws IOException {
        BakedGeoModel baked = posed("lean", seconds);
        Vec3 pos = RailSpots.leanPoint(new RailSpots.Spot(BlockPos.ZERO, outboard));
        PoseStack pose = new PoseStack();
        pose.translate(pos.x, pos.y, pos.z);
        pose.mulPose(Axis.YP.rotationDegrees(180f - StationPoses.yaw(outboard, null)));
        for (String name : List.of("root", "waist", right ? "right_arm" : "left_arm")) {
            RenderUtil.prepMatrixForBone(pose, baked.getBone(name).orElseThrow());
        }
        float x0 = right ? 4 : -8;
        double low = Double.POSITIVE_INFINITY;
        int n = 12;
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= 3 * n; j++) {
                for (int k = 0; k <= n; k++) {
                    Vector3f p = pose.last().pose().transformPosition(
                            new Vector3f(x0 + 4f * i / n, 12f + 12f * j / (3 * n), -2f + 4f * k / n).div(16f));
                    double o = out(outboard, p);
                    if (o >= 0.5 - RailingRules.RAIL_DEPTH * PX && o <= 0.5) {
                        low = Math.min(low, p.y);
                    }
                }
            }
        }
        assertTrue(low < Double.POSITIVE_INFINITY, outboard + ": the arm does not reach the rail");
        return low;
    }

    @Test
    void theLeanerStandsAtTheRailWithoutTouchingIt() {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Vec3 at = RailSpots.leanPoint(new RailSpots.Spot(BlockPos.ZERO, d));
            double front = (at.x - 0.5) * d.getStepX() + (at.z - 0.5) * d.getStepZ() + LeanRules.HALF_WIDTH;
            double railInner = 0.5 - RailingRules.RAIL_DEPTH * PX;
            assertTrue(front < railInner && front > railInner - 0.05, d + ": front " + front + ", rail at " + railInner);
        }
    }

    @Test
    void theForearmsLieAcrossTheTopRailAndTheHandsHangOutboard() throws IOException {
        double railInner = 0.5 - RailingRules.RAIL_DEPTH * PX;
        double railTop = RailingRules.RAIL_TOP * PX;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (double t : new double[] {0.0, 3.0, 6.0}) {
                Arm[] arms = arms(d, t);
                for (int i = 0; i < 2; i++) {
                    String what = d + " " + t + " s " + (i == 0 ? "right" : "left") + " hand";
                    Arm a = arms[i];
                    // the hand reaches over the rail: forward of the shoulder, outboard of the rail's inner face
                    assertTrue(out(d, a.hand()) > out(d, a.shoulder()) + 0.4, what + " not forward of the shoulder: " + a);
                    assertTrue(out(d, a.hand()) > railInner, what + " not over the rail: " + out(d, a.hand()));
                    assertTrue(out(d, a.hand()) < 0.5 + 0.5, what + " reaches too far out: " + out(d, a.hand()));
                    // just above the rail's top, below the shoulder
                    assertTrue(a.hand().y > railTop - PX && a.hand().y < railTop + 4 * PX, what + " height " + a.hand().y * 16 + " px");
                    assertTrue(a.hand().y < a.shoulder().y - 2 * PX, what + " not below the shoulder");
                    // the arm's line crosses the rail above it
                    double over = a.heightAt(d, 0.5 - RailingRules.RAIL_DEPTH * PX / 2);
                    assertTrue(!Double.isNaN(over) && over > railTop, what + ": the arm does not pass over the rail: " + over);
                }
                // the forearms rest on the rail: the arm cubes' underside over the rail lies on its top, not in it
                for (int i = 0; i < 2; i++) {
                    double under = underside(d, t, i == 0);
                    assertTrue(under > railTop - 0.1 * PX && under < railTop + 1.5 * PX,
                            d + " " + t + " s " + (i == 0 ? "right" : "left") + " forearm " + (under - railTop) * 16 + " px over the rail top");
                }
                // the hands meet in front of the chest, the right one on the leaner's right
                double right = side(d, arms[0].hand()), left = side(d, arms[1].hand());
                assertTrue(Math.abs(right) < 0.4 && Math.abs(left) < 0.4, d + ": hands spread " + right + " / " + left);
                assertTrue(right != left, d + ": both hands in one place");
            }
        }
    }

    @Test
    void theUpperBodyLeansOutOverTheRail() throws IOException {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BakedGeoModel baked = posed("lean", 0.0);
            Vec3 pos = RailSpots.leanPoint(new RailSpots.Spot(BlockPos.ZERO, d));
            Vector3f neck = world(baked, pos, StationPoses.yaw(d, null), List.of("root", "waist"), "head");
            double feet = (pos.x - 0.5) * d.getStepX() + (pos.z - 0.5) * d.getStepZ();
            assertTrue(out(d, neck) > feet + 0.15, d + ": the neck is not out over the feet: " + (out(d, neck) - feet));
        }
    }

    @Test
    void theHeadTurnStaysWithinTheBodysReach() {
        float max = 0;
        boolean both = false;
        float seen = 0;
        for (long t = 0; t < 4L * LeanRules.HEAD_CYCLE; t++) {
            float y = LeanRules.headYaw(t);
            max = Math.max(max, Math.abs(y));
            if (seen != 0 && Math.signum(y) == -Math.signum(seen)) both = true;
            if (y != 0) seen = y;
        }
        assertTrue(max <= StationFacing.MAX_HEAD_DEGREES, "the head turns " + max + " off the body");
        assertEquals(LeanRules.HEAD_TURN, max, 0.5f);
        assertTrue(both, "the head turns to one side only");
        assertEquals(0f, LeanRules.headYaw(0), 1e-6f, "a lean starts looking straight out");
    }
}
