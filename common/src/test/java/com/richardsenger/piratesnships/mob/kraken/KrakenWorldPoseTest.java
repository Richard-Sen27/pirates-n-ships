package com.richardsenger.piratesnships.mob.kraken;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.richardsenger.piratesnships.mob.kraken.client.KrakenAim;
import com.richardsenger.piratesnships.mob.kraken.client.KrakenModel;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.util.RenderUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The kraken's tentacles in world space (GL1), composed the way the game draws them: GeckoLib's baked model,
 * {@code GeoEntityRenderer#applyRotations}' turn by {@code 180 - bodyYaw}, {@link KrakenAim#reach} on
 * {@code tentacle_<i>_1} exactly as {@link KrakenModel} calls it, then {@link RenderUtil#prepMatrixForBone} down
 * {@code root -> tentacle_<i>_1 -> _2 -> _3} and {@code GeoRenderer#renderCube}'s cube rotation. The facts are about
 * the world: where the tip ends, which way the suckers face, which way the curled tip bends, for the body turned 0°,
 * 90°, 180° and 270° (and an odd 37°).
 * <p>
 * Before GL1 the sucker face was kept towards the body's axis: for an arm reaching up and out to a deck that normal,
 * made perpendicular to the arm, points inwards and up, so the suckers faced the sky and the curled tip bent upwards,
 * which is what the playtest showed from above. {@link #raisedArmsShowTheirSuckersDownAndCurlDown} fails on that
 * code (sucker normal y +0.77 for the 50° attack rest) while {@link #theTipEndsAtTheTargetForEveryBodyYaw} passes on
 * it: the frame change (yaw turn, baked mirror) was right, the choice of sucker direction was not.
 */
class KrakenWorldPoseTest {

    private static final Path GEO = Path.of("src/main/resources/assets/pirates_n_ships/geo/kraken.geo.json");
    private static final float[] YAWS = {0f, 90f, 180f, 270f, 37f};
    private static final double MIN_STRETCH = 0.4, MAX_STRETCH = 8;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static BakedGeoModel baked() throws IOException {
        Model model = KeyFramesAdapter.GEO_GSON.fromJson(JsonParser.parseString(Files.readString(GEO)).getAsJsonObject(), Model.class);
        return BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(model));
    }

    /** One arm drawn in the world (blocks, relative to the entity's position). */
    private record Arm(Vector3f root, Vector3f end, Vector3f suckerNormal, Vector3f curl, Vector3f tipCentroid) {
    }

    /** The world pose of the renderer's model frame for a body yaw. */
    private static PoseStack entityPose(float bodyYaw) {
        PoseStack pose = new PoseStack();
        pose.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
        return pose;
    }

    private static Vector3f at(PoseStack pose, double x, double y, double z) {
        return pose.last().pose().transformPosition(new Vector3f((float) (x / 16), (float) (y / 16), (float) (z / 16)));
    }

    /** The world position of the root of arm {@code i} (its first bone's pivot). */
    private static Vector3f rootOf(BakedGeoModel model, int i, float bodyYaw) {
        GeoBone b = model.getBone(KrakenModel.tentacleBone(i, 1)).orElseThrow();
        PoseStack pose = entityPose(bodyYaw);
        RenderUtil.prepMatrixForBone(pose, model.getBone("root").orElseThrow());
        return at(pose, b.getPivotX(), b.getPivotY(), b.getPivotZ());
    }

    /**
     * Arm {@code i} aimed at the world offset {@code target} (blocks from the entity's position). Each output vector is
     * from the arm's axis: the sucker normal from the first segment's suckers, the curl from the third segment's
     * tilted tip cubes (both averaged, then measured from the axis point at the same height in the rest pose).
     */
    private static Arm draw(int i, float bodyYaw, Vector3f target) throws IOException {
        BakedGeoModel model = baked();
        GeoBone first = model.getBone(KrakenModel.tentacleBone(i, 1)).orElseThrow();
        double px = first.getPivotX(), py = first.getPivotY(), pz = first.getPivotZ();
        KrakenAim.Reach reach = KrakenAim.reach(new double[]{px, py, pz}, new double[]{target.x, target.y, target.z},
                bodyYaw, KrakenModel.TENTACLE_LENGTH, MIN_STRETCH, MAX_STRETCH);
        first.setRotX(reach.rot()[0]);
        first.setRotY(reach.rot()[1]);
        first.setRotZ(reach.rot()[2]);
        first.setScaleY(reach.stretch());

        PoseStack pose = entityPose(bodyYaw);
        RenderUtil.prepMatrixForBone(pose, model.getBone("root").orElseThrow());
        Vector3f root = at(pose, px, py, pz);
        Vector3f sucker = new Vector3f(), curl = new Vector3f(), tip = new Vector3f();
        Vector3f end = null;
        int suckers = 0, curls = 0, tipPoints = 0;
        for (int seg = 1; seg <= 3; seg++) {
            GeoBone bone = model.getBone(KrakenModel.tentacleBone(i, seg)).orElseThrow();
            RenderUtil.prepMatrixForBone(pose, bone);
            if (seg == 3) end = at(pose, px, py + KrakenModel.TENTACLE_LENGTH, pz);
            for (GeoCube cube : bone.getCubes()) {
                boolean isSucker = cube.size().x < 1.3 && cube.size().y < 1.3;
                boolean tilted = !isSucker && Math.abs(cube.rotation().x) > 1e-3;
                if (!(seg == 1 && isSucker) && !tilted && seg != 3) continue;
                pose.pushPose();
                RenderUtil.translateToPivotPoint(pose, cube);
                RenderUtil.rotateMatrixAroundCube(pose, cube);
                RenderUtil.translateAwayFromPivotPoint(pose, cube);
                List<Vector3f> world = new ArrayList<>();
                double restY = 0;
                for (GeoQuad q : cube.quads()) {
                    if (q == null) continue;
                    for (GeoVertex v : q.vertices()) {
                        world.add(pose.last().pose().transformPosition(new Vector3f(v.position())));
                        restY += v.position().y() * 16;
                    }
                }
                pose.popPose();
                restY /= world.size();
                Vector3f c = new Vector3f();
                for (Vector3f w : world) c.add(w);
                c.div(world.size());
                if (seg == 3) {
                    tip.add(c);
                    tipPoints++;
                }
                if (!(seg == 1 && isSucker) && !tilted) continue;
                // the axis point at the cube's rest height, drawn with the same bone transforms (no cube rotation)
                Vector3f axis = at(pose, px, restY, pz);
                Vector3f off = c.sub(axis);
                if (tilted) {
                    curl.add(off);
                    curls++;
                } else {
                    sucker.add(off);
                    suckers++;
                }
            }
        }
        assertTrue(suckers > 10 && curls == 3, "arm " + i + ": " + suckers + " suckers, " + curls + " curled cubes");
        return new Arm(root, end, sucker.normalize(), curl.normalize(), tip.div(tipPoints));
    }

    /** The horizontal unit vector from the body's axis out through the root of arm {@code i}. */
    private static Vector3f outward(Vector3f root) {
        return new Vector3f(root.x, 0, root.z).normalize();
    }

    @Test
    void theTipEndsAtTheTargetForEveryBodyYaw() throws IOException {
        BakedGeoModel model = baked();
        for (float yaw : YAWS) {
            for (int i = 0; i < KrakenTentacles.COUNT; i++) {
                Vector3f root = rootOf(model, i, yaw), out = outward(root);
                for (Vector3f dir : new Vector3f[]{new Vector3f(out).mul(2).add(0, 2, 0), new Vector3f(out).mul(3).add(0, -1, 0),
                        new Vector3f(-out.z, 0.5f, out.x).mul(2.5f), new Vector3f(0, -3, 0)}) {
                    Vector3f target = new Vector3f(root).add(dir);
                    Arm arm = draw(i, yaw, target);
                    assertTrue(arm.root().distance(root) < 1e-4, "root moved");
                    assertTrue(arm.end().distance(target) < 1.0 / 16, "yaw " + yaw + " arm " + i + ": the arm ends at "
                            + arm.end() + ", not at the target " + target);
                }
            }
        }
    }

    @Test
    void anArmAimedThreeBlocksDownHangsBelowItsRoot() throws IOException {
        BakedGeoModel model = baked();
        for (float yaw : YAWS) {
            for (int i = 0; i < KrakenTentacles.COUNT; i++) {
                Vector3f root = rootOf(model, i, yaw);
                Arm arm = draw(i, yaw, new Vector3f(root).add(0, -3, 0));
                String what = "yaw " + yaw + " arm " + i;
                assertTrue(arm.tipCentroid().y < root.y - 2.0, what + ": tip at " + arm.tipCentroid().y + ", root at " + root.y);
                // hanging straight down the suckers face the body's axis (a dragged swimmer is held against the body)
                assertTrue(arm.suckerNormal().dot(outward(root)) < -0.9, what + ": suckers " + arm.suckerNormal());
                assertTrue(arm.curl().dot(outward(root)) < -0.9, what + ": the tip curls " + arm.curl());
            }
        }
    }

    @Test
    void raisedArmsShowTheirSuckersDownAndCurlDown() throws IOException {
        BakedGeoModel model = baked();
        for (float yaw : YAWS) {
            for (int i = 0; i < KrakenTentacles.COUNT; i++) {
                Vector3f root = rootOf(model, i, yaw), out = outward(root), side = new Vector3f(-out.z, 0, out.x);
                double rest = Math.toRadians(Kraken.REST_LEAN_UP);
                Vector3f[] reaches = {
                        // the attack rest (50 deg out from straight up), the K1c render pose
                        new Vector3f(out).mul((float) Math.sin(rest)).add(0, (float) Math.cos(rest), 0).mul(3.75f),
                        // up and out to a deck, level out, up and to the side
                        new Vector3f(out).mul(2.5f).add(0, 2.5f, 0), new Vector3f(out).mul(3.5f),
                        new Vector3f(out).add(side).mul(2).add(0, 2, 0)};
                for (Vector3f dir : reaches) {
                    Arm arm = draw(i, yaw, new Vector3f(root).add(dir));
                    String what = "yaw " + yaw + " arm " + i + " reaching " + dir;
                    assertTrue(arm.suckerNormal().y < -0.3, what + ": the suckers face up " + arm.suckerNormal());
                    assertTrue(arm.curl().y < -0.3, what + ": the tip curls up " + arm.curl());
                }
                // nearly straight up (13 deg out) the suckers face outwards, still a little down, and the tip hooks out
                Arm steep = draw(i, yaw, new Vector3f(root).add(new Vector3f(out).mul(0.8f)).add(0, 3.4f, 0));
                String what = "yaw " + yaw + " arm " + i + " steeply up";
                assertTrue(steep.suckerNormal().dot(out) > 0.8 && steep.suckerNormal().y < 0, what + ": suckers " + steep.suckerNormal());
                assertTrue(steep.curl().dot(out) > 0.8 && steep.curl().y < 0, what + ": the tip curls " + steep.curl());
            }
        }
    }
}
