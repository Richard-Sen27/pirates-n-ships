package com.richardsenger.piratesnships.mob;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.mob.client.HumanoidGeoModel;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.keyframe.BoneAnimation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import com.richardsenger.piratesnships.mob.entity.NavySoldier;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Blockbench models of the pirate, sailor, navy soldier and officer (M3-art) on the humanoid rig
 * ({@code art/README.md}, "Entities"): each type's {@code geo/<type>.geo.json} has exactly the crew member's bones with
 * the same parents and pivots and the same contract cubes (detail cubes may be added), so the shared
 * {@code crew_member.animation.json} drives it; GeckoLib's own parser bakes it; every per-face UV stays on the 64x64
 * skin sheet; and the model resolves the geometry from the texture name.
 */
class SeafarerRigTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/pirates_n_ships");
    private static final Path RIG = ASSETS.resolve("geo/crew_member.geo.json");
    private static final Path ANIMATIONS = ASSETS.resolve("animations/crew_member.animation.json");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Path geo(MobKind kind) {
        return ASSETS.resolve("geo/" + kind.id() + ".geo.json");
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static JsonObject geometry(Path file) throws IOException {
        return read(file).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
    }

    private static Map<String, JsonObject> bones(Path file) throws IOException {
        Map<String, JsonObject> byName = new HashMap<>();
        for (JsonElement b : geometry(file).getAsJsonArray("bones")) byName.put(b.getAsJsonObject().get("name").getAsString(), b.getAsJsonObject());
        return byName;
    }

    /** The box-UV cubes of a bone (the rig's contract cubes: body, head, hat layer, limbs and their outer layers). */
    private static List<JsonObject> boxCubes(JsonObject bone) {
        List<JsonObject> out = new ArrayList<>();
        if (!bone.has("cubes")) return out;
        for (JsonElement c : bone.getAsJsonArray("cubes")) {
            JsonObject cube = c.getAsJsonObject();
            if (cube.get("uv").isJsonArray()) out.add(cube);
        }
        return out;
    }

    private static float[] floats(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    @ParameterizedTest
    @EnumSource(MobKind.class)
    void geometryIsBedrock1_12WithItsOwnIdentifierAndA64x64Sheet(MobKind kind) throws IOException {
        JsonObject json = read(geo(kind));
        assertEquals("1.12.0", json.get("format_version").getAsString(), "GeckoLib supports geometry 1.12.0 only");
        JsonObject desc = geometry(geo(kind)).getAsJsonObject("description");
        assertEquals("geometry." + kind.id(), desc.get("identifier").getAsString());
        assertEquals(64, desc.get("texture_width").getAsInt());
        assertEquals(64, desc.get("texture_height").getAsInt());
    }

    @ParameterizedTest
    @EnumSource(MobKind.class)
    void bonesParentsPivotsAndContractCubesAreTheCrewRigs(MobKind kind) throws IOException {
        Map<String, JsonObject> rig = bones(RIG), mob = bones(geo(kind));
        assertEquals(rig.keySet(), mob.keySet(), kind + " bone names");
        for (String name : rig.keySet()) {
            JsonObject r = rig.get(name), m = mob.get(name);
            assertEquals(r.has("parent") ? r.get("parent").getAsString() : null,
                    m.has("parent") ? m.get("parent").getAsString() : null, kind + " " + name + " parent");
            assertArrayEquals(floats(r.getAsJsonArray("pivot")), floats(m.getAsJsonArray("pivot")), 1e-4f, kind + " " + name + " pivot");
            assertFalse(m.has("rotation"), kind + " " + name + " must not carry a rest rotation");
            assertEquals(boxCubes(r), boxCubes(m), kind + " " + name + " contract cubes");
        }
        assertFalse(mob.get("right_hand").has("cubes"), "right_hand is an empty locator");
        assertFalse(mob.get("left_hand").has("cubes"), "left_hand is an empty locator");
    }

    @ParameterizedTest
    @EnumSource(MobKind.class)
    void everyFaceUvStaysOnTheSheet(MobKind kind) throws IOException {
        int detail = 0;
        for (JsonObject bone : bones(geo(kind)).values()) {
            if (!bone.has("cubes")) continue;
            for (JsonElement c : bone.getAsJsonArray("cubes")) {
                JsonElement uv = c.getAsJsonObject().get("uv");
                if (!uv.isJsonObject()) continue;
                detail++;
                for (Map.Entry<String, JsonElement> face : uv.getAsJsonObject().entrySet()) {
                    float[] at = floats(face.getValue().getAsJsonObject().getAsJsonArray("uv"));
                    float[] size = floats(face.getValue().getAsJsonObject().getAsJsonArray("uv_size"));
                    for (int i = 0; i < 2; i++) {
                        float lo = Math.min(at[i], at[i] + size[i]), hi = Math.max(at[i], at[i] + size[i]);
                        assertTrue(lo >= 0 && hi <= 64, kind + " " + bone.get("name").getAsString() + " " + face.getKey() + " uv off the sheet");
                    }
                }
            }
        }
        assertTrue(detail > 10, kind + " has its detail cubes");
    }

    @ParameterizedTest
    @EnumSource(MobKind.class)
    void geckoLibBakesItAndTheSharedAnimationsFindEveryBone(MobKind kind) throws IOException {
        Model model = KeyFramesAdapter.GEO_GSON.fromJson(read(geo(kind)), Model.class);
        BakedGeoModel baked = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(model));
        assertEquals(1, baked.topLevelBones().size(), "one root bone");
        GeoBone arm = baked.getBone("right_arm").orElseThrow();
        assertEquals(5f, arm.getPivotX(), 1e-4f, "GeckoLib mirrors x when baking");
        BakedAnimations anims = KeyFramesAdapter.GEO_GSON.fromJson(read(ANIMATIONS).getAsJsonObject("animations"), BakedAnimations.class);
        for (Animation anim : anims.animations().values()) {
            for (BoneAnimation b : anim.boneAnimations()) {
                assertNotNull(baked.getBone(b.boneName()).orElse(null), kind + ": " + anim.name() + " animates missing bone " + b.boneName());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MobKind.class)
    void textureIsAPainted64x64SkinSheet(MobKind kind) throws IOException {
        BufferedImage img = ImageIO.read(ASSETS.resolve("textures/entity/" + kind.id() + ".png").toFile());
        assertEquals(64, img.getWidth());
        assertEquals(64, img.getHeight());
        assertEquals(0xFF, img.getRGB(12, 12) >>> 24, "face");
        assertEquals(0xFF, img.getRGB(24, 26) >>> 24, "body front");
    }

    @ParameterizedTest
    @EnumSource(MobKind.class)
    void theModelResolvesTheTypesGeometryFromItsTexture(MobKind kind) {
        ResourceLocation geo = HumanoidGeoModel.geoFor(HumanoidGeoModel.entityTexture(kind.id()));
        assertEquals(Constants.id("geo/" + kind.id() + ".geo.json"), geo);
        assertTrue(Files.isRegularFile(ASSETS.resolve(geo.getPath())), geo + " exists");
        assertEquals(HumanoidGeoModel.RIG_GEO, HumanoidGeoModel.geoFor(HumanoidGeoModel.entityTexture("crew_member")),
                "the crew member keeps its geometry");
    }

    /** Bones the musket animations may key (M6): arms, the right hand locator (turns the held musket) and the waist. */
    private static final Set<String> MUSKET_BONES = Set.of("right_arm", "left_arm", "right_hand", "waist");

    @Test
    void musketAnimationsExistWithTheirLengthsAndLoopModesAndKeyOnlyTheUpperBody() throws IOException {
        BakedAnimations anims = KeyFramesAdapter.GEO_GSON.fromJson(read(ANIMATIONS).getAsJsonObject("animations"), BakedAnimations.class);
        Animation aim = anims.getAnimation(NavySoldier.ANIM_AIM);
        Animation reload = anims.getAnimation(NavySoldier.ANIM_RELOAD);
        Animation shove = anims.getAnimation(NavySoldier.ANIM_SHOVE);
        assertNotNull(aim, "musket_aim");
        assertNotNull(reload, "musket_reload");
        assertNotNull(shove, "musket_shove");
        assertEquals(Animation.LoopType.HOLD_ON_LAST_FRAME, aim.loopType(), "the aim holds its last frame");
        assertEquals(Animation.LoopType.PLAY_ONCE, reload.loopType(), "the reload plays once");
        assertEquals(Animation.LoopType.PLAY_ONCE, shove.loopType(), "the shove plays once");
        // GeckoLib lengths are in ticks
        assertEquals(7.0, aim.length(), 1e-6, "aim rises in 0.35 s");
        assertEquals(MusketAction.RELOAD_ANIMATION_TICKS, reload.length(), 1e-6, "reload is 5 s (the speed stretches it)");
        assertEquals(MusketAction.SHOVE_TICKS, shove.length(), 1e-6, "shove is 0.5 s");
        for (Animation anim : List.of(aim, reload, shove)) {
            Set<String> keyed = new java.util.HashSet<>();
            for (BoneAnimation b : anim.boneAnimations()) {
                assertTrue(MUSKET_BONES.contains(b.boneName()), anim.name() + " keys " + b.boneName() + " (legs walk, the head looks)");
                keyed.add(b.boneName());
            }
            assertEquals(MUSKET_BONES, keyed, anim.name() + " keys both arms, the musket hand and the waist");
        }
        // the musket lies along the aim: the hand locator turns the barrel level (about +84 in file convention)
        JsonObject hand = read(ANIMATIONS).getAsJsonObject("animations").getAsJsonObject(NavySoldier.ANIM_AIM)
                .getAsJsonObject("bones").getAsJsonObject("right_hand").getAsJsonObject("rotation");
        JsonArray last = hand.getAsJsonObject("0.35").getAsJsonArray("vector");
        assertTrue(last.get(0).getAsFloat() > 70f, "aim turns the musket level along the arm");
    }
}
