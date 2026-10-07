package com.richardsenger.piratesnships.crew.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.keyframe.BoneAnimation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crew member's rig (contract in {@code art/README.md}, "Entities"): the geo and animation files load with
 * GeckoLib's own parsers (the Gson adapters and model baker its resource reload uses), the bones have the documented
 * names, parents and pivots, every pose animation exists and loops, and the texture is a 64x64 skin sheet.
 */
class CrewMemberRigTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/pirates_n_ships");
    private static final Path GEO = ASSETS.resolve("geo/crew_member.geo.json");
    private static final Path ANIMATIONS = ASSETS.resolve("animations/crew_member.animation.json");
    private static final Path TEXTURE = ASSETS.resolve("textures/entity/crew_member.png");

    /** Bone → {parent (null for a root bone), pivot x, y, z}: the rig contract. */
    private static final Map<String, Object[]> BONES = new HashMap<>();

    static {
        BONES.put("root", new Object[]{null, 0f, 0f, 0f});
        BONES.put("waist", new Object[]{"root", 0f, 12f, 0f});
        BONES.put("body", new Object[]{"waist", 0f, 24f, 0f});
        BONES.put("head", new Object[]{"waist", 0f, 24f, 0f});
        BONES.put("hat", new Object[]{"head", 0f, 24f, 0f});
        BONES.put("right_arm", new Object[]{"waist", -5f, 22f, 0f});
        BONES.put("left_arm", new Object[]{"waist", 5f, 22f, 0f});
        BONES.put("right_hand", new Object[]{"right_arm", -6f, 12f, -2f});
        BONES.put("left_hand", new Object[]{"left_arm", 6f, 12f, -2f});
        BONES.put("right_leg", new Object[]{"root", -1.9f, 12f, 0f});
        BONES.put("left_leg", new Object[]{"root", 1.9f, 12f, 0f});
    }

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static BakedGeoModel bake() throws IOException {
        Model model = KeyFramesAdapter.GEO_GSON.fromJson(read(GEO), Model.class);
        return BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(model));
    }

    private static BakedAnimations animations() throws IOException {
        JsonObject anims = read(ANIMATIONS).getAsJsonObject("animations");
        return KeyFramesAdapter.GEO_GSON.fromJson(anims, BakedAnimations.class);
    }

    @Test
    void geometryIsBedrock1_12WithA64x64Texture() throws IOException {
        JsonObject json = read(GEO);
        assertEquals("1.12.0", json.get("format_version").getAsString(), "GeckoLib supports geometry 1.12.0 only");
        JsonObject desc = json.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonObject("description");
        assertEquals(64, desc.get("texture_width").getAsInt());
        assertEquals(64, desc.get("texture_height").getAsInt());
    }

    @Test
    void bonesHaveTheContractNamesParentsAndPivots() throws IOException {
        JsonArray bones = read(GEO).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
        Map<String, JsonObject> byName = new HashMap<>();
        for (JsonElement b : bones) byName.put(b.getAsJsonObject().get("name").getAsString(), b.getAsJsonObject());
        assertEquals(BONES.keySet(), byName.keySet(), "bone names");
        for (Map.Entry<String, Object[]> e : BONES.entrySet()) {
            JsonObject bone = byName.get(e.getKey());
            Object parent = e.getValue()[0];
            assertEquals(parent, bone.has("parent") ? bone.get("parent").getAsString() : null, e.getKey() + " parent");
            JsonArray p = bone.getAsJsonArray("pivot");
            float[] expected = {(float) e.getValue()[1], (float) e.getValue()[2], (float) e.getValue()[3]};
            float[] actual = {p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat()};
            assertArrayEquals(expected, actual, 1e-4f, e.getKey() + " pivot");
        }
        assertTrue(!byName.get("right_hand").has("cubes") && !byName.get("left_hand").has("cubes"), "hand locators are empty bones");
    }

    @Test
    void geckoLibBakesTheModelWithEveryBone() throws IOException {
        BakedGeoModel model = bake();
        for (String name : BONES.keySet()) {
            GeoBone bone = model.getBone(name).orElse(null);
            assertNotNull(bone, "GeckoLib did not bake bone " + name);
            Object parent = BONES.get(name)[0];
            assertEquals(parent, bone.getParent() == null ? null : bone.getParent().getName(), name + " parent after baking");
        }
        // GeckoLib mirrors x when baking (Bedrock to Java model space); y and z stay
        GeoBone arm = model.getBone("right_arm").orElseThrow();
        assertEquals(5f, arm.getPivotX(), 1e-4f);
        assertEquals(22f, arm.getPivotY(), 1e-4f);
        assertEquals(1, model.topLevelBones().size(), "one root bone");
    }

    @Test
    void everyPoseAnimationExistsLoopsAndAnimatesOnlyRigBones() throws IOException {
        BakedAnimations baked = animations();
        for (CrewPose pose : CrewPose.values()) {
            Animation anim = baked.getAnimation(pose.animation());
            assertNotNull(anim, "missing animation " + pose.animation());
            assertEquals(Animation.LoopType.LOOP, anim.loopType(), pose.animation() + " must loop");
            assertTrue(anim.length() > 0, pose.animation() + " has no length");
            assertTrue(anim.boneAnimations().length > 0, pose.animation() + " animates nothing");
            for (BoneAnimation b : anim.boneAnimations()) {
                assertTrue(BONES.containsKey(b.boneName()), pose.animation() + " animates unknown bone " + b.boneName());
            }
        }
        // the four poses plus the navy soldier's musket animations (M6), which share the rig
        assertEquals(Set.of("idle", "walk", "work", "sit", "musket_aim", "musket_reload", "musket_shove"), baked.animations().keySet());
    }

    @Test
    void noAnimationTurnsTheHead() throws IOException {
        // the head's x and y rotation follow the look direction (CrewMemberModel#setCustomAnimations overwrites them)
        JsonObject anims = read(ANIMATIONS).getAsJsonObject("animations");
        for (Map.Entry<String, JsonElement> a : anims.entrySet()) {
            JsonObject bones = a.getValue().getAsJsonObject().getAsJsonObject("bones");
            assertTrue(!bones.has("head") || !bones.getAsJsonObject("head").has("rotation"), a.getKey() + " rotates the head");
        }
    }

    @Test
    void textureIsA64x64SkinSheet() throws IOException {
        BufferedImage img = ImageIO.read(TEXTURE.toFile());
        assertEquals(64, img.getWidth());
        assertEquals(64, img.getHeight());
        // the front of the head and of the body are painted (opaque); the jacket layer is left transparent
        assertEquals(0xFF, img.getRGB(12, 12) >>> 24, "face");
        assertEquals(0xFF, img.getRGB(24, 26) >>> 24, "shirt front");
        assertEquals(0x00, img.getRGB(24, 40) >>> 24, "jacket layer");
    }
}
