package com.richardsenger.piratesnships.mob;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * {@code bite} plays once, no animation turns the head or the root (code does), and the texture is 64x32.
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
}
