package com.richardsenger.piratesnships.mob;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.richardsenger.piratesnships.mob.client.SharkPose;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.keyframe.BoneAnimation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.util.RenderUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shark's rig (contract in {@code art/README.md}, "Entities", shark rig): the geo and animation files load with
 * GeckoLib's own parsers, the bones have the documented names, parents and pivots, {@code swim} and {@code idle} loop,
 * {@code bite} plays once, no animation turns the head or the root (code does), and the texture is 64x32; and the
 * code's pitch drawn in the world (GL1): {@link #theBodyPitchesWithTheSwimmingDirection} composes
 * {@code GeoEntityRenderer#applyRotations}' turn by {@code 180 - bodyYaw}, GeckoLib's baked model and
 * {@link RenderUtil#prepMatrixForBone} with {@link SharkPose}'s rotations. Before GL1 the root got Minecraft's pitch
 * unflipped ({@code root.setRotX(viewXRot)}); with that sign this test fails (the nose of a shark swimming down at 30°
 * ends 30° above its tail), which is the "back tilted the wrong way" of the 2026-10-07 playtest.
 */
class SharkRigTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/pirates_n_ships");
    private static final Path GEO = ASSETS.resolve("geo/shark.geo.json");
    private static final Path ANIMATIONS = ASSETS.resolve("animations/shark.animation.json");
    private static final Path TEXTURE = ASSETS.resolve("textures/entity/shark.png");

    /** Bone → {parent (null for the root bone), pivot x, y, z}: the rig contract. */
    private static final Map<String, Object[]> BONES = new LinkedHashMap<>();

    static {
        BONES.put("root", new Object[]{null, 0f, 5f, 0f});
        BONES.put("body", new Object[]{"root", 0f, 5f, 0f});
        BONES.put("head", new Object[]{"body", 0f, 5f, -8f});
        BONES.put("jaw", new Object[]{"head", 0f, 3f, -9f});
        BONES.put("tail_1", new Object[]{"body", 0f, 5f, 6f});
        BONES.put("tail_2", new Object[]{"tail_1", 0f, 5f, 14f});
        BONES.put("fin_left", new Object[]{"body", 5f, 2f, -4f});
        BONES.put("fin_right", new Object[]{"body", -5f, 2f, -4f});
        BONES.put("fin_dorsal", new Object[]{"body", 0f, 10f, -2f});
    }

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    @Test
    void geometryIsBedrock1_12WithA64x32Texture() throws IOException {
        JsonObject json = read(GEO);
        assertEquals("1.12.0", json.get("format_version").getAsString());
        JsonObject desc = json.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonObject("description");
        assertEquals("geometry.shark", desc.get("identifier").getAsString());
        assertEquals(64, desc.get("texture_width").getAsInt());
        assertEquals(32, desc.get("texture_height").getAsInt());
        BufferedImage img = ImageIO.read(TEXTURE.toFile());
        assertEquals(64, img.getWidth());
        assertEquals(32, img.getHeight());
    }

    @Test
    void bonesHaveTheContractNamesParentsAndPivots() throws IOException {
        JsonArray bones = read(GEO).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
        Map<String, JsonObject> byName = new LinkedHashMap<>();
        for (JsonElement b : bones) byName.put(b.getAsJsonObject().get("name").getAsString(), b.getAsJsonObject());
        assertEquals(BONES.keySet(), byName.keySet(), "bone names");
        for (Map.Entry<String, Object[]> e : BONES.entrySet()) {
            JsonObject bone = byName.get(e.getKey());
            assertEquals(e.getValue()[0], bone.has("parent") ? bone.get("parent").getAsString() : null, e.getKey() + " parent");
            JsonArray p = bone.getAsJsonArray("pivot");
            float[] expected = {(float) e.getValue()[1], (float) e.getValue()[2], (float) e.getValue()[3]};
            float[] actual = {p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat()};
            assertArrayEquals(expected, actual, 1e-4f, e.getKey() + " pivot");
        }
    }

    @Test
    void geckoLibBakesTheModelWithEveryBone() throws IOException {
        Model model = KeyFramesAdapter.GEO_GSON.fromJson(read(GEO), Model.class);
        BakedGeoModel baked = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(model));
        for (String name : BONES.keySet()) {
            GeoBone bone = baked.getBone(name).orElse(null);
            assertNotNull(bone, "GeckoLib did not bake bone " + name);
            assertEquals(BONES.get(name)[0], bone.getParent() == null ? null : bone.getParent().getName(), name + " parent after baking");
        }
        assertEquals(1, baked.topLevelBones().size(), "one root bone");
        assertTrue(baked.getBone("body").orElseThrow().getCubes().size() > 0, "the body has cubes");
    }

    @Test
    void animationsSwimAndIdleLoopBitePlaysOnce() throws IOException {
        BakedAnimations baked = KeyFramesAdapter.GEO_GSON.fromJson(read(ANIMATIONS).getAsJsonObject("animations"), BakedAnimations.class);
        assertEquals(Set.of("swim", "idle", "bite"), baked.animations().keySet());
        for (String name : Set.of("swim", "idle", "bite")) {
            Animation anim = baked.getAnimation(name);
            assertTrue(anim.length() > 0, name + " has no length");
            for (BoneAnimation b : anim.boneAnimations()) {
                assertTrue(BONES.containsKey(b.boneName()), name + " animates unknown bone " + b.boneName());
            }
        }
        assertEquals(Animation.LoopType.LOOP, baked.getAnimation("swim").loopType());
        assertEquals(Animation.LoopType.LOOP, baked.getAnimation("idle").loopType());
        assertEquals(Animation.LoopType.PLAY_ONCE, baked.getAnimation("bite").loopType());
        JsonObject bite = read(ANIMATIONS).getAsJsonObject("animations").getAsJsonObject("bite").getAsJsonObject("bones");
        assertTrue(bite.has("jaw"), "bite moves the jaw");
    }

    @Test
    void noAnimationTurnsTheHeadOrTheRoot() throws IOException {
        JsonObject anims = read(ANIMATIONS).getAsJsonObject("animations");
        for (Map.Entry<String, JsonElement> a : anims.entrySet()) {
            JsonObject bones = a.getValue().getAsJsonObject().getAsJsonObject("bones");
            for (String coded : Set.of("head", "root")) {
                assertTrue(!bones.has(coded) || !bones.getAsJsonObject(coded).has("rotation"), a.getKey() + " rotates " + coded);
            }
        }
    }

    /** The shark's frontmost (nose) or rearmost (tail tip) vertex in the world, blocks from the entity's position. */
    private static Vector3f drawn(BakedGeoModel baked, float bodyYaw, List<String> chain, boolean front) {
        PoseStack pose = new PoseStack();
        pose.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
        for (String name : chain) RenderUtil.prepMatrixForBone(pose, baked.getBone(name).orElseThrow());
        GeoBone last = baked.getBone(chain.get(chain.size() - 1)).orElseThrow();
        Vector3f best = null;
        float bestZ = 0;
        for (GeoCube cube : last.getCubes()) {
            pose.pushPose();
            RenderUtil.translateToPivotPoint(pose, cube);
            RenderUtil.rotateMatrixAroundCube(pose, cube);
            RenderUtil.translateAwayFromPivotPoint(pose, cube);
            for (GeoQuad q : cube.quads()) {
                if (q == null) continue;
                for (GeoVertex v : q.vertices()) {
                    float z = v.position().z(); // the model faces -z
                    if (best == null || (front ? z < bestZ : z > bestZ)) {
                        bestZ = z;
                        best = pose.last().pose().transformPosition(new Vector3f(v.position()));
                    }
                }
            }
            pose.popPose();
        }
        return best;
    }

    /** The pivot of the last bone of {@code chain} in the world, blocks from the entity's position. */
    private static Vector3f pivot(BakedGeoModel baked, float bodyYaw, List<String> chain) {
        PoseStack pose = new PoseStack();
        pose.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
        for (String name : chain) RenderUtil.prepMatrixForBone(pose, baked.getBone(name).orElseThrow());
        GeoBone last = baked.getBone(chain.get(chain.size() - 1)).orElseThrow();
        return pose.last().pose().transformPosition(new Vector3f(last.getPivotX(), last.getPivotY(), last.getPivotZ()).div(16f));
    }

    @Test
    void theBodyPitchesWithTheSwimmingDirection() throws IOException {
        Model model = KeyFramesAdapter.GEO_GSON.fromJson(read(GEO), Model.class);
        for (float yaw : new float[]{0f, 90f, 180f, 270f, 37f}) {
            for (float pitch : new float[]{30f, -30f, 50f, 0f}) {
                BakedGeoModel baked = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(model));
                baked.getBone("root").orElseThrow().setRotX(SharkPose.rootRotX(pitch, true));
                baked.getBone("head").orElseThrow().setRotX(SharkPose.headRotX(pitch, true));
                Vector3f nose = drawn(baked, yaw, List.of("root", "body", "head"), true);
                Vector3f tail = drawn(baked, yaw, List.of("root", "body", "tail_1", "tail_2"), false);
                // the body's line: tail_2's pivot to the head's pivot, both at y 5 in the rest pose
                Vector3f along = pivot(baked, yaw, List.of("root", "body", "head"))
                        .sub(pivot(baked, yaw, List.of("root", "body", "tail_1", "tail_2"))).normalize();
                // Minecraft's look vector for this yaw and pitch (positive pitch = down)
                double p = Math.toRadians(pitch), y = Math.toRadians(yaw);
                Vector3f look = new Vector3f((float) (-Math.sin(y) * Math.cos(p)), (float) -Math.sin(p), (float) (Math.cos(y) * Math.cos(p)));
                String what = "yaw " + yaw + " pitch " + pitch + ": nose " + nose + ", tail " + tail;
                if (pitch > 0) assertTrue(nose.y < tail.y - 0.5, what + ": swimming down, the nose is not below the tail");
                if (pitch < 0) assertTrue(nose.y > tail.y + 0.5, what + ": swimming up, the nose is not above the tail");
                double off = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, along.dot(look)))));
                assertTrue(off < 0.5, what + ": the body points " + off + " deg off the swimming direction");
            }
        }
    }

    @Test
    void theHeadOnlyTakesWhatTheBodyCannot() {
        // in water the body carries the pitch (up to 60 deg), the head stays straight on it
        assertEquals(0f, SharkPose.headRotX(30f, true), 1e-6f);
        assertEquals(-Math.toRadians(30), SharkPose.rootRotX(30f, true), 1e-6);
        assertEquals(-Math.toRadians(20), SharkPose.headRotX(80f, true), 1e-6);
        // on land the body stays level and the head looks within 30 deg; nose down is a negative bone x rotation
        assertEquals(0f, SharkPose.rootRotX(45f, false), 1e-6f);
        assertEquals(-Math.toRadians(30), SharkPose.headRotX(45f, false), 1e-6);
        assertEquals(Math.toRadians(30), SharkPose.headRotX(-45f, false), 1e-6);
    }
}
