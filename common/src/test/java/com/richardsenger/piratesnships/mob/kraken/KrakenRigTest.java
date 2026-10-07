package com.richardsenger.piratesnships.mob.kraken;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.richardsenger.piratesnships.mob.kraken.client.KrakenAim;
import com.richardsenger.piratesnships.mob.kraken.client.KrakenModel;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The kraken's rig (contract in art/README.md, "Kraken (K1b)"; tentacles lengthened in K1c): the files load with GeckoLib's own parsers, the bones have
 * the contract names, parents and pivots (the tentacle ring matches the server's hit-box ring {@code Kraken#anchor}),
 * {@code idle} loops, {@code surface} and {@code grab} play once, {@code submerge} holds, no animation keys what the code
 * drives (root, tentacle_i_1), the texture is 128x64 and the loot sprites 16x16.
 */
class KrakenRigTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/pirates_n_ships");
    private static final Path GEO = ASSETS.resolve("geo/kraken.geo.json");
    private static final Path ANIMATIONS = ASSETS.resolve("animations/kraken.animation.json");
    private static final Path TEXTURE = ASSETS.resolve("textures/entity/kraken.png");

    /** Spacing of the tentacle segments in px (K1c; K1b had 12). */
    static final float SEGMENT = 20f;
    /** Rest length of a tentacle in px, root to the curled tip: three segments. */
    static final float TENTACLE_LENGTH = 3 * SEGMENT;
    /** Bone → {parent, pivot x, y, z}: the rig contract. */
    private static final Map<String, Object[]> BONES = new LinkedHashMap<>();

    static {
        BONES.put("root", new Object[]{null, 0f, 0f, 0f});
        BONES.put("mantle", new Object[]{"root", 0f, 30f, 0f});
        BONES.put("eye_left", new Object[]{"mantle", 12f, 40f, -20f});
        BONES.put("eye_right", new Object[]{"mantle", -12f, 40f, -20f});
        for (int i = 0; i < KrakenTentacles.COUNT; i++) {
            double a = Math.toRadians((i + 0.5) * 45.0);
            float x = (float) (-Math.sin(a) * 22.4), z = (float) (-Math.cos(a) * 22.4);
            BONES.put("tentacle_" + i + "_1", new Object[]{"root", x, 20f, z});
            BONES.put("tentacle_" + i + "_2", new Object[]{"tentacle_" + i + "_1", x, 20f + SEGMENT, z});
            BONES.put("tentacle_" + i + "_3", new Object[]{"tentacle_" + i + "_2", x, 20f + 2 * SEGMENT, z});
        }
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
    void geometryIsBedrock1_12WithA128x64Texture() throws IOException {
        JsonObject json = read(GEO);
        assertEquals("1.12.0", json.get("format_version").getAsString());
        JsonObject desc = json.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonObject("description");
        assertEquals("geometry.kraken", desc.get("identifier").getAsString());
        assertEquals(128, desc.get("texture_width").getAsInt());
        assertEquals(64, desc.get("texture_height").getAsInt());
        BufferedImage img = ImageIO.read(TEXTURE.toFile());
        assertEquals(128, img.getWidth());
        assertEquals(64, img.getHeight());
        for (String item : Set.of("kraken_beak", "kraken_ink")) {
            BufferedImage sprite = ImageIO.read(ASSETS.resolve("textures/item/" + item + ".png").toFile());
            assertEquals(16, sprite.getWidth(), item);
            assertEquals(16, sprite.getHeight(), item);
        }
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
            assertArrayEquals(expected, actual, 1e-3f, e.getKey() + " pivot");
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
        assertFalse(baked.getBone("mantle").orElseThrow().getCubes().isEmpty(), "the mantle has cubes");
        // the model aims tentacle_i_1 with the baked pivot, whose x GeckoLib flips
        GeoBone t0 = baked.getBone("tentacle_0_1").orElseThrow();
        assertEquals(-(float) BONES.get("tentacle_0_1")[1], t0.getPivotX(), 1e-3f, "baked pivots have x flipped");
    }

    @Test
    void animationsLoopPlayOnceAndHold() throws IOException {
        BakedAnimations baked = KeyFramesAdapter.GEO_GSON.fromJson(read(ANIMATIONS).getAsJsonObject("animations"), BakedAnimations.class);
        assertEquals(Set.of("idle", "surface", "grab", "submerge"), baked.animations().keySet());
        for (String name : baked.animations().keySet()) {
            Animation anim = baked.getAnimation(name);
            assertTrue(anim.length() > 0, name + " has no length");
            for (BoneAnimation b : anim.boneAnimations()) {
                assertTrue(BONES.containsKey(b.boneName()), name + " animates unknown bone " + b.boneName());
            }
        }
        assertEquals(Animation.LoopType.LOOP, baked.getAnimation("idle").loopType());
        assertEquals(Animation.LoopType.PLAY_ONCE, baked.getAnimation("surface").loopType());
        assertEquals(Animation.LoopType.PLAY_ONCE, baked.getAnimation("grab").loopType());
        assertEquals(Animation.LoopType.HOLD_ON_LAST_FRAME, baked.getAnimation("submerge").loopType());
    }

    @Test
    void noAnimationKeysWhatTheCodeDrives() throws IOException {
        JsonObject anims = read(ANIMATIONS).getAsJsonObject("animations");
        for (Map.Entry<String, JsonElement> a : anims.entrySet()) {
            JsonObject bones = a.getValue().getAsJsonObject().getAsJsonObject("bones");
            assertFalse(bones.has("root"), a.getKey() + " keys the root");
            for (int i = 0; i < KrakenTentacles.COUNT; i++) {
                assertFalse(bones.has("tentacle_" + i + "_1"), a.getKey() + " keys tentacle_" + i + "_1");
            }
        }
    }

    @Test
    void tentacleRingMatchesTheHitBoxRing() {
        // Kraken#anchor: (-sin a, cos a) * 1.4 blocks in the world at yaw 0 (facing +z); the model faces -z with +x
        // on the left, so model (x, z) = (world x, -world z) * 16
        for (int i = 0; i < KrakenTentacles.COUNT; i++) {
            double a = (i + 0.5) * (Math.PI * 2 / KrakenTentacles.COUNT);
            float wx = (float) (-Math.sin(a) * 1.4 * 16), wz = (float) (Math.cos(a) * 1.4 * 16);
            assertEquals(wx, (float) BONES.get("tentacle_" + i + "_1")[1], 1e-3f, "tentacle " + i + " x");
            assertEquals(-wz, (float) BONES.get("tentacle_" + i + "_1")[3], 1e-3f, "tentacle " + i + " z");
        }
    }

    private static BakedGeoModel baked() throws IOException {
        Model model = KeyFramesAdapter.GEO_GSON.fromJson(read(GEO), Model.class);
        return BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(model));
    }

    /** The cube's corners in baked px, turned by the cube's own rotation about its pivot (as GeckoLib renders it). */
    private static List<double[]> corners(GeoCube cube) {
        List<double[]> out = new ArrayList<>();
        Vec3 r = cube.rotation(), p = cube.pivot();
        float[] e = {(float) r.x, (float) r.y, (float) r.z};
        for (GeoQuad q : cube.quads()) {
            if (q == null) continue;
            for (GeoVertex v : q.vertices()) {
                double[] rel = {v.position().x() * 16 - p.x, v.position().y() * 16 - p.y, v.position().z() * 16 - p.z};
                double[] t = KrakenAim.rotate(e, rel);
                out.add(new double[]{t[0] + p.x, t[1] + p.y, t[2] + p.z});
            }
        }
        return out;
    }

    private static double[] centroid(List<double[]> pts) {
        double[] c = new double[3];
        for (double[] q : pts) for (int k = 0; k < 3; k++) c[k] += q[k] / pts.size();
        return c;
    }

    @Test
    void tentaclesAreSixtyPxLongAndTheServerAgrees() throws IOException {
        assertEquals(TENTACLE_LENGTH, (float) (Kraken.TENTACLE_LENGTH * 16.0), 1e-4f, "Kraken#TENTACLE_LENGTH");
        assertEquals(TENTACLE_LENGTH, KrakenModel.TENTACLE_LENGTH, 1e-4f, "KrakenModel#TENTACLE_LENGTH");
        BakedGeoModel baked = baked();
        for (int i = 0; i < KrakenTentacles.COUNT; i++) {
            GeoBone root = baked.getBone("tentacle_" + i + "_1").orElseThrow();
            double top = Double.NEGATIVE_INFINITY;
            for (int seg = 1; seg <= 3; seg++) {
                for (GeoCube cube : baked.getBone("tentacle_" + i + "_" + seg).orElseThrow().getCubes()) {
                    for (double[] q : corners(cube)) top = Math.max(top, q[1]);
                }
            }
            // the curled tip ends within 1.5 px of the rest length above the root
            assertEquals(root.getPivotY() + TENTACLE_LENGTH, top, 1.5, "tentacle " + i + " tip height");
        }
    }

    @Test
    void suckersAndTheCurledTipFaceTheBodyAxis() throws IOException {
        BakedGeoModel baked = baked();
        for (int i = 0; i < KrakenTentacles.COUNT; i++) {
            GeoBone root = baked.getBone("tentacle_" + i + "_1").orElseThrow();
            double[] n0 = KrakenAim.restNormal(root.getPivotX(), root.getPivotZ());
            int suckers = 0, curled = 0;
            for (int seg = 1; seg <= 3; seg++) {
                for (GeoCube cube : baked.getBone("tentacle_" + i + "_" + seg).orElseThrow().getCubes()) {
                    double[] c = centroid(corners(cube));
                    double ox = c[0] - root.getPivotX(), oz = c[2] - root.getPivotZ();
                    double off = Math.hypot(ox, oz);
                    double along = (ox * n0[0] + oz * n0[2]);
                    boolean sucker = cube.size().x < 1.3 && cube.size().y < 1.3;
                    boolean tilted = Math.abs(cube.rotation().x) > 1e-3;
                    if (sucker) {
                        suckers++;
                        assertTrue(along > 0.5, "tentacle " + i + ": a sucker is not on the inner side (" + along + ")");
                    }
                    if (tilted && !sucker) {
                        curled++;
                        assertTrue(along > 0 && along / off > Math.cos(Math.toRadians(10)),
                                "tentacle " + i + ": the tip curls away from the sucker side");
                    }
                }
            }
            assertTrue(suckers >= 40, "tentacle " + i + " has only " + suckers + " suckers");
            assertEquals(3, curled, "tentacle " + i + " curled tip cubes");
        }
    }
}
